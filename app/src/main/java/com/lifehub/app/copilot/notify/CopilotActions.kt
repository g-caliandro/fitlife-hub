package com.lifehub.app.copilot.notify

import com.lifehub.app.copilot.capture.CaptureProcessor
import com.lifehub.app.copilot.capture.CaptureRunner
import com.lifehub.app.copilot.capture.CaptureScheduler
import com.lifehub.app.copilot.capture.IllegalTransitionException
import com.lifehub.app.copilot.capture.UndoResult

/**
 * User actions on captures from outside the chat: notification actions and the inbox. Every one is a quick DB change;
 * the slow part (transcription, model turn) is queued for the CaptureWorker, so they are safe from a BroadcastReceiver
 * with the app closed. Answering goes through [CaptureProcessor.recordAnswer], the same path as the chat, so a request
 * answered here is closed in the chat and the inbox too.
 */
class CopilotActions(
    private val processor: CaptureProcessor,
    private val runner: CaptureRunner,
    private val scheduler: CaptureScheduler,
    private val events: NotificationCenter? = null,
) {
    /** False when the request was already answered or discarded elsewhere (its notification is removed anyway). */
    suspend fun answer(pendingId: String, option: Int? = null, text: String? = null): Boolean {
        if (option == null && text.isNullOrBlank()) return false
        val captureId = try { processor.recordAnswer(pendingId, option, text) } catch (_: IllegalTransitionException) { null }
        events?.pendingClosed(pendingId)
        captureId ?: return false
        scheduler.enqueue(captureId)
        return true
    }

    suspend fun discard(pendingId: String): Boolean = try {
        processor.discard(pendingId); true
    } catch (_: IllegalTransitionException) { events?.pendingClosed(pendingId); false }

    suspend fun undo(captureId: String): UndoResult = processor.undo(captureId).also { events?.cancelCapture(captureId) }

    suspend fun retry(captureId: String): Boolean {
        if (!runner.prepareRetry(captureId)) return false
        scheduler.enqueue(captureId)
        return true
    }
}
