package com.lifehub.app.copilot.notify

import com.lifehub.app.copilot.capture.CaptureEvents
import com.lifehub.app.copilot.capture.CaptureNotifier
import com.lifehub.app.copilot.capture.CaptureStore
import com.lifehub.app.copilot.capture.PendingInputStore
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.navigation.Routes

/** Where notifications go: Android's NotificationManager in the app, a recorder in tests. */
interface NotificationSink {
    /** False when the system would not show it (permission denied, channel blocked). */
    fun post(spec: NotificationSpec): Boolean
    fun cancel(tag: String)
}

/**
 * Design doc "NotificationCenter". Publishes the result of a QUICK capture (chat captures are seen in the chat):
 * - DONE -> Outcome (what was logged, kcal, Undo);
 * - AWAITING_INPUT -> Outcome for what is already logged + one "Needs an answer" per open request, each posted ONCE
 *   (`pending_inputs.notifiedAt`): dismissing or ignoring it never closes the request, and it is never re-posted;
 * - FAILED -> Error with Retry; CANCELLED -> its notification is removed.
 * As [CaptureEvents] it removes a request's notification as soon as the request is answered or discarded anywhere
 * (chat, inbox, notification action) and the capture's notification after Undo.
 */
class NotificationCenter(
    private val captures: CaptureStore,
    private val pending: PendingInputStore,
    private val entries: suspend (captureId: String) -> List<FoodEntry>,
    private val sink: NotificationSink,
    private val enabled: () -> Boolean,
    private val time: TimeSource,
) : CaptureNotifier, CaptureEvents {

    override suspend fun publish(captureId: String) {
        val c = captures.get(captureId) ?: return
        if (c.origin != ChatOrigin.QUICK) return
        val tag = CopilotNotificationContent.captureTag(c.id)
        val thread = Routes.copilot(c.threadId)
        when (c.status) {
            CaptureStatus.CANCELLED -> sink.cancel(tag)
            CaptureStatus.FAILED -> if (enabled()) sink.post(CopilotNotificationContent.error(c, Routes.COPILOT_INBOX))
            CaptureStatus.DONE -> if (enabled()) sink.post(CopilotNotificationContent.outcome(c, entries(c.id), thread))
            CaptureStatus.AWAITING_INPUT -> {
                if (!enabled()) return
                val logged = entries(c.id)
                if (logged.isNotEmpty()) sink.post(CopilotNotificationContent.outcome(c, logged, thread)) else sink.cancel(tag)
                pending.byCapture(c.id).filter { it.status == PendingStatus.OPEN && it.notifiedAt == null }.forEach { p ->
                    if (pending.markNotified(p.id, time.now())) sink.post(CopilotNotificationContent.needsAnswer(p, Routes.COPILOT_INBOX))
                }
            }
            CaptureStatus.RECORDED, CaptureStatus.PROCESSING -> Unit
        }
    }

    /**
     * After an empty inline reply Android keeps showing the reply spinner until the notification is updated: re-post the
     * same content (same tag, alert only once) while the request is still open, else remove it.
     */
    suspend fun repostIfOpen(pendingId: String) {
        val p = pending.get(pendingId)
        if (p == null || p.status != PendingStatus.OPEN || !enabled()) { pendingClosed(pendingId); return }
        sink.post(CopilotNotificationContent.needsAnswer(p, Routes.COPILOT_INBOX))
    }

    override fun cancelCapture(captureId: String) = sink.cancel(CopilotNotificationContent.captureTag(captureId))
    override fun pendingClosed(pendingId: String) = sink.cancel(CopilotNotificationContent.pendingTag(pendingId))
    override fun captureUndone(captureId: String) = cancelCapture(captureId)
}
