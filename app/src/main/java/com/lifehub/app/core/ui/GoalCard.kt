package com.lifehub.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhProgressBar
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.domain.PlanProgress
import com.lifehub.app.domain.TimeProgress

data class GoalCardModel(
    val id: String,
    val name: String,
    val state: GoalState,
    val time: TimeProgress,
    val plans: List<PlanProgress>,
    /** Optional pre-formatted caption, e.g. "14 days left · ends 26 Nov". If null a default is derived from [time]. */
    val timeCaption: String? = null,
)

/** Shared goal card (Home + Goals list): sessions done/needed is the primary bar, time left is small text + thin bar. */
@Composable
fun GoalProgressCard(model: GoalCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val overdue = model.time.daysLeft < 0 && model.state == GoalState.ACTIVE
    val done = model.plans.sumOf { it.done }
    val needed = model.plans.sumOf { it.needed ?: 0 }.takeIf { model.plans.any { p -> p.needed != null } }
    LhCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(model.name, style = LhType.heading, color = c.foreground, modifier = Modifier.weight(1f), maxLines = 1)
            if (model.state != GoalState.ACTIVE) {
                Spacer(Modifier.width(8.dp))
                LhBadge(
                    if (model.state == GoalState.ACHIEVED) "Achieved" else "Archived",
                    variant = if (model.state == GoalState.ACHIEVED) LhBadgeVariant.Success else LhBadgeVariant.Secondary,
                )
            } else {
                Spacer(Modifier.width(8.dp))
                when {
                    needed != null -> Text("${fmtInt(done)} / ${fmtInt(needed)}", style = LhType.tnum(LhType.heading), color = c.foreground)
                    model.plans.isEmpty() -> Text("No plan linked", style = LhType.caption, color = c.mutedForeground)
                    else -> Text("$done sessions", style = LhType.tnum(LhType.heading), color = c.foreground)
                }
            }
        }
        if (needed != null) {
            Spacer(Modifier.height(8.dp))
            LhProgressBar(if (needed <= 0) 1f else (done.toFloat() / needed).coerceAtMost(1f), color = c.success)
        }
        Spacer(Modifier.height(6.dp))
        val caption = model.timeCaption ?: when {
            overdue -> "Overdue by ${-model.time.daysLeft} days"
            else -> "${model.time.daysLeft} days left"
        }
        Text((if (needed != null) "sessions done · " else "") + caption, style = LhType.caption, color = if (overdue) c.warning else c.mutedForeground)
        Spacer(Modifier.height(6.dp))
        LhProgressBar(model.time.fraction, color = if (overdue) c.warning else c.mutedForeground, height = 3.dp)
        if (model.plans.size > 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                model.plans.joinToString("  ·  ") { p -> "${p.planName} ${p.done}" + (p.needed?.let { "/$it" } ?: "") },
                style = LhType.captionMuted, maxLines = 2,
            )
        }
    }
}
