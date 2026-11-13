package com.lifehub.app.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhChip
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhListRow
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtClock
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.core.designsystem.fmtDuration
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.model.WorkoutSession
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.data.repo.SessionRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.sessionsDone
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanGoalChip(val goalId: String, val name: String, val done: Int, val needed: Int?)

data class PlanDetailState(
    val plan: PlanWithExercises, val goals: List<PlanGoalChip>, val recent: List<WorkoutSession>, val todayIso: Int,
)

class PlanDetailViewModel(
    private val plans: PlanRepository, goals: GoalRepository, sessions: SessionRepository,
    private val time: TimeSource, handle: SavedStateHandle,
) : ViewModel() {
    private val planId: String = checkNotNull(handle["planId"])

    val state: StateFlow<PlanDetailState?> = combine(
        plans.observePlan(planId), goals.observeGoals(), sessions.observeCompleted(),
    ) { p, gs, done ->
        p ?: return@combine null
        val today = time.today()
        val chips = gs.mapNotNull { g ->
            g.links.firstOrNull { it.planId == planId }?.let { PlanGoalChip(g.goal.id, g.goal.name, sessionsDone(g.goal, planId, done, today), it.sessionsNeeded) }
        }
        PlanDetailState(p, chips, done.filter { it.planId == planId }.take(5), today.dayOfWeek.value)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleArchive() = viewModelScope.launch { state.value?.let { plans.setArchived(planId, !it.plan.plan.isArchived) } }
    fun duplicate(onNew: (String) -> Unit) = viewModelScope.launch { onNew(plans.duplicate(planId, time.now())) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { plans.delete(planId); onDone() }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { PlanDetailViewModel(c.plans, c.goals, c.sessions, c.time, createSavedStateHandle()) }
        }
    }
}

@Composable
fun PlanDetailRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: PlanDetailViewModel = viewModel(factory = PlanDetailViewModel.factory(c))
    val st by vm.state.collectAsState()
    PlanDetailScreen(
        st, onBack,
        onEdit = { st?.let { onNavigate(Routes.planEdit(it.plan.plan.id)) } },
        onDuplicate = { vm.duplicate { onNavigate(Routes.replace(Routes.plan(it))) } },
        onToggleArchive = vm::toggleArchive,
        onDelete = { vm.delete(onBack) },
        onStart = { st?.let { onNavigate(Routes.workoutStart(it.plan.plan.id)) } },
        onGoal = { onNavigate(Routes.goal(it)) },
        onSession = { onNavigate(Routes.session(it)) },
    )
}

@Composable
fun PlanDetailScreen(
    st: PlanDetailState?, onBack: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onToggleArchive: () -> Unit,
    onDelete: () -> Unit, onStart: () -> Unit, onGoal: (String) -> Unit, onSession: (String) -> Unit,
) {
    val c = LhTheme.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (st == null) { LhTopBar("Plan", onBack = onBack); return@Column }
        val p = st.plan.plan
        LhTopBar(p.name, onBack = onBack, titleIcon = p.dayType, actions = {
            com.lifehub.app.core.designsystem.components.LhIconAction(Icons.Outlined.Edit, "Edit plan", onEdit)
            OverflowMenu(listOf("Duplicate" to onDuplicate, (if (p.isArchived) "Unarchive" else "Archive") to onToggleArchive, "Delete" to { confirmDelete = true }))
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = c.mutedForeground)
                Spacer(Modifier.width(8.dp))
                Text(fmtWeekdays(p.weekdays), style = LhType.label, color = c.foreground)
                Text("  ·  ${st.plan.exercises.size} exercises", style = LhType.captionMuted)
            }
            if (st.goals.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Goals", style = LhType.captionMuted)
                Spacer(Modifier.height(4.dp))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    st.goals.forEach { g ->
                        LhChip(g.name + (g.needed?.let { " (${g.done}/$it)" } ?: " (${g.done})"), false, { onGoal(g.goalId) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LhButton("Start this plan", onStart, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.PlayArrow, enabled = !p.isArchived)
            LhSectionHeader("Exercises")
            com.lifehub.app.core.designsystem.components.LhGroupCard {
                st.plan.exercises.forEachIndexed { i, it ->
                    com.lifehub.app.core.designsystem.components.LhListItem(
                        it.exercise.name, standalone = false,
                        subtitle = planExerciseSummary(it.planExercise) + " · rest ${fmtClock(it.planExercise.restSeconds)}",
                        showDivider = i < st.plan.exercises.lastIndex,
                        leading = { com.lifehub.app.core.designsystem.ExerciseIcon(it.exercise, 40.dp, dayType = p.dayType) },
                    )
                }
            }
            p.notes?.let { Spacer(Modifier.height(8.dp)); Text(it, style = LhType.body, color = c.mutedForeground) }
            if (st.recent.isNotEmpty()) {
                LhSectionHeader("Recent sessions")
                com.lifehub.app.core.designsystem.components.LhCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    st.recent.forEachIndexed { i, s ->
                        LhListRow(
                            fmtDate(s.date), subtitle = fmtDuration(s.durationSec), onClick = { onSession(s.id) }, showDivider = i < st.recent.lastIndex,
                            trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.mutedForeground) },
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) LhConfirmDialog("Delete plan?", "Logged sessions are kept.", "Delete", { confirmDelete = false; onDelete() }, { confirmDelete = false })
}
