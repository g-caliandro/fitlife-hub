package com.lifehub.app.copilot.capture

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Captures (design doc "CaptureStore"): text/audio, state, outcome; the records each capture wrote are in capture_writes. */
class CaptureStore(db: LifeHubDatabase) {
    private val dao = db.captureDao()

    /**
     * [audioPath]: a dictated capture (phase 4) whose text comes from the transcriber; [text] is then null.
     * [attachedFoodId]: the scanned product attached to the message (phase 5), a cached food.
     */
    suspend fun create(threadId: String?, origin: ChatOrigin, text: String?, zone: ZoneId, now: Instant, audioPath: String? = null, attachedFoodId: String? = null, id: String = UUID.randomUUID().toString()): Capture {
        val c = Capture(id, threadId, origin, CaptureStatus.RECORDED, text, audioPath = audioPath, attachedFoodId = attachedFoodId, zoneId = zone.id, createdAt = now, updatedAt = now)
        dao.upsert(c)
        return c
    }

    suspend fun get(id: String) = dao.get(id)
    fun observe(id: String) = dao.observe(id)
    fun observeByThread(threadId: String) = dao.observeByThread(threadId)
    suspend fun byStatus(statuses: List<CaptureStatus>) = dao.byStatus(statuses)
    fun observeByStatus(statuses: List<CaptureStatus>) = dao.observeByStatus(statuses)

    /** Stores the transcript of a dictated capture (set once; later calls are ignored). */
    suspend fun setTranscript(id: String, transcript: String, now: Instant) {
        dao.get(id)?.takeIf { it.transcript == null }?.let { dao.upsert(it.copy(transcript = transcript, updatedAt = now)) }
    }

    /** Clears [Capture.audioPath] after the audio file was deleted. */
    suspend fun clearAudio(id: String, now: Instant) {
        dao.get(id)?.takeIf { it.audioPath != null }?.let { dao.upsert(it.copy(audioPath = null, updatedAt = now)) }
    }

    /** Moves [id] to [to] if [CaptureStateMachine] allows it, else throws [IllegalTransitionException]. */
    suspend fun move(id: String, to: CaptureStatus, now: Instant, outcome: String? = null, errorCode: String? = null, errorMessage: String? = null): Capture {
        val c = dao.get(id) ?: throw IllegalTransitionException("Unknown capture $id")
        if (c.status == to) return c
        if (!CaptureStateMachine.canMove(c.status, to)) throw IllegalTransitionException("Capture ${c.status} -> $to not allowed")
        val next = c.copy(
            status = to, updatedAt = now,
            outcome = outcome ?: c.outcome,
            errorCode = if (to == CaptureStatus.FAILED) errorCode else null,
            errorMessage = if (to == CaptureStatus.FAILED) errorMessage else null,
            attempts = if (to == CaptureStatus.PROCESSING) c.attempts + 1 else c.attempts,
        )
        dao.upsert(next)
        return next
    }

}
