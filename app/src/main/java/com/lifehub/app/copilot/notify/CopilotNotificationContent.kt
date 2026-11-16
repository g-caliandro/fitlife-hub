package com.lifehub.app.copilot.notify

import com.lifehub.app.copilot.capture.PendingCodec
import com.lifehub.app.copilot.agent.dayLabel
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.feature.food.fmtGrams
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** The three copilot notification types (design doc §11), one Android channel each. */
enum class CopilotChannel(val id: String, val title: String) {
    OUTCOME("copilot_outcome", "Copilot: logged"),
    NEEDS_ANSWER("copilot_needs_answer", "Copilot: needs an answer"),
    ERROR("copilot_error", "Copilot: errors"),
}

enum class ActionKind { UNDO, RETRY, CHOOSE, REPLY }

/** [option] = index of the pending option for CHOOSE. */
data class NotificationAction(val kind: ActionKind, val label: String, val option: Int? = null)

/**
 * Platform-independent content of one notification. [tag] identifies it (one per capture for outcome/error, so an error
 * is replaced by the outcome after a Retry; one per pending request), [route] = screen opened by a tap.
 */
data class NotificationSpec(
    val tag: String,
    val channel: CopilotChannel,
    val title: String,
    val text: String,
    val actions: List<NotificationAction>,
    val captureId: String,
    val pendingId: String? = null,
    val route: String,
) {
    init { require(actions.size <= MAX_ACTIONS) { "Android shows at most $MAX_ACTIONS actions" } }
    companion object { const val MAX_ACTIONS = 3 }
}

/** Pure builders for the copilot notifications (unit-tested). English chrome; the copilot's own words stay Italian. */
object CopilotNotificationContent {
    fun captureTag(captureId: String) = "copilot-capture:$captureId"
    fun pendingTag(pendingId: String) = "copilot-pending:$pendingId"

    /** Outcome: what was logged and the total kcal, with Undo; without entries, the copilot's reply (no Undo). */
    fun outcome(capture: Capture, entries: List<FoodEntry>, route: String, weights: List<BodyEntry> = emptyList(), today: LocalDate? = null): NotificationSpec {
        // A log that did not land on today says which day (retroactive logs: "yesterday", "Sun 15 Nov").
        fun day(d: LocalDate): String? = if (today == null || d == today) null else if (d == today.minusDays(1)) "yesterday" else dayLabel(d, today)
        fun suffix(d: LocalDate) = day(d)?.let { " · $it" }.orEmpty()
        val weightLines = weights.map { "Weight ${fmtKg(it.weightKg)} kg" + (day(it.date)?.let { d -> " ($d)" }.orEmpty()) }
        if (entries.isEmpty() && weights.isNotEmpty()) {
            val w = weights.first()
            val title = "Weight logged · ${fmtKg(w.weightKg)} kg" + suffix(w.date)
            val text = (weightLines.drop(1) + listOfNotNull(capture.outcome?.takeIf { it.isNotBlank() })).joinToString("\n")
            return NotificationSpec(captureTag(capture.id), CopilotChannel.OUTCOME, title, text.ifEmpty { weightLines.first() }, listOf(NotificationAction(ActionKind.UNDO, "Undo")), capture.id, route = route)
        }
        if (entries.isEmpty()) {
            return NotificationSpec(
                captureTag(capture.id), CopilotChannel.OUTCOME, "Copilot",
                capture.outcome?.takeIf { it.isNotBlank() } ?: capture.transcript ?: "Done.",
                emptyList(), capture.id, route = route,
            )
        }
        val kcal = entries.sumOf { it.kcal }.roundToInt()
        val items = entries.joinToString(", ") { "${it.name} ${fmtGrams(it.grams)} g" }
        val title = "Logged ${entries.size} item" + (if (entries.size == 1) "" else "s") + " · $kcal kcal" + suffix(entries.first().date)
        val text = (listOf(items) + weightLines + listOfNotNull(capture.outcome?.takeIf { it.isNotBlank() })).joinToString("\n")
        return NotificationSpec(captureTag(capture.id), CopilotChannel.OUTCOME, title, text, listOf(NotificationAction(ActionKind.UNDO, "Undo")), capture.id, route = route)
    }

    /**
     * Needs an answer: the question, at most 2 quick choices + inline Reply (owner decision 14, Android's 3-action limit).
     * Requests without options (quantity, package weight) get Reply only.
     */
    fun needsAnswer(p: PendingInput, route: String): NotificationSpec {
        val item = PendingCodec.item(p)?.name
        val choices = PendingCodec.options(p).take(2).mapIndexed { i, o ->
            NotificationAction(ActionKind.CHOOSE, shortLabel(o.label) + if (o.isEstimate) " (estimate)" else "", option = i)
        }
        return NotificationSpec(
            pendingTag(p.id), CopilotChannel.NEEDS_ANSWER, "Copilot needs an answer" + (item?.let { ": $it" } ?: ""),
            p.prompt, choices + NotificationAction(ActionKind.REPLY, "Reply"), p.captureId, p.id, route,
        )
    }

    /** Error: the cause, with Retry (the capture and its audio are kept). */
    fun error(capture: Capture, route: String): NotificationSpec {
        val said = capture.transcript ?: capture.inputText
        val text = listOfNotNull(capture.errorMessage ?: "Something went wrong.", said?.let { "“${it.take(80)}”" }, "Your recording is saved.")
            .joinToString("\n")
        return NotificationSpec(captureTag(capture.id), CopilotChannel.ERROR, "Couldn't process your voice note", text,
            listOf(NotificationAction(ActionKind.RETRY, "Retry")), capture.id, route = route)
    }

    private fun fmtKg(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun shortLabel(s: String) = if (s.length <= 24) s else s.take(23).trimEnd() + "…"
}
