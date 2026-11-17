package com.lifehub.app.feature.copilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lifehub.app.copilot.agent.MealSuggestionCard
import com.lifehub.app.copilot.agent.PlanDraftCard
import com.lifehub.app.copilot.agent.SuggestionItemView
import com.lifehub.app.copilot.agent.dayLabel
import com.lifehub.app.copilot.tools.MealCoverage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.feature.food.fmtGrams
import java.time.LocalDate
import kotlin.math.roundToInt

/** Live state of a draft shown as a card: its status and the active plans an Approve could archive (same weekdays). */
data class DraftLive(val status: PlanDraftStatus, val overlapping: List<String> = emptyList())

fun draftStatusLabel(s: PlanDraftStatus): String = when (s) {
    PlanDraftStatus.DRAFT -> "Plan draft"
    PlanDraftStatus.APPROVED -> "Plan approved"
    PlanDraftStatus.REJECTED -> "Draft rejected"
    PlanDraftStatus.SUPERSEDED -> "Draft replaced"
}

/** Body of a draft (goal line + sessions with exercises, sets and targets); shared by the chat card and the draft screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DraftContent(card: PlanDraftCard, muted: Boolean = false) {
    val c = LhTheme.colors
    val fg = if (muted) c.mutedForeground else c.foreground
    card.goalName?.let { g ->
        Text("Goal: $g" + (card.weeks?.let { " · $it weeks" } ?: ""), style = LhType.caption, color = c.mutedForeground, modifier = Modifier.testTag("draft-goal"))
    }
    card.plans.forEachIndexed { i, p ->
        if (i > 0 || card.goalName != null) HorizontalDivider(color = c.border, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth().testTag("draft-plan"), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = LhType.label, color = fg, modifier = Modifier.weight(1f))
            Text(p.weekdays, style = LhType.captionMuted)
        }
        p.exercises.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, style = LhType.body, color = fg, modifier = Modifier.weight(1f))
                if (e.isNew) { LhBadge("New", variant = LhBadgeVariant.Outline); Spacer(Modifier.width(6.dp)) }
                Text(e.summary, style = LhType.tnum(LhType.body), color = c.mutedForeground)
            }
        }
    }
}

/** "Plan draft": sessions, exercises with sets and reps; Approve / Reject / Open draft. Nothing is active until Approve. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanDraftCardView(card: PlanDraftCard, live: DraftLive?, enabled: Boolean, actions: CardActions, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val status = live?.status ?: PlanDraftStatus.DRAFT
    val open = status == PlanDraftStatus.DRAFT
    LhCard(modifier.fillMaxWidth().testTag("card-plan-draft"), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(draftStatusLabel(status), style = LhType.heading, color = if (open || status == PlanDraftStatus.APPROVED) c.foreground else c.mutedForeground, modifier = Modifier.weight(1f))
            if (open) LhBadge("Not active", variant = LhBadgeVariant.Warning)
        }
        Text(card.title, style = LhType.body, color = c.foreground, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        DraftContent(card, muted = !open && status != PlanDraftStatus.APPROVED)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (open) {
                LhButton("Approve", { actions.onApproveDraft(card.draftId) }, Modifier.testTag("draft-approve"), size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Check, enabled = enabled)
                LhButton("Reject", { actions.onRejectDraft(card.draftId) }, Modifier.testTag("draft-reject"), variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Close, enabled = enabled)
            }
            LhButton("Open draft", { actions.onOpenDraft(card.draftId) }, Modifier.testTag("draft-open"), variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
        }
    }
}

/** "x% of the 62 g protein left" lines for the kcal and macros that have a target (coverage of the remaining). */
fun coverageLines(card: MealSuggestionCard): List<String> = listOfNotNull(
    coverage("kcal", card.kcalRemaining, card.totalKcal, unit = ""),
    coverage("protein", card.proteinRemainingG, card.totalProteinG),
    coverage("carbs", card.carbsRemainingG, card.totalCarbsG),
    coverage("fat", card.fatRemainingG, card.totalFatG),
)

private fun coverage(label: String, remaining: Int?, meal: Double, unit: String = " g"): String? {
    remaining ?: return null
    val after = remaining - meal.roundToInt()
    val pct = MealCoverage.percent(remaining, meal)
    val left = if (unit.isEmpty()) "${fmtInt(remaining)} kcal" else "$remaining$unit $label"
    return when {
        pct == null -> "No $label left today"
        after >= 0 -> "$pct% of the $left left"
        else -> "$pct% of the $left left (${-after}${if (unit.isEmpty()) " kcal" else unit} over)"
    }
}

/**
 * "Meal suggestion": dish, items with grams, kcal and macros computed by the app, totals and coverage of the day's remaining.
 * "Log as meal" writes it now (capture with Undo); then the card shows Undo and Open in diary like "Meal logged".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealSuggestionCardView(card: MealSuggestionCard, logStatus: CaptureStatus?, enabled: Boolean, today: LocalDate, actions: CardActions, onOpenDiary: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val logged = logStatus != null && logStatus != CaptureStatus.FAILED && logStatus != CaptureStatus.CANCELLED
    val undone = logStatus == CaptureStatus.CANCELLED
    LhCard(modifier.fillMaxWidth().testTag("card-meal-suggestion"), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(when { logged -> "Suggestion logged"; undone -> "Suggestion log undone"; else -> "Meal suggestion" }, style = LhType.heading,
                color = if (undone) c.mutedForeground else c.foreground, modifier = Modifier.weight(1f))
            Text(dayLabel(LocalDate.parse(card.date), today), style = LhType.captionMuted)
        }
        Text(card.title, style = LhType.body, color = c.foreground, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        card.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = c.border, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            SuggestionRow(item)
        }
        HorizontalDivider(color = c.border, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth().testTag("suggestion-total"), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = LhType.label, color = c.foreground, modifier = Modifier.weight(1f))
            Text("${fmtInt(card.totalKcal.roundToInt())} kcal", style = LhType.label, color = c.foreground)
        }
        Text("P ${fmtMacro(card.totalProteinG)} · C ${fmtMacro(card.totalCarbsG)} · F ${fmtMacro(card.totalFatG)} g", style = LhType.captionMuted)
        val cov = coverageLines(card)
        if (cov.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.testTag("suggestion-coverage")) { cov.forEach { Text(it, style = LhType.caption, color = c.mutedForeground) } }
        }
        if (logStatus == CaptureStatus.FAILED) Text("Couldn't log this meal. Try again.", style = LhType.caption, color = c.destructive, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                logged -> {
                    LhButton("Undo", { actions.onUndo(card.captureId) }, Modifier.testTag("card-undo"), variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.Undo)
                    LhButton("Open in diary", { onOpenDiary(today) }, Modifier.testTag("card-open-diary"), variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Book)
                }
                undone -> LhButton("Undone", {}, Modifier.testTag("card-undo"), variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.AutoMirrored.Outlined.Undo, enabled = false)
                else -> LhButton("Log as meal", { actions.onLogSuggestion(card) }, Modifier.testTag("suggestion-log"), size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Restaurant, enabled = enabled)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(item: SuggestionItemView) {
    val c = LhTheme.colors
    Column(Modifier.fillMaxWidth().testTag("suggestion-item")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.name + (item.brand?.let { " · $it" } ?: ""), style = LhType.body, color = c.foreground, modifier = Modifier.weight(1f))
            Text("${fmtInt(item.kcal.roundToInt())} kcal", style = LhType.body, color = c.foreground)
        }
        Text("${fmtGrams(item.grams)} g · P ${fmtMacro(item.proteinG)} · C ${fmtMacro(item.carbsG)} · F ${fmtMacro(item.fatG)} g", style = LhType.captionMuted)
        Spacer(Modifier.height(4.dp))
        LhBadge(sourceLabel(item.source))
    }
}
