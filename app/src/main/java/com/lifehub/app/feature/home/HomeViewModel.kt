package com.lifehub.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.core.time.todayFlow
import com.lifehub.app.core.ui.GoalCardModel
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.MacroDay
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.model.WorkoutSession
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.data.repo.BodyRepository
import com.lifehub.app.data.repo.FoodRepository
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.data.repo.SessionRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.DayNutrition
import com.lifehub.app.domain.loggingStreak
import com.lifehub.app.domain.planProgress
import com.lifehub.app.domain.timeProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Home = today only: weight, calories/macros, training (today + tomorrow), goals, food today. Trends live in feature/trends. */
data class HomeUiState(
    val today: LocalDate,
    val proteinTargetG: Int,
    val goals: List<GoalCardModel>,
    val workoutInProgress: Boolean,
    val todayMacro: MacroDay? = null,
    val todayPlans: List<TodayPlan> = emptyList(),
    /** Plans scheduled for tomorrow (compact reminder under today's training). */
    val tomorrowPlans: List<TodayPlan> = emptyList(),
    /** False when there are no active (non-archived) plans at all -> "create a plan" empty state. */
    val hasPlans: Boolean = true,
    val latestBody: BodyEntry? = null,
    val kcalTarget: Int? = null,
    val carbsTargetG: Int? = null,
    val fatTargetG: Int? = null,
    val todayFood: List<FoodEntry> = emptyList(),
    /** Consecutive days with food or quick-add macros logged (ONT streak chip); 0 hides the chip. */
    val streakDays: Int = 0,
)

data class TodayPlan(
    val id: String, val name: String, val done: Boolean,
    val dayType: DayType? = null, val exerciseCount: Int = 0,
)

/** Active plans scheduled on [date] (ISO weekday in Plan.weekdays), in plan order; done = a completed session that day. */
internal fun plansOn(date: LocalDate, plans: List<PlanWithExercises>, completed: List<WorkoutSession>): List<TodayPlan> =
    plans.filter { !it.plan.isArchived && date.dayOfWeek.value in it.plan.weekdays }.map { p ->
        TodayPlan(p.plan.id, p.plan.name, completed.any { it.planId == p.plan.id && it.date == date }, p.plan.dayType, p.exercises.size)
    }

private data class Extras(val today: DayNutrition?, val body: BodyEntry?, val streak: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    settings: SettingsRepository,
    goals: GoalRepository,
    plans: PlanRepository,
    sessions: SessionRepository,
    food: FoodRepository,
    body: BodyRepository,
    time: TimeSource,
) : ViewModel() {
    val state: StateFlow<HomeUiState?> = time.todayFlow().flatMapLatest { today ->
        val streak = food.observeNutrition(today.minusDays(730), today).map { n ->
            loggingStreak(n.filter { it.entries.isNotEmpty() || it.quickAdd != null }.map { it.date }.toSet(), today)
        }
        val extras = combine(food.observeNutrition(today, today), body.observeAll(), streak) { m, b, k ->
            Extras(m.firstOrNull(), b.maxByOrNull { it.date }, k)
        }
        val goalData = combine(goals.observeGoals(), plans.observePlans(), sessions.observeCompleted()) { g, p, s -> Triple(g, p, s) }
        combine(goalData, settings.settings, sessions.observeInProgress(), extras) { (g, p, allDone), st, ip, ex ->
            val planList: List<Plan> = p.map { it.plan }
            HomeUiState(
                today, st.proteinTargetG,
                g.filter { it.goal.state == GoalState.ACTIVE }.sortedBy { it.goal.deadline }.map {
                    GoalCardModel(it.goal.id, it.goal.name, it.goal.state, timeProgress(it.goal.startDate, it.goal.deadline, today), planProgress(it, planList, allDone, today))
                },
                ip != null,
                todayMacro = ex.today?.asMacroDay(),
                todayPlans = plansOn(today, p, allDone),
                tomorrowPlans = plansOn(today.plusDays(1), p, allDone),
                hasPlans = planList.any { !it.isArchived },
                latestBody = ex.body,
                kcalTarget = st.kcalTarget, carbsTargetG = st.carbsTargetG, fatTargetG = st.fatTargetG,
                todayFood = ex.today?.entries.orEmpty(),
                streakDays = ex.streak,
            ) as HomeUiState?
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(c.settings, c.goals, c.plans, c.sessions, c.food, c.body, c.time) }
        }
    }
}
