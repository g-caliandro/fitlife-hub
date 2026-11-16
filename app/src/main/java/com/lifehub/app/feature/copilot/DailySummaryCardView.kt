package com.lifehub.app.feature.copilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.MetricKind
import com.lifehub.app.core.designsystem.color
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhMetricBar
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.copilot.agent.DailySummaryCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Daily summary" card, drawn only from the persisted [DailySummaryCard] (the diary's rounded day totals and the
 * app-computed remaining amounts), so the numbers match the Diary exactly.
 */
@Composable
fun DailySummaryCardView(card: DailySummaryCard, today: LocalDate, onOpenDiary: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val date = LocalDate.parse(card.date)
    val dayLabel = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
    }
    LhCard(modifier.fillMaxWidth().testTag("card-daily-summary"), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily summary", style = LhType.heading, color = c.foreground, modifier = Modifier.weight(1f))
            Text(dayLabel, style = LhType.captionMuted)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val kt = card.kcalTarget
            LhMetricBar(
                MetricKind.Calories,
                if (kt != null) "${fmtInt(card.kcal)} / ${fmtInt(kt)} kcal" else "${fmtInt(card.kcal)} kcal",
                if (kt != null) card.kcal / kt.toFloat() else 0f,
                color = if (kt != null && card.kcal > kt) c.warning else MetricKind.Calories.color(),
                caption = leftCaption(card.kcalRemaining, kt, "kcal", known = true, complete = true),
            )
            MacroBar(MetricKind.Protein, card.proteinG, card.proteinTargetG, card.proteinRemainingG, card.proteinKnown, card.proteinComplete)
            MacroBar(MetricKind.Carbs, card.carbsG, card.carbsTargetG, card.carbsRemainingG, card.carbsKnown, card.carbsComplete)
            MacroBar(MetricKind.Fat, card.fatG, card.fatTargetG, card.fatRemainingG, card.fatKnown, card.fatComplete)
        }
        Spacer(Modifier.height(12.dp))
        LhButton("Open in diary", { onOpenDiary(date) }, Modifier.testTag("card-open-diary"),
            variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Book)
    }
}

@Composable
private fun MacroBar(metric: MetricKind, value: Int, target: Int?, remaining: Int?, known: Boolean, complete: Boolean) {
    val shown = if (known) "$value" else "—"
    LhMetricBar(
        metric,
        if (target != null) "$shown / $target g" else "$shown g",
        if (target != null && known) value / target.toFloat() else 0f,
        caption = leftCaption(remaining, target, "g", known, complete),
    )
}

private fun leftCaption(remaining: Int?, target: Int?, unit: String, known: Boolean, complete: Boolean): String = when {
    !known -> "Unknown (not logged for every item)"
    target == null -> "No target set"
    remaining == null -> "No target set"
    remaining < 0 -> "${fmtInt(-remaining)} $unit over" + if (!complete) " (at least)" else ""
    else -> "${fmtInt(remaining)} $unit left" + if (!complete) " (at most, some items lack this value)" else ""
}
