package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.agent.TurnResult
import com.lifehub.app.copilot.stt.SttResult
import com.lifehub.app.copilot.stt.Transcriber
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.repo.ChatStore
import com.lifehub.app.data.repo.NewChatMessage
import java.io.File

/** Visible progress of a run (chat: "Transcribing…" then "Thinking…"). */
enum class CaptureStage { TRANSCRIBING, THINKING }

/**
 * Where a run left the capture. [ran] = false when there was nothing to do (already final, waiting for an answer, or
 * FAILED waiting for the user's Retry); [turn] is null when the agent was not reached (e.g. transcription failed).
 */
data class RunReport(val captureId: String, val status: CaptureStatus?, val ran: Boolean, val turn: TurnResult? = null)

/** Error codes stored on FAILED captures by the runner (others come from the agent turn). */
object CaptureErrors {
    const val STT_PREFIX = "Stt."
    const val NO_SPEECH = "NoSpeech"
    const val NO_INPUT = "NoInput"
    const val UNEXPECTED = "Unexpected"
    const val NO_SPEECH_MESSAGE = "Nothing was heard in the recording."
}

/**
 * The core of the CaptureWorker (design doc §4 and §11), a plain class so it is tested without WorkManager. Takes a capture
 * from RECORDED (or an interrupted PROCESSING) to DONE / AWAITING_INPUT / FAILED, skipping every step already done:
 * 1. transcribe the audio (only if there is no transcript yet), store the transcript;
 * 2. append the transcript as the capture's USER message (only once; a quick capture's thread takes it as its title);
 * 3. run the agent turn through [CaptureProcessor.process] (its writes are idempotent per capture);
 * 4. publish the outcome ([CaptureNotifier]) and delete the audio once the capture is DONE or CANCELLED.
 * Failures (no network, no key, provider errors) leave the capture FAILED with its audio and transcript; [prepareRetry]
 * puts it back to RECORDED so the next run resumes from the first missing step.
 */
class CaptureRunner(
    private val captures: CaptureStore,
    private val chat: ChatStore,
    private val processor: CaptureProcessor,
    private val transcriber: Transcriber,
    private val language: () -> String?,
    private val time: TimeSource,
    private val notifier: CaptureNotifier = CaptureNotifier.None,
    private val deleteAudio: (String) -> Unit = { runCatching { File(it).delete() } },
) {
    suspend fun run(captureId: String, notify: Boolean = true, onStage: (CaptureStage) -> Unit = {}): RunReport {
        val c = captures.get(captureId) ?: return RunReport(captureId, null, ran = false)
        if (c.status != CaptureStatus.RECORDED && c.status != CaptureStatus.PROCESSING) return RunReport(captureId, c.status, ran = false)
        val threadId = c.threadId ?: return fail(captureId, CaptureErrors.NO_INPUT, "This capture has no conversation.", notify)

        // 1. Text: typed, or already transcribed by an earlier attempt, or transcribe now.
        var text = c.inputText ?: c.transcript
        if (text == null) {
            val audio = c.audioPath ?: return fail(captureId, CaptureErrors.NO_INPUT, "This capture has no text or audio.", notify)
            onStage(CaptureStage.TRANSCRIBING)
            when (val r = transcriber.transcribe(File(audio), language()?.takeIf { it.isNotBlank() })) {
                is SttResult.Failure -> return fail(captureId, CaptureErrors.STT_PREFIX + r.error::class.simpleName, r.error.message, notify)
                is SttResult.Ok -> {
                    if (r.text.isBlank()) return fail(captureId, CaptureErrors.NO_SPEECH, CaptureErrors.NO_SPEECH_MESSAGE, notify)
                    captures.setTranscript(captureId, r.text, time.now())
                    text = r.text
                }
            }
        }

        // 2. The user message, once per capture (a retry never appends it twice).
        if (chat.messages(threadId).none { it.role == ChatRole.USER && it.captureId == captureId }) {
            chat.append(threadId, NewChatMessage(ChatRole.USER, text, captureId = captureId), time.now())
            // A quick capture's thread, or a chat thread started by voice, takes the transcript as its title (no model call).
            if (c.origin == ChatOrigin.QUICK || chat.thread(threadId)?.title == VOICE_TITLE) chat.setTitle(threadId, threadTitle(text))
        }

        // 3. The agent turn (tools filtered by origin inside the processor).
        onStage(CaptureStage.THINKING)
        val run = processor.process(captureId)
        return finish(captureId, notify, run.turn)
    }

    /** Retry (inbox, notification, chat): FAILED -> RECORDED. False when the capture is not FAILED any more. */
    suspend fun prepareRetry(captureId: String): Boolean {
        val c = captures.get(captureId) ?: return false
        if (c.status != CaptureStatus.FAILED) return false
        captures.move(captureId, CaptureStatus.RECORDED, time.now())
        notifier.cancelCapture(captureId)
        return true
    }

    /** Last resort for a crash inside a run: the capture must not stay PROCESSING forever. */
    suspend fun failUnexpected(captureId: String, notify: Boolean = true) {
        val c = captures.get(captureId) ?: return
        if (c.status == CaptureStatus.RECORDED || c.status == CaptureStatus.PROCESSING)
            fail(captureId, CaptureErrors.UNEXPECTED, "Something went wrong while processing this capture.", notify)
    }

    private suspend fun fail(captureId: String, code: String, message: String, notify: Boolean): RunReport {
        captures.move(captureId, CaptureStatus.FAILED, time.now(), errorCode = code, errorMessage = message)
        if (notify) notifier.publish(captureId)
        return RunReport(captureId, CaptureStatus.FAILED, ran = true)
    }

    private suspend fun finish(captureId: String, notify: Boolean, turn: TurnResult): RunReport {
        val c = captures.get(captureId)
        if (c != null && (c.status == CaptureStatus.DONE || c.status == CaptureStatus.CANCELLED)) {
            c.audioPath?.let { deleteAudio(it); captures.clearAudio(captureId, time.now()) }
        }
        if (notify) notifier.publish(captureId)
        return RunReport(captureId, c?.status, ran = true, turn = turn)
    }

    companion object {
        /** Placeholder title of a chat thread whose first message is a voice message (until the transcript exists). */
        const val VOICE_TITLE = "Voice message"

        fun threadTitle(text: String): String {
            val t = text.trim().replace(Regex("\\s+"), " ")
            return if (t.length <= 60) t else t.take(59).trimEnd() + "…"
        }
    }
}

/** Publishes the result of a capture run (implemented by [com.lifehub.app.copilot.notify.NotificationCenter]). */
interface CaptureNotifier {
    suspend fun publish(captureId: String) {}
    fun cancelCapture(captureId: String) {}
    object None : CaptureNotifier
}

/** Queues a capture for the background worker (WorkManager in the app, a fake in tests). */
fun interface CaptureScheduler {
    fun enqueue(captureId: String)
}
