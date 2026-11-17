package com.lifehub.app.feature.copilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lifehub.app.copilot.agent.MealItemView
import com.lifehub.app.copilot.agent.MealLoggedCard
import com.lifehub.app.copilot.agent.PendingInputCard
import com.lifehub.app.copilot.agent.WeightLoggedCard
import com.lifehub.app.copilot.agent.dayLabel
import com.lifehub.app.copilot.agent.ProductCard
import com.lifehub.app.copilot.capture.PendingCodec
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.feature.food.fmtGrams
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Actions of the copilot food cards (all app actions; none of them is a model tool). */
data class CardActions(
    val onUndo: (captureId: String) -> Unit = {},
    val onChoose: (pendingId: String, option: Int) -> Unit = { _, _ -> },
    val onReply: (pendingId: String, text: String) -> Unit = { _, _ -> },
    val onDiscard: (pendingId: String) -> Unit = {},
    /** Plan draft card: Approve opens the confirmation with the "archive plans on the same days" checkbox. */
    val onApproveDraft: (draftId: String) -> Unit = {},
    val onRejectDraft: (draftId: String) -> Unit = {},
    val onOpenDraft: (draftId: String) -> Unit = {},
    /** Meal suggestion card: "Log as meal". */
    val onLogSuggestion: (com.lifehub.app.copilot.agent.MealSuggestionCard) -> Unit = {},
)

/** Grams with one decimal, like the per-item macros everywhere in the copilot. */
internal fun fmtMacro(v: Double): String = String.format(Locale.US, "%.1f", v)

internal fun sourceLabel(wire: String): String = when (wire) {
    "history" -> "History"
    "base_table" -> "Base table"
    "open_food_facts" -> "Open Food Facts"
    "llm_estimate" -> "Estimate"
    else -> wire
}

/** "Meal logged": per item grams, kcal, macros, source badge and portion used; total; day's remaining; Undo / Open in diary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealLoggedCardView(card: MealLoggedCard, undone: Boolean, today: LocalDate, onUndo: (String) -> Unit, onOpenDiary: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val date = LocalDate.parse(card.date)
    LhCard(modifier.fillMaxWidth().testTag("card-meal-logged"), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (undone) "Meal log undone" else "Meal logged", style = LhType.heading, color = if (undone) c.mutedForeground else c.foreground, modifier = Modifier.weight(1f))
            Text(dayLabel(date, today) + (card.time?.let { " · $it" } ?: ""), style = LhType.captionMuted)
        }
        Spacer(Modifier.height(8.dp))
        card.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = c.border, thickness = 1.dp, modifier = Modifier.padding8())
            MealItemRow(item, undone)
        }
        HorizontalDivider(color = c.border, thickness = 1.dp, modifier = Modifier.padding8())
        Row(Modifier.fillMaxWidth().testTag("meal-total"), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = LhType.label, color = c.foreground, modifier = Modifier.weight(1f))
            Text("${fmtInt(card.totalKcal.roundToInt())} kcal", style = LhType.label, color = c.foreground)
        }
        Text("P ${fmtMacro(card.totalProteinG)} · C ${fmtMacro(card.totalCarbsG)} · F ${fmtMacro(card.totalFatG)} g", style = LhType.captionMuted)
        remainingText(card, today)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = LhType.caption, color = c.mutedForeground, modifier = Modifier.testTag("meal-remaining"))
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LhButton(if (undone) "Undone" else "Undo", { onUndo(card.captureId) }, Modifier.testTag("card-undo"),
                variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.Undo, enabled = !undone)
            LhButton("Open in diary", { onOpenDiary(date) }, Modifier.testTag("card-open-diary"),
                variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Book, enabled = !undone)
        }
    }
}

private fun Modifier.padding8() = this.then(Modifier.padding(vertical = 8.dp))

private fun remainingText(card: MealLoggedCard, today: LocalDate): String? {
    val parts = listOfNotNull(
        card.kcalRemaining?.let { if (it >= 0) "${fmtInt(it)} kcal left" else "${fmtInt(-it)} kcal over" },
        card.proteinRemainingG?.let { if (it >= 0) "$it g protein left" else "${-it} g protein over" },
    )
    // The remaining amounts are those of the day the meal was logged on, not necessarily today.
    return if (parts.isEmpty()) null else "${dayLabel(LocalDate.parse(card.date), today)} after this meal: " + parts.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealItemRow(item: MealItemView, undone: Boolean) {
    val c = LhTheme.colors
    val fg = if (undone) c.mutedForeground else c.foreground
    Column(Modifier.fillMaxWidth().testTag("meal-item")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.name + (item.brand?.let { " · $it" } ?: ""), style = LhType.body, color = fg, modifier = Modifier.weight(1f))
            Text("${fmtInt(item.kcal.roundToInt())} kcal", style = LhType.body, color = fg)
        }
        val portion = item.portionLabel?.let { label ->
            val n = item.servings?.let(::fmtGrams) ?: "1"
            " · $n × $label" + (item.portionGrams?.let { " (${fmtGrams(it)} g)" } ?: "")
        }.orEmpty()
        Text("${fmtGrams(item.grams)} g$portion", style = LhType.captionMuted)
        Text("P ${fmtMacro(item.proteinG)} · C ${fmtMacro(item.carbsG)} · F ${fmtMacro(item.fatG)} g", style = LhType.captionMuted)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LhBadge(sourceLabel(item.source), variant = if (item.source == "llm_estimate") LhBadgeVariant.Warning else LhBadgeVariant.Secondary)
            if (item.usedDefaultPortion) LhBadge("Default portion", variant = LhBadgeVariant.Outline)
            if (item.incomplete) LhBadge("Incomplete values", variant = LhBadgeVariant.Warning)
            if (item.stateMismatch) LhBadge("Raw values used", variant = LhBadgeVariant.Warning)
        }
    }
}

/** "Weight logged": value and date, with Undo (restores the day's previous weight, or removes a new day's). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightLoggedCardView(card: WeightLoggedCard, undone: Boolean, today: LocalDate, onUndo: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val date = LocalDate.parse(card.date)
    LhCard(modifier.fillMaxWidth().testTag("card-weight-logged"), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (undone) "Weight log undone" else "Weight logged", style = LhType.heading, color = if (undone) c.mutedForeground else c.foreground, modifier = Modifier.weight(1f))
            Text(dayLabel(date, today), style = LhType.captionMuted, modifier = Modifier.testTag("weight-date"))
        }
        Spacer(Modifier.height(8.dp))
        Text("${fmtMacro(card.weightKg)} kg", style = LhType.heading, color = if (undone) c.mutedForeground else c.foreground, modifier = Modifier.testTag("weight-value"))
        card.previousWeightKg?.let { Text("Replaced ${fmtMacro(it)} kg", style = LhType.captionMuted) }
        Spacer(Modifier.height(12.dp))
        LhButton(if (undone) "Undone" else "Undo", { onUndo(card.captureId) }, Modifier.testTag("card-undo"),
            variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.Undo, enabled = !undone)
    }
}

/** "Pending request": question + options (an estimate is marked as such) or a reply field; Choose / Reply / Discard. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingInputCardView(card: PendingInputCard, live: PendingInput?, enabled: Boolean, actions: CardActions, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val status = live?.status ?: PendingStatus.OPEN
    LhCard(modifier.fillMaxWidth().testTag("card-pending"), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (status) { PendingStatus.OPEN -> "Needs your answer"; PendingStatus.ANSWERED -> "Answered"; PendingStatus.DISCARDED -> "Discarded" },
                style = LhType.heading, color = if (status == PendingStatus.OPEN) c.foreground else c.mutedForeground, modifier = Modifier.weight(1f),
            )
        }
        card.itemName?.let { Text(it, style = LhType.captionMuted) }
        Spacer(Modifier.height(4.dp))
        Text(card.prompt, style = LhType.body, color = c.foreground)
        when (status) {
            PendingStatus.OPEN -> {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.options.forEachIndexed { i, o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LhButton(o.label, { actions.onChoose(card.pendingInputId, i) }, Modifier.testTag("pending-option-$i"),
                                variant = LhButtonVariant.Tonal, size = LhButtonSize.Sm, enabled = enabled)
                            if (o.isEstimate) { Spacer(Modifier.width(8.dp)); LhBadge("Estimate", variant = LhBadgeVariant.Warning) }
                        }
                        o.detail?.let { Text(it, style = LhType.captionMuted) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                var reply by rememberSaveable(card.pendingInputId) { mutableStateOf("") }
                LhTextField(reply, { reply = it }, Modifier.fillMaxWidth().testTag("pending-reply"), placeholder = "Reply", enabled = enabled, maxLength = 300)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LhButton("Reply", { actions.onReply(card.pendingInputId, reply); reply = "" }, Modifier.testTag("pending-send"),
                        size = LhButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.Reply, enabled = enabled && reply.isNotBlank())
                    LhButton("Discard", { actions.onDiscard(card.pendingInputId) }, Modifier.testTag("pending-discard"),
                        variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Close, enabled = enabled)
                }
            }
            PendingStatus.ANSWERED -> {
                val a = live?.let(PendingCodec::answer)
                val chosen = a?.option?.let { card.options.getOrNull(it) }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        chosen != null -> "Chosen: ${chosen.label}" + if (chosen.isEstimate) " (estimate)" else ""
                        a?.text != null -> "Replied: ${a.text}"
                        else -> "Answered"
                    },
                    style = LhType.caption, color = c.mutedForeground,
                )
            }
            PendingStatus.DISCARDED -> Unit
        }
    }
}

/** "Product": name, brand, values per 100 g, known portions. No actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductCardView(card: ProductCard, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    LhCard(modifier.fillMaxWidth().testTag("card-product"), contentPadding = PaddingValues(16.dp)) {
        Text(card.name, style = LhType.heading, color = c.foreground)
        card.brand?.let { Text(it, style = LhType.captionMuted) }
        Spacer(Modifier.height(8.dp))
        Text("Per 100 g: ${card.kcal100.roundToInt()} kcal · P ${fmtMacro(card.protein100)} · C ${fmtMacro(card.carbs100)} · F ${fmtMacro(card.fat100)} g", style = LhType.body, color = c.foreground)
        if (card.portions.isNotEmpty()) Text("Portions: " + card.portions.joinToString(", "), style = LhType.captionMuted)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LhBadge(sourceLabel(card.source))
            if (card.incomplete) LhBadge("Incomplete values", variant = LhBadgeVariant.Warning)
        }
    }
}
