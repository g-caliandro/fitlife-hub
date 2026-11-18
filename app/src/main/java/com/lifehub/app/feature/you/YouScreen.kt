package com.lifehub.app.feature.you

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhGroupCard
import com.lifehub.app.core.designsystem.components.LhListRow
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtKg
import com.lifehub.app.core.designsystem.tint
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.navigation.Routes
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.lifehub.app.domain.fatPct

data class YouState(
    val weightKg: Double? = null, val bodyFatPct: Double? = null, val daysAgo: Long? = null,
    val activeGoals: Int = 0, val achievements: Int = 0, val remindersOn: Boolean = true, val targetsSummary: String = "", val version: String = "v0.3",
)

@Composable
fun YouRoute(onNavigate: (String) -> Unit, onBack: () -> Unit = {}) {
    val c = LocalContext.current.container
    val body by c.body.observeAll().collectAsState(initial = emptyList())
    val ach by c.achievements.observeAll().collectAsState(initial = emptyList())
    val goals by c.goals.observeGoals().collectAsState(initial = emptyList())
    val s by c.settings.settings.collectAsState()
    val latest = body.maxByOrNull { it.date }
    val today = c.time.today()
    val targets = buildList {
        add("Protein ${s.proteinTargetG} g")
        s.kcalTarget?.let { add("${String.format(Locale.US, "%,d", it)} kcal") }
    }.joinToString(" · ")
    YouScreen(
        YouState(latest?.weightKg, latest?.let(::fatPct), latest?.let { ChronoUnit.DAYS.between(it.date, today) },
            goals.count { it.goal.state == GoalState.ACTIVE }, ach.count { !it.kind.isConsistency },
            s.notifyTrain || s.notifyProtein || s.notifyWeight || s.notifyAchievements || s.notifyStreak, targets),
        onNavigate, today, onBack,
    )
}

/** Profile + settings hub, pushed from the avatar in Home's header (back -> Home). */
@Composable
fun YouScreen(state: YouState, onNavigate: (String) -> Unit, today: java.time.LocalDate = java.time.LocalDate.now(), onBack: () -> Unit = {}) {
    val c = LhTheme.colors
    Column(Modifier.fillMaxSize()) {
        LhTopBar("You", onBack = onBack, icon = Icons.Outlined.AccountCircle)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(160.dp).background(c.tint(c.primary), CircleShape).border(BorderStroke(2.dp, c.primary.copy(alpha = 0.5f)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.weightKg?.let { String.format(Locale.US, "%.1f", it) } ?: "—", style = LhType.display, color = c.foreground)
                    if (state.weightKg != null) Text("kg", style = LhType.body, color = c.mutedForeground)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.weightKg != null) {
                val sub = buildList {
                    state.bodyFatPct?.let { add(String.format(Locale.US, "%.1f %% body fat", it)) }
                    state.daysAgo?.let { add(when (it) { 0L -> "today"; 1L -> "yesterday"; else -> "$it days ago" }) }
                }.joinToString(" · ")
                Text(sub, style = LhType.heading, color = c.foreground, textAlign = TextAlign.Center)
            } else {
                Text("Log your weight", style = LhType.heading, color = c.foreground)
                Spacer(Modifier.height(8.dp))
                LhButton("Log weight", { onNavigate(Routes.bodyEdit(today)) }, variant = LhButtonVariant.Tonal)
            }
            Column(Modifier.fillMaxWidth()) {
                Group("Progress")
                LhGroupCard {
                    Row(Icons.Outlined.Flag, "Goals", if (state.activeGoals == 0) "No active goals" else "${state.activeGoals} active", { onNavigate(Routes.GOALS) }, true)
                    Row(Icons.Outlined.EmojiEvents, "Achievements", if (state.achievements == 0) "Your milestones" else "${state.achievements} unlocked", { onNavigate(Routes.ACHIEVEMENTS) }, false)
                }
                Group("Nutrition")
                LhGroupCard { Row(Icons.Outlined.TrackChanges, "Targets", state.targetsSummary, { onNavigate(Routes.TARGETS) }, false) }
                Group("Settings")
                LhGroupCard {
                    Row(Icons.Outlined.Notifications, "Notifications", if (state.remindersOn) "Reminders on" else "Reminders off", { onNavigate(Routes.SETTINGS_NOTIFICATIONS) }, true)
                    Row(Icons.Outlined.AutoAwesome, "Copilot", "AI provider and API keys", { onNavigate(Routes.SETTINGS_COPILOT) }, true)
                    Row(Icons.Outlined.ImportExport, "Data", "Export, import, mock", { onNavigate(Routes.SETTINGS_DATA) }, true)
                    Row(Icons.Outlined.Info, "About", state.version, { onNavigate(Routes.ABOUT) }, false)
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = LhType.body, color = LhTheme.colors.mutedForeground, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun Row(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, divider: Boolean) {
    LhListRow(
        title, subtitle = subtitle.ifBlank { null }, leadingIcon = icon, onClick = onClick, showDivider = divider,
        trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LhTheme.colors.mutedForeground) },
    )
}
