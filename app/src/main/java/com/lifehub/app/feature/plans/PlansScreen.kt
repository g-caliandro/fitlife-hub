package com.lifehub.app.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhListItem
import com.lifehub.app.core.designsystem.components.LhIconSquare
import com.lifehub.app.core.designsystem.components.LhAddRow
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.FitnessCenter
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlanRowModel(
    val id: String, val name: String, val weekdays: Set<Int>, val exerciseCount: Int,
    val goalNames: List<String>, val isArchived: Boolean,
    val dayType: com.lifehub.app.data.model.DayType? = null,
    /** Estimated duration (see [estimatePlanMinutes]); 0 = unknown. */
    val estMinutes: Int = 0,
)

data class PlansUiState(val plans: List<PlanRowModel> = emptyList(), val todayIso: Int = 1)

class PlansViewModel(plans: PlanRepository, goals: GoalRepository, private val time: TimeSource) : ViewModel() {
    val state: StateFlow<PlansUiState> = combine(plans.observePlans(), goals.observeGoals()) { ps, gs ->
        val linkedNames = HashMap<String, MutableList<String>>()
        // Chips show active goals only (achieved/archived ones are history, not what the plan works towards).
        gs.filter { it.goal.state == com.lifehub.app.data.model.GoalState.ACTIVE }
            .forEach { g -> g.links.forEach { l -> linkedNames.getOrPut(l.planId) { mutableListOf() }.add(g.goal.name) } }
        PlansUiState(
            ps.map {
                PlanRowModel(
                    it.plan.id, it.plan.name, it.plan.weekdays, it.exercises.size, linkedNames[it.plan.id].orEmpty().sorted(), it.plan.isArchived,
                    it.plan.dayType, estimatePlanMinutes(it.exercises.map { e -> e.planExercise }),
                )
            },
            time.today().dayOfWeek.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlansUiState())

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { PlansViewModel(c.plans, c.goals, c.time) } }
    }
}

@Composable
fun PlansRoute(onNavigate: (String) -> Unit) {
    val c = LocalContext.current.container
    val vm: PlansViewModel = viewModel(factory = PlansViewModel.factory(c))
    val st by vm.state.collectAsState()
    PlansScreen(
        st, onOpen = { onNavigate(Routes.plan(it)) }, onAdd = { onNavigate(Routes.planEdit()) },
        onStart = { onNavigate(Routes.workoutStart()) }, onStartPlan = { onNavigate(Routes.workoutStart(it)) }, onExercises = { onNavigate(Routes.EXERCISES) },
    )
}

@Composable
fun PlansScreen(st: PlansUiState, onOpen: (String) -> Unit, onAdd: () -> Unit, onStart: () -> Unit, onExercises: () -> Unit, onStartPlan: (String) -> Unit = {}) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val active = st.plans.filter { !it.isArchived }
    val archived = st.plans.filter { it.isArchived }
    Column(Modifier.fillMaxSize()) {
        com.lifehub.app.core.designsystem.components.LhScreenHeader("Train", Icons.Filled.FitnessCenter,
            actions = { com.lifehub.app.core.designsystem.components.LhIconAction(Icons.Outlined.Add, "Add plan", onAdd) })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = com.lifehub.app.core.designsystem.components.LocalTabBottomPadding.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LhButton("Start workout", onStart, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.PlayArrow) }
            item { LhSectionHeader("Plans", topPadding = 12.dp) }
            if (active.isEmpty()) {
                item {
                    LhEmptyState("No plans yet", Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        description = "A plan is an ordered list of exercises you repeat.", icon = Icons.Outlined.FitnessCenter,
                        actionText = "Create plan", onAction = onAdd, actionIcon = Icons.Outlined.Add)
                }
            }
            items(active, key = { it.id }) { PlanRow(it, st.todayIso, onOpen, onStartPlan) }
            if (active.isNotEmpty()) item { LhAddRow(onAdd, label = "New plan") }
            if (archived.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LhButton("Archived (${archived.size})", { showArchived = !showArchived }, variant = LhButtonVariant.Ghost, size = LhButtonSize.Sm,
                            leadingIcon = if (showArchived) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore)
                    }
                }
                if (showArchived) items(archived, key = { it.id }) { PlanRow(it, st.todayIso, onOpen, onStartPlan) }
            }
            item { LhSectionHeader("Library", topPadding = 12.dp) }
            item {
                LhListItem("Exercises", icon = Icons.AutoMirrored.Outlined.ListAlt, onClick = onExercises,
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LhTheme.colors.mutedForeground) })
            }
        }
    }
}

/** Max goal chips on a plan card before collapsing the rest into "+N". */
private const val MAX_GOAL_CHIPS = 2

@Composable
private fun PlanRow(p: PlanRowModel, todayIso: Int, onOpen: (String) -> Unit, onStartPlan: (String) -> Unit) {
    val c = LhTheme.colors
    val sub = buildList {
        if (p.estMinutes > 0) add("~${p.estMinutes} min")
        add(if (!p.isArchived && todayIso in p.weekdays) "Today" else fmtWeekdays(p.weekdays))
    }.joinToString(" · ")
    LhCard(Modifier.fillMaxWidth().testTag("plan-row-${p.id}"), onClick = { onOpen(p.id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.dayType != null) LhIconSquare(p.dayType) else LhIconSquare(Icons.Outlined.FitnessCenter)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = LhType.bodyLarge, color = c.foreground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(sub, style = LhType.body, color = c.mutedForeground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (p.goalNames.isNotEmpty()) {
                    Row(Modifier.padding(top = 6.dp).testTag("plan-goals-${p.id}"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        p.goalNames.take(MAX_GOAL_CHIPS).forEach { GoalChip(it, Modifier.weight(1f, fill = false)) }
                        val more = p.goalNames.size - MAX_GOAL_CHIPS
                        if (more > 0) GoalChip("+$more")
                    }
                }
            }
            if (!p.isArchived) {
                Spacer(Modifier.width(12.dp))
                LhIconButton(Icons.Outlined.PlayArrow, "Start ${p.name}", { onStartPlan(p.id) }, Modifier.testTag("plan-start-${p.id}"), variant = LhButtonVariant.Tonal)
            }
        }
    }
}

/** Small muted pill for a linked goal (ellipsizes instead of growing the card). */
@Composable
private fun GoalChip(text: String, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    androidx.compose.material3.Surface(modifier, shape = com.lifehub.app.core.designsystem.LhRadius.fullShape, color = c.secondary, contentColor = c.secondaryForeground) {
        Text(
            text, style = LhType.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
