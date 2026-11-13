package com.lifehub.app.feature.goals

import com.lifehub.app.core.time.todayFlow
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import androidx.lifecycle.viewModelScope
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhSegmentedControl
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.core.ui.GoalProgressCard
import com.lifehub.app.core.ui.GoalCardModel
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.GoalWithLinks
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.data.repo.SessionRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.TimeProgress
import com.lifehub.app.domain.planProgress
import com.lifehub.app.domain.timeProgress
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** "14 days left · ends 26 Nov" / "Overdue by 3 days · ended 26 Nov" (achieved/archived: "Ended 26 Nov"). */
fun goalCaption(time: TimeProgress, deadline: LocalDate, state: GoalState): String = when {
    state != GoalState.ACTIVE -> "Deadline ${fmtDate(deadline)}"
    time.daysLeft < 0 -> "Overdue by ${-time.daysLeft} days"
    time.daysLeft == 1 -> "1 day left · ends ${fmtDate(deadline)}"
    else -> "${time.daysLeft} days left · ends ${fmtDate(deadline)}"
}

fun GoalWithLinks.toCardModel(plans: List<com.lifehub.app.data.model.Plan>, completed: List<com.lifehub.app.data.model.WorkoutSession>, today: LocalDate): GoalCardModel {
    val t = timeProgress(goal.startDate, goal.deadline, today)
    return GoalCardModel(goal.id, goal.name, goal.state, t, planProgress(this, plans, completed, today), goalCaption(t, goal.deadline, goal.state))
}

class GoalsViewModel(goals: GoalRepository, plans: PlanRepository, sessions: SessionRepository, time: TimeSource) : ViewModel() {
    val cards: StateFlow<List<GoalCardModel>> = combine(
        goals.observeGoals(), plans.observePlans(), sessions.observeCompleted(), time.todayFlow(),
    ) { gs, ps, done, today ->
        val planList = ps.map { it.plan }
        gs.sortedWith(compareBy({ it.goal.deadline }, { it.goal.name.lowercase() })).map { it.toCardModel(planList, done, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { GoalsViewModel(c.goals, c.plans, c.sessions, c.time) } }
    }
}

@Composable
fun GoalsRoute(onNavigate: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val c = LocalContext.current.container
    val vm: GoalsViewModel = viewModel(factory = GoalsViewModel.factory(c))
    val cards by vm.cards.collectAsState()
    GoalsScreen(cards, onOpen = { onNavigate(Routes.goal(it)) }, onAdd = { onNavigate(Routes.goalEdit()) },
        onAchievements = { onNavigate(Routes.ACHIEVEMENTS) }, onBack = onBack)
}

@Composable
fun GoalsScreen(cards: List<GoalCardModel>, onOpen: (String) -> Unit, onAdd: () -> Unit, onAchievements: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    var tab by rememberSaveable { mutableStateOf(GoalState.ACTIVE) }
    val shown = cards.filter { it.state == tab }
    Column(Modifier.fillMaxSize()) {
        LhTopBar("Goals", onBack = onBack, icon = Icons.Outlined.Flag, actions = {
            LhIconButton(Icons.Outlined.EmojiEvents, "Achievements", onAchievements)
            LhIconButton(Icons.Outlined.Add, "Add goal", onAdd)
        })
        LhSegmentedControl(
            GoalState.entries.toList(), tab, { tab = it },
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            label = { it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } },
        )
        if (shown.isEmpty()) {
            val (title, desc) = when (tab) {
                GoalState.ACTIVE -> "No active goals" to "Set a time window for a skill you're chasing."
                GoalState.ACHIEVED -> "No achieved goals yet" to null
                GoalState.ARCHIVED -> "No archived goals" to null
            }
            LhEmptyState(title, Modifier.fillMaxSize().padding(24.dp), description = desc, icon = Icons.Outlined.Flag,
                actionIcon = Icons.Outlined.Add, actionText = if (tab == GoalState.ACTIVE) "Add goal" else null, onAction = if (tab == GoalState.ACTIVE) onAdd else null)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.id }) { GoalProgressCard(it, onClick = { onOpen(it.id) }) }
            }
        }
    }
}
