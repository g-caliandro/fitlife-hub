package com.lifehub.app.feature.goals

import androidx.compose.foundation.layout.Box
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.time.todayFlow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhBottomSheet
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhListRow
import com.lifehub.app.core.designsystem.components.LhNumberField
import com.lifehub.app.core.designsystem.components.LhProgressBar
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.Goal
import com.lifehub.app.data.model.GoalPlanLink
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.data.repo.SessionRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.TimeProgress
import com.lifehub.app.domain.sessionsDone
import com.lifehub.app.domain.timeProgress
import com.lifehub.app.feature.plans.NameValueRow
import androidx.compose.foundation.layout.width
import com.lifehub.app.feature.plans.OverflowMenu
import com.lifehub.app.feature.plans.planExerciseSummary
import com.lifehub.app.navigation.LocalSnackbarHostState
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalPlanItem(val plan: PlanWithExercises, val done: Int, val needed: Int?)

data class GoalDetailState(
    val goal: Goal,
    val time: TimeProgress,
    val items: List<GoalPlanItem>,
    val linkable: List<Plan>,
    val todayIso: Int,
)

class GoalDetailViewModel(
    private val goals: GoalRepository, plans: PlanRepository, sessions: SessionRepository,
    private val time: TimeSource, handle: SavedStateHandle,
) : ViewModel() {
    private val goalId: String = checkNotNull(handle["goalId"])

    val state: StateFlow<GoalDetailState?> = combine(
        goals.observeGoal(goalId), plans.observePlans(), sessions.observeCompleted(), time.todayFlow(),
    ) { gl, ps, done, today ->
        gl ?: return@combine null
        val byId = ps.associateBy { it.plan.id }
        val items = gl.links.mapNotNull { l ->
            byId[l.planId]?.let { GoalPlanItem(it, sessionsDone(gl.goal, it.plan.id, done, today), l.sessionsNeeded) }
        }.sortedBy { it.plan.plan.name.lowercase() }
        val linked = gl.links.map { it.planId }.toSet()
        GoalDetailState(
            gl.goal, timeProgress(gl.goal.startDate, gl.goal.deadline, today), items,
            ps.map { it.plan }.filter { !it.isArchived && it.id !in linked }, today.dayOfWeek.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setState(s: GoalState) = viewModelScope.launch { goals.setState(goalId, s, time.today()) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { goals.delete(goalId); onDone() }
    fun link(planId: String, needed: Int?) = viewModelScope.launch { goals.upsertLink(GoalPlanLink(goalId, planId, needed)) }
    fun unlink(planId: String) = viewModelScope.launch { goals.removeLink(goalId, planId) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { GoalDetailViewModel(c.goals, c.plans, c.sessions, c.time, createSavedStateHandle()) }
        }
    }
}

@Composable
fun GoalDetailRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: GoalDetailViewModel = viewModel(factory = GoalDetailViewModel.factory(c))
    val st by vm.state.collectAsState()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    GoalDetailScreen(
        st, onBack,
        onEdit = { st?.let { onNavigate(Routes.goalEdit(it.goal.id)) } },
        onSetState = vm::setState,
        onDelete = { vm.delete(onBack) },
        onOpenPlan = { onNavigate(Routes.plan(it)) },
        onCreatePlan = { st?.let { onNavigate(Routes.planEdit(goalId = it.goal.id)) } },
        onLink = vm::link,
        onUnlink = { item ->
            vm.unlink(item.plan.plan.id)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar("Plan unlinked", "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.link(item.plan.plan.id, item.needed)
            }
        },
    )
}

@Composable
fun GoalDetailScreen(
    st: GoalDetailState?, onBack: () -> Unit, onEdit: () -> Unit, onSetState: (GoalState) -> Unit, onDelete: () -> Unit,
    onOpenPlan: (String) -> Unit, onCreatePlan: () -> Unit, onLink: (String, Int?) -> Unit, onUnlink: (GoalPlanItem) -> Unit,
) {
    val c = LhTheme.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var linkSheet by rememberSaveable { mutableStateOf(false) }
    var editNeededFor by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (st == null) {
            LhTopBar("Goal", onBack = onBack)
            return@Column
        }
        val g = st.goal
        LhTopBar(g.name, onBack = onBack, actions = {
            com.lifehub.app.core.designsystem.components.LhIconAction(androidx.compose.material.icons.Icons.Outlined.Edit, "Edit goal", onEdit)
            OverflowMenu(
                buildList {
                    if (g.state == GoalState.ACTIVE) add("Mark achieved" to { onSetState(GoalState.ACHIEVED) })
                    else add("Reactivate" to { onSetState(GoalState.ACTIVE) })
                    if (g.state != GoalState.ARCHIVED) add("Archive" to { onSetState(GoalState.ARCHIVED) })
                    add("Delete" to { confirmDelete = true })
                },
            )
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            val overdue = st.time.daysLeft < 0 && g.state == GoalState.ACTIVE
            val totalDone = st.items.sumOf { it.done }
            val totalNeeded = st.items.mapNotNull { it.needed }.sum()
            val hasNeeded = st.items.any { it.needed != null } && totalNeeded > 0
            val progress = if (hasNeeded) (totalDone.toFloat() / totalNeeded).coerceIn(0f, 1f) else st.time.fraction
            LhCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LhBadge(
                        g.state.name,
                        variant = when (g.state) { GoalState.ACTIVE -> LhBadgeVariant.Accent; GoalState.ACHIEVED -> LhBadgeVariant.Success; GoalState.ARCHIVED -> LhBadgeVariant.Secondary },
                    )
                    Text("${fmtDate(g.startDate)} → ${fmtDate(g.deadline)}", style = LhType.captionMuted)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    LhRing(progress, "${(progress * 100).toInt()}%", if (hasNeeded) "complete" else "of time", size = 140.dp, stroke = 14.dp)
                }
                Text(
                    "Day ${st.time.elapsedDays} of ${st.time.totalDays} · " + goalCaption(st.time, g.deadline, g.state).substringBefore(" · "),
                    style = LhType.caption, color = if (overdue) c.warning else c.mutedForeground,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                g.notes?.let { Spacer(Modifier.height(12.dp)); Text(it, style = LhType.body, color = c.mutedForeground) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhStatTile("Sessions", if (hasNeeded) "$totalDone / $totalNeeded" else "$totalDone", Modifier.weight(1f))
                LhStatTile(
                    if (st.time.daysLeft < 0) "Overdue" else "Days left", "${kotlin.math.abs(st.time.daysLeft)}", Modifier.weight(1f), unit = "d",
                )
            }
            LhSectionHeader("Plans", action = {
                LhButton("Link plan", { linkSheet = true }, variant = LhButtonVariant.Tonal, size = com.lifehub.app.core.designsystem.components.LhButtonSize.Sm,
                    leadingIcon = androidx.compose.material.icons.Icons.Outlined.Add)
            })
            if (st.items.isEmpty()) {
                LhEmptyState("No plans linked", description = null, icon = androidx.compose.material.icons.Icons.Outlined.FitnessCenter, actionText = "Create plan", actionIcon = androidx.compose.material.icons.Icons.Outlined.Add, onAction = onCreatePlan, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                st.items.forEach { item ->
                    val dt = item.plan.plan.dayType
                    LhListItem(
                        item.plan.plan.name,
                        subtitle = (item.needed?.let { "${item.done} / $it sessions" } ?: "${item.done} sessions done") +
                            " · " + com.lifehub.app.feature.plans.fmtWeekdays(item.plan.plan.weekdays),
                        leading = if (dt != null) ({ LhIconSquare(dt) }) else null,
                        icon = if (dt == null) androidx.compose.material.icons.Icons.Outlined.FitnessCenter else null,
                        onClick = { onOpenPlan(item.plan.plan.id) },
                        trailing = {
                            OverflowMenu(
                                listOf(
                                    "Edit sessions needed" to { editNeededFor = item.plan.plan.id },
                                    "Unlink" to { onUnlink(item) },
                                    "Open plan" to { onOpenPlan(item.plan.plan.id) },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        LhConfirmDialog("Delete goal?", "Linked plans are kept.", "Delete", { confirmDelete = false; onDelete() }, { confirmDelete = false })
    }
    if (st != null && (linkSheet || editNeededFor != null)) {
        val fixed = editNeededFor?.let { id -> st.items.firstOrNull { it.plan.plan.id == id } }
        LinkSheet(
            candidates = if (fixed != null) listOf(fixed.plan.plan) else st.linkable,
            fixedPlan = fixed?.plan?.plan, initialNeeded = fixed?.needed,
            onDismiss = { linkSheet = false; editNeededFor = null },
            onConfirm = { id, n -> onLink(id, n); linkSheet = false; editNeededFor = null },
        )
    }
}

@Composable
private fun LinkSheet(candidates: List<Plan>, fixedPlan: Plan?, initialNeeded: Int?, onDismiss: () -> Unit, onConfirm: (String, Int?) -> Unit) {
    var chosen by remember { mutableStateOf(fixedPlan) }
    var needed by remember { mutableStateOf(initialNeeded?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    LhBottomSheet(onDismiss, title = if (fixedPlan != null) "Sessions needed" else "Link plan") {
        if (chosen == null) {
            if (candidates.isEmpty()) LhEmptyState("No plans to link", Modifier.fillMaxWidth().padding(24.dp), description = "Create a plan first.", icon = androidx.compose.material.icons.Icons.Outlined.FitnessCenter)
            candidates.forEach { p -> LhListRow(p.name, onClick = { chosen = p }) }
        } else {
            Text(chosen!!.name, style = LhType.heading, color = LhTheme.colors.foreground)
            Spacer(Modifier.height(12.dp))
            LhNumberField(needed, { needed = it; error = null }, Modifier.fillMaxWidth(), label = "Sessions needed (optional)", error = error)
            Spacer(Modifier.height(16.dp))
            LhButton("Link".takeIf { fixedPlan == null } ?: "Save", {
                val n = needed.toIntOrNull()
                if (needed.isNotBlank() && (n == null || n !in 1..500)) error = "1–500"
                else onConfirm(chosen!!.id, n)
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}
