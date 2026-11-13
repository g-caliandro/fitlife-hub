package com.lifehub.app.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.DayTypeIcon
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.dayType
import com.lifehub.app.core.designsystem.label
import com.lifehub.app.data.model.DayType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.fmtTarget
import com.lifehub.app.core.designsystem.fmtWeight
import com.lifehub.app.data.model.PlanExercise

/** "⋮" button with a dropdown. Items: label to action. */
@Composable
fun OverflowMenu(items: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        LhIconButton(Icons.Outlined.MoreVert, "More", { open = true })
        DropdownMenu(open, { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label, style = LhType.body) }, onClick = { open = false; action() })
            }
        }
    }
}

/** "Mon · Sat" (compact schedule text used on plan cards / plan detail / goal detail). */
fun fmtWeekdays(days: Set<Int>): String =
    if (days.isEmpty()) "No fixed days"
    else days.sorted().joinToString(" · ") { java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) }

/** "4 × 6", "3 × MAX", "2 × 30 s", "3 × 8 +10 kg". */
fun planExerciseSummary(pe: PlanExercise): String =
    "${pe.sets} × ${fmtTarget(pe.targetType, pe.targetValue)}" + (pe.weightKg?.takeIf { it > 0 }?.let { " +${fmtWeight(it)} kg" } ?: "")

@Composable
fun NameValueRow(name: String, value: String, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = LhType.body, color = c.foreground, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = LhType.tnum(LhType.body), color = c.mutedForeground)
    }
}

/** 4 day-type squares (56dp). Tapping the selected one clears the day type. */
@Composable
fun DayTypeSelector(selected: DayType?, onSelect: (DayType?) -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayType.entries.forEach { t ->
            val on = t == selected
            val tone = c.dayType(t)
            Column(
                Modifier.selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(if (on) null else t) }).testTag("daytype-${t.name}"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                com.lifehub.app.core.designsystem.components.LhIconSquare(
                    t, Modifier.then(if (on) Modifier.border(2.dp, tone, LhRadius.itemShape) else Modifier), size = 56.dp, iconSize = 28.dp)
                Text(t.label(), style = LhType.labelSmall, color = if (on) c.foreground else c.mutedForeground)
            }
        }
    }
}

/** Assumed work time of a REPS / MAX set (a timed set uses its target seconds). */
internal const val WORK_SET_SEC = 40

/**
 * Approximate plan length in minutes: Σ sets × (work + rest), work = target seconds for timed sets else 40 s,
 * rest = the exercise's rest seconds. Rounded to the nearest 5 min (at least 5); 0 for a plan without exercises.
 */
fun estimatePlanMinutes(exercises: List<PlanExercise>): Int {
    if (exercises.isEmpty()) return 0
    val sec = exercises.sumOf { pe ->
        val work = if (pe.targetType == com.lifehub.app.data.model.TargetType.SECONDS) pe.targetValue ?: WORK_SET_SEC else WORK_SET_SEC
        pe.sets * (work + pe.restSeconds)
    }
    return (Math.round(sec / 300.0) * 5).toInt().coerceAtLeast(5)
}
