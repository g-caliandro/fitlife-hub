package com.lifehub.app.copilot.capture

import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.repo.ChatStore
import java.io.File

/**
 * Turns a finished recording into a saved capture (design doc §4 step 1): from the moment [saveQuick] / [saveInThread]
 * returns, the audio is on disk and the capture is RECORDED in the DB, so nothing is lost if the app dies or the phone
 * locks; the transcription and the model turn happen later (CaptureWorker or the chat).
 */
class VoiceCaptures(
    private val chat: ChatStore,
    private val captures: CaptureStore,
    private val scheduler: CaptureScheduler,
    private val time: TimeSource,
) {
    /** Quick capture: a NEW thread per capture (found later in the chat), then queued for the worker. */
    suspend fun saveQuick(audio: File): Capture {
        val now = time.now()
        val thread = chat.createThread(QUICK_TITLE, ChatOrigin.QUICK, now)
        val c = captures.create(thread.id, ChatOrigin.QUICK, text = null, zone = time.zone, now = now, audioPath = audio.absolutePath)
        scheduler.enqueue(c.id)
        return c
    }

    /** Chat mic: the capture joins [threadId]; the chat runs it in place (not queued). */
    suspend fun saveInThread(threadId: String, audio: File, attachedFoodId: String? = null): Capture =
        captures.create(threadId, ChatOrigin.CHAT, text = null, zone = time.zone, now = time.now(), audioPath = audio.absolutePath, attachedFoodId = attachedFoodId)

    companion object { const val QUICK_TITLE = "Voice capture" }
}
