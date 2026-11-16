package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.agent.AgentLoop
import com.lifehub.app.copilot.agent.ToolPolicy
import com.lifehub.app.copilot.agent.TurnOptions
import com.lifehub.app.copilot.agent.TurnResult
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.data.repo.ChatStore
import com.lifehub.app.data.repo.NewChatMessage
import java.io.File

/**
 * Side effects of closing things, so every place that shows them stays in sync: a request answered or discarded in the
 * chat, the inbox or a notification action closes it everywhere (including cancelling its notification).
 */
interface CaptureEvents {
    fun pendingClosed(pendingId: String) {}
    fun captureUndone(captureId: String) {}
    object None : CaptureEvents
}

/** Outcome of running (part of) a capture: its id and how the agent turn ended. */
data class CaptureRun(val captureId: String, val turn: TurnResult)

/**
 * Takes a capture from RECORDED to a final state (design doc §4): every chat message that can write goes through here.
 * Phase 4 adds voice (the worker transcribes, then calls [process]) and notifications; this class stays the single entry
 * point, independent from the chat UI:
 * - [submitText]: new text capture in a thread -> USER message -> agent turn.
 * - [answer] / [discard]: close a pending request (from the chat now; inbox / notification in phase 4). An answer appends
 *   a USER message and resumes the capture with a new turn whose context lists the answered requests, so the new items
 *   join the same meal (same capture, same date and time).
 * - [retry]: re-runs a FAILED capture from its stored thread; writes are idempotent per capture, nothing is duplicated.
 * - [undo]: app action (never a model tool), see [FoodLogWriter.undo].
 */
class CaptureProcessor(
    private val captures: CaptureStore,
    private val pending: PendingInputStore,
    private val writer: FoodLogWriter,
    private val agent: AgentLoop,
    private val chat: ChatStore,
    private val time: TimeSource,
    private val events: CaptureEvents = CaptureEvents.None,
    private val attached: AttachedProductContext? = null,
    private val deleteAudio: (String) -> Unit = { runCatching { File(it).delete() } },
) {
    /** [attachedFoodId]: a product scanned in the composer; its data enters every turn of this capture (retry and resume too). */
    suspend fun submitText(threadId: String, text: String, origin: ChatOrigin = ChatOrigin.CHAT, attachedFoodId: String? = null): CaptureRun {
        val t = text.trim()
        val c = captures.create(threadId, origin, t, time.zone, time.now(), attachedFoodId = attachedFoodId)
        chat.append(threadId, NewChatMessage(ChatRole.USER, t, captureId = c.id), time.now())
        return process(c.id)
    }

    suspend fun retry(captureId: String): CaptureRun? {
        val c = captures.get(captureId) ?: return null
        if (c.status != CaptureStatus.FAILED) return null
        return process(captureId)
    }

    /** Answers [pendingId] with an option index or a free-text reply, then resumes its capture. */
    suspend fun answer(pendingId: String, option: Int? = null, text: String? = null): CaptureRun? {
        val captureId = recordAnswer(pendingId, option, text) ?: return null
        return process(captureId)
    }

    /**
     * The fast half of [answer] (DB only, safe from a notification action): closes the request, saves a package weight,
     * appends the answer to the thread and moves the capture to PROCESSING. Returns the capture to resume (with [process]
     * now, or later by the CaptureWorker), or null when the capture was undone. Throws [IllegalTransitionException] when
     * the request was already answered or discarded somewhere else.
     */
    suspend fun recordAnswer(pendingId: String, option: Int? = null, text: String? = null): String? {
        val before = pending.get(pendingId) ?: return null
        val c = captures.get(before.captureId) ?: return null
        if (c.status == CaptureStatus.CANCELLED) return null
        val p = pending.answer(pendingId, PendingAnswer(option, text?.trim()?.takeIf { it.isNotEmpty() }), time.now())
        events.pendingClosed(pendingId)
        // A FAILED capture with requests still open resumes too (the answer is a new user gesture, like Retry).
        if (c.status == CaptureStatus.AWAITING_INPUT || c.status == CaptureStatus.FAILED) captures.move(c.id, CaptureStatus.PROCESSING, time.now())
        if (p.kind == PendingKind.PACKAGE_WEIGHT) savePackageWeight(p)
        val threadId = p.threadId ?: c.threadId ?: return null
        chat.append(threadId, NewChatMessage(ChatRole.USER, answerText(p), captureId = c.id), time.now())
        return c.id
    }

    /** Discards [pendingId]; when it was the last open request of an AWAITING_INPUT capture, the capture is DONE. */
    suspend fun discard(pendingId: String) {
        val p = pending.discard(pendingId, time.now())
        events.pendingClosed(pendingId)
        val c = captures.get(p.captureId) ?: return
        if (c.status == CaptureStatus.AWAITING_INPUT && pending.byCapture(c.id).none { it.status == PendingStatus.OPEN }) {
            captures.move(c.id, CaptureStatus.DONE, time.now())
            releaseAudio(c.id)  // nothing is left to retry: the recording is not needed any more
        }
    }

    /** Deletes the capture's audio file (if any) and forgets its path; the transcript stays. */
    private suspend fun releaseAudio(captureId: String) {
        val path = captures.get(captureId)?.audioPath ?: return
        deleteAudio(path)
        captures.clearAudio(captureId, time.now())
    }

    suspend fun undo(captureId: String): UndoResult {
        val open = pending.byCapture(captureId).filter { it.status == PendingStatus.OPEN }.map { it.id }
        val r = writer.undo(captureId, time.now())
        if (r is UndoResult.Undone) { open.forEach(events::pendingClosed); events.captureUndone(captureId); releaseAudio(captureId) }
        return r
    }

    /** Runs the agent for [captureId] (status -> PROCESSING) and stores where it ended. */
    suspend fun process(captureId: String): CaptureRun {
        val c = captures.move(captureId, CaptureStatus.PROCESSING, time.now())
        val threadId = c.threadId ?: error("capture without thread")
        // Quick (voice) captures never get chat-only tools such as save_user_memory (design doc §10).
        val turn = agent.run(threadId, TurnOptions(toolFilter = ToolPolicy.forOrigin(c.origin), captureId = captureId, extraContext = listOfNotNull(c.attachedFoodId?.let { attached?.section(it) }, resumeContext(captureId))))
        val now = captures.get(captureId) ?: return CaptureRun(captureId, turn)
        if (now.status == CaptureStatus.CANCELLED) return CaptureRun(captureId, turn)
        when (turn) {
            is TurnResult.Failed -> captures.move(captureId, CaptureStatus.FAILED, time.now(), errorCode = turn.error::class.simpleName, errorMessage = turn.error.message)
            is TurnResult.Answered, is TurnResult.AwaitingInput -> {
                val open = pending.byCapture(captureId).any { it.status == PendingStatus.OPEN }
                captures.move(captureId, if (open) CaptureStatus.AWAITING_INPUT else CaptureStatus.DONE, time.now(),
                    outcome = (turn as? TurnResult.Answered)?.text)
            }
        }
        return CaptureRun(captureId, turn)
    }

    private suspend fun savePackageWeight(p: PendingInput) {
        val grams = PendingCodec.answer(p)?.text?.let(::parseGrams) ?: return
        val food = PendingCodec.item(p)?.food ?: return
        writer.savePackageWeight(p.captureId, food, grams, time.now())
    }

    private fun answerText(p: PendingInput): String {
        val a = PendingCodec.answer(p) ?: return ""
        val o = a.option?.let { PendingCodec.options(p).getOrNull(it) }
        return when {
            o != null && o.isEstimate -> "${o.label} (stima)"
            o != null -> o.label
            else -> a.text.orEmpty()
        }
    }

    /**
     * Model-facing section for a resumed capture, rebuilt from the DB on every run (so a retry after an app restart has the
     * same information): answered requests with the chosen ref and the item to log, and how many are still open.
     */
    private suspend fun resumeContext(captureId: String): String? {
        val all = pending.byCapture(captureId)
        if (all.isEmpty()) return null
        val answered = all.filter { it.status == PendingStatus.ANSWERED }
        val open = all.count { it.status == PendingStatus.OPEN }
        val discarded = all.filter { it.status == PendingStatus.DISCARDED }
        return buildString {
            append("Questa richiesta riprende dopo una risposta dell'utente. Registra gli item risposti con log_meal: si aggiungono allo stesso pasto (stessa data e ora). Non registrare di nuovo gli item già registrati.")
            answered.forEach { p ->
                val a = PendingCodec.answer(p)
                val o = a?.option?.let { PendingCodec.options(p).getOrNull(it) }
                val item = PendingCodec.item(p)
                append("\n- Domanda \"${p.prompt}\" (${p.kind.name.lowercase()})")
                item?.let { append("; item: ${PendingCodec.encodeItem(it.copy(food = null))}") }
                when {
                    o != null -> append("; scelta: \"${o.label}\" ref=${o.ref}" + if (o.isEstimate) " (stima scelta dall'utente)" else "")
                    a?.text != null -> append("; risposta libera: \"${a.text}\"")
                }
                if (p.kind == PendingKind.PACKAGE_WEIGHT) append("; il peso della confezione è stato salvato sul prodotto ${p.foodRef}: usa servings con portion 'package'.")
            }
            discarded.forEach { append("\n- Scartata: \"${it.prompt}\": non registrare quell'item.") }
            if (open > 0) append("\nRichieste ancora aperte: $open. Non ripeterle.")
        }
    }

    companion object {
        /** "400", "400 g", "0,4 kg" -> grams; null when no positive number is found. */
        fun parseGrams(text: String): Double? {
            val m = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g|gr|grammi)?\b""", RegexOption.IGNORE_CASE).find(text) ?: return null
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val g = if (m.groupValues[2].equals("kg", true)) v * 1000 else v
            return g.takeIf { it > 0 && it <= 20_000 }
        }
    }
}
