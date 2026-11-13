package com.lifehub.app.feature.achievements

import com.lifehub.app.core.designsystem.tint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.lifehub.app.core.designsystem.LhRadius
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.core.designsystem.fmtDateFull
import com.lifehub.app.data.model.Achievement
import com.lifehub.app.data.model.AchievementKind
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.AchievementEngine
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class AchievementsUi(
    val items: List<Achievement> = emptyList(),
    val goalIds: Set<String> = emptySet(),
    val sessionIds: Set<String> = emptySet(),
)

class AchievementsViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<AchievementsUi?> = combine(
        c.achievements.observeAll(), c.goals.observeGoals(), c.sessions.observeCompleted(),
    ) { a, g, s -> AchievementsUi(a, g.map { it.goal.id }.toSet(), s.map { it.id }.toSet()) as AchievementsUi? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete(id: String) { viewModelScope.launch { c.achievements.delete(id) } }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { AchievementsViewModel(c) } }
    }
}

@Composable
fun AchievementsRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: AchievementsViewModel = viewModel(factory = AchievementsViewModel.factory(c))
    val ui by vm.ui.collectAsState()
    AchievementsScreen(
        ui ?: return, today = c.time.today(), onBack = onBack, onDelete = vm::delete,
        onOpenGoal = { onNavigate(Routes.goal(it)) }, onOpenSession = { onNavigate(Routes.session(it)) },
    )
}

enum class AchievementFilter(val label: String) {
    ALL("All"), GOALS("Goals"), PRS("PRs");

    fun accepts(a: Achievement) = when (this) {
        ALL -> !a.kind.isConsistency; GOALS -> a.kind.isBig; PRS -> a.kind.isPr
    }
}

fun AchievementKind.icon(): ImageVector = when (this) {
    AchievementKind.GOAL_ACHIEVED -> Icons.Outlined.EmojiEvents
    AchievementKind.PR_REPS -> Icons.Outlined.TrendingUp
    AchievementKind.PR_HOLD -> Icons.Outlined.Timer
    AchievementKind.PR_WEIGHT -> Icons.Outlined.FitnessCenter
    AchievementKind.PERFECT_WEEK_TRAINING -> Icons.Outlined.EventAvailable
    AchievementKind.PERFECT_MONTH_TRAINING -> Icons.Outlined.CalendarMonth
    AchievementKind.PROTEIN_WEEK -> Icons.Outlined.Restaurant
    AchievementKind.SESSION_MILESTONE -> Icons.Outlined.MilitaryTech
}

fun AchievementKind.label(): String = when (this) {
    AchievementKind.GOAL_ACHIEVED -> "Goal achieved"
    AchievementKind.PR_REPS -> "Reps record"
    AchievementKind.PR_HOLD -> "Hold record"
    AchievementKind.PR_WEIGHT -> "Weight record"
    AchievementKind.PERFECT_WEEK_TRAINING -> "Perfect week"
    AchievementKind.PERFECT_MONTH_TRAINING -> "Perfect month"
    AchievementKind.PROTEIN_WEEK -> "Protein week"
    AchievementKind.SESSION_MILESTONE -> "Milestone"
}

/** Compact one-line row for small achievements (also used by the Home card). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementRow(
    a: Achievement, today: LocalDate, modifier: Modifier = Modifier, showDivider: Boolean = true,
    onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
) {
    val c = LhTheme.colors
    LhListItem(
        a.title, modifier, standalone = false, showDivider = showDivider, onClick = onClick, onLongClick = onLongClick,
        subtitle = a.kind.label() + " · " + fmtDate(a.achievedOn, today.year),
        leading = { LhIconSquare(a.kind.icon(), tint = c.warning, size = 44.dp) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AchievementsScreen(
    ui: AchievementsUi, today: LocalDate, onBack: () -> Unit = {}, onDelete: (String) -> Unit = {},
    onOpenGoal: (String) -> Unit = {}, onOpenSession: (String) -> Unit = {},
) {
    val c = LhTheme.colors
    val haptic = LocalHapticFeedback.current
    var filter by rememberSaveable { mutableStateOf(AchievementFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(ui.items, filter) { ui.items.filter { filter.accepts(it) } }
    val groups = remember(shown) { shown.groupBy { YearMonth.from(it.achievedOn) }.toList().sortedByDescending { it.first } }

    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar("Achievements", subtitle = if (ui.items.isEmpty()) null else "${ui.items.size} unlocked", onBack = onBack, icon = Icons.Outlined.EmojiEvents)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AchievementFilter.entries.forEach { f -> LhChip(f.label, f == filter, { filter = f }) }
        }
        if (shown.isEmpty()) {
            LhEmptyState(
                if (ui.items.isEmpty()) "No achievements yet" else "Nothing here yet",
                Modifier.fillMaxSize().padding(24.dp),
                description = if (ui.items.isEmpty()) "Records, perfect weeks and achieved goals will show up here." else "Try another filter.",
                icon = Icons.Outlined.EmojiEvents,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
                groups.forEach { (month, list) ->
                    item(key = "h-$month") {
                        val name = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (month.year != today.year) " ${month.year}" else ""
                        LhSectionHeader(name)
                    }
                    item(key = "g-$month") {
                        LhGroupCard {
                            list.forEachIndexed { i, a ->
                                AchievementRow(
                                    a, today, showDivider = i < list.lastIndex, onClick = { selectedId = a.id },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); deleteId = a.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    ui.items.firstOrNull { it.id == selectedId }?.let { a ->
        LhBottomSheet(onDismiss = { selectedId = null }, title = a.kind.label()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).background(c.tint(c.warning), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(a.kind.icon(), null, Modifier.size(36.dp), tint = c.warning)
                }
                Text(a.title, style = LhType.heading, color = c.foreground, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                Text(fmtDateFull(a.achievedOn), style = LhType.captionMuted)
                if (a.value != null) {
                    Text(AchievementEngine.num(a.value) + (a.unit?.let { " $it" } ?: ""), style = LhType.display, color = c.foreground, modifier = Modifier.padding(top = 8.dp))
                }
                if (a.detail != null) Text(a.detail, style = LhType.body, color = c.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            val goalId = a.goalId?.takeIf { it in ui.goalIds }
            val sessionId = a.sessionId?.takeIf { it in ui.sessionIds }
            if (goalId != null) LhButton("View goal", { selectedId = null; onOpenGoal(goalId) }, Modifier.fillMaxWidth(), variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.Flag)
            if (sessionId != null) LhButton("View session", { selectedId = null; onOpenSession(sessionId) }, Modifier.fillMaxWidth(), variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.FitnessCenter)
            Spacer(Modifier.height(8.dp))
            LhButton("Delete", { deleteId = a.id; selectedId = null }, Modifier.fillMaxWidth(), variant = LhButtonVariant.Destructive, leadingIcon = Icons.Outlined.Delete)
            Spacer(Modifier.height(16.dp))
        }
    }
    ui.items.firstOrNull { it.id == deleteId }?.let { a ->
        LhConfirmDialog(
            "Delete achievement?", "\"${a.title}\" will be removed from your log and won't come back automatically.",
            "Delete", { onDelete(a.id); deleteId = null }, { deleteId = null },
        )
    }
}
