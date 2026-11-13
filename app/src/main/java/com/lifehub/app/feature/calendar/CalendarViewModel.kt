package com.lifehub.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.core.time.todayFlow
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.MacroDay
import kotlinx.coroutines.launch
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SessionWithSets
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.Compliance
import com.lifehub.app.domain.ComplianceCalc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** One exercise of a performed session: its sets with target vs actual. */
data class ExerciseResult(val name: String, val sets: List<SessionSet>)

/** A performed workout of the selected day (collapsible card). */
data class WorkoutCard(
    val sessionId: String,
    val planName: String,
    val dayType: DayType?,
    val durationSec: Int?,
    val rpe: Int?,
    val compliance: Compliance?,
    val exercises: List<ExerciseResult>,
)

data class DaySummary(
    val date: LocalDate,
    val workouts: List<WorkoutCard>,
    val macro: MacroDay?,
    val body: BodyEntry?,
    /** Food entries + quick add of the day (totals = both). */
    val nutrition: com.lifehub.app.domain.DayNutrition? = null,
)

data class CalendarState(
    val today: LocalDate,
    val selected: LocalDate = today,
    /** Legacy markers, no longer drawn by the grid (kept so older callers/tests still compile). */
    val workoutDays: Map<LocalDate, List<DayType?>> = emptyMap(),
    val macroDays: Set<LocalDate> = emptySet(),
    val weightDays: Set<LocalDate> = emptySet(),
    val day: DaySummary? = null,
    val proteinTarget: Int = 150,
    val kcalTarget: Int? = null,
    val carbsTarget: Int? = null,
    val fatTarget: Int? = null,
    /** Finished days that missed the protein target / were cheat days (kcal well over target); only these are marked. */
    val flags: Map<LocalDate, DayFlags> = emptyMap(),
    /** Plan-scheduled days before today without a completed session (counted in the month header). */
    val missedTraining: Set<LocalDate> = emptySet(),
    /** Days with at least one completed session (dumbbell marker). */
    val trainedDays: Set<LocalDate> = emptySet(),
    /** Active goals by a deadline today or later (silver star on the day, "Goal due" row in the day detail). */
    val goalsDue: Map<LocalDate, List<com.lifehub.app.data.model.Goal>> = emptyMap(),
    /** Achieved goals by achievedAt (gold star on the day, "Goal reached" row in the day detail). */
    val goalsReached: Map<LocalDate, List<com.lifehub.app.data.model.Goal>> = emptyMap(),
)

fun buildWorkoutCard(sw: SessionWithSets, plan: PlanWithExercises?): WorkoutCard {
    val planned = plan?.exercises?.associate { it.exercise.id to it.planExercise.sets }
    val exercises = sw.sets.groupBy { it.exerciseOrder }.toSortedMap().values.map { ExerciseResult(it.first().exerciseName, it.sortedBy { s -> s.setIndex }) }
    return WorkoutCard(
        sw.session.id, sw.session.planName, plan?.plan?.dayType, sw.session.durationSec, sw.session.rpe,
        ComplianceCalc.compute(sw.sets, planned), exercises,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val c: AppContainer) : ViewModel() {
    private val selected = MutableStateFlow<LocalDate?>(null)

    private val daySummary = combine(selected, c.time.todayFlow()) { s, t -> s ?: t }.flatMapLatest { d ->
        combine(
            c.sessions.observeCompletedWithSets(d, d), c.food.observeNutrition(d, d), c.body.observeBetween(d, d), c.plans.observePlans(),
        ) { s, m, b, plans ->
            val byId = plans.associateBy { it.plan.id }
            DaySummary(d, s.map { buildWorkoutCard(it, byId[it.session.planId]) }, m.firstOrNull()?.quickAdd, b.firstOrNull(), m.firstOrNull())
        }
    }

    private val days = combine(
        c.sessions.observeCompleted(), c.food.observeNutrition(MIN, MAX), c.plans.observePlans(), c.time.todayFlow(), c.settings.settings,
    ) { s, m, plans, today, st ->
        val trained = s.map { it.date }.toSet()
        Triple(
            DiaryFlags.dayFlags(m, today, st.proteinTargetG, st.kcalTarget),
            DiaryFlags.missedTrainingDays(plans.map { it.plan }, trained, today, c.time.zone),
            trained,
        )
    }

    private val goalDays = combine(c.goals.observeGoals(), c.time.todayFlow()) { g, today ->
        val goals = g.map { it.goal }
        DiaryFlags.goalsDue(goals, today) to DiaryFlags.goalsReached(goals)
    }

    val state: StateFlow<CalendarState> = combine(days, daySummary, selected, c.time.todayFlow(), c.settings.settings) { d, day, sel, today, st ->
        CalendarState(
            today, sel ?: today, day = day, proteinTarget = st.proteinTargetG, kcalTarget = st.kcalTarget, carbsTarget = st.carbsTargetG,
            fatTarget = st.fatTargetG, flags = d.first, missedTraining = d.second, trainedDays = d.third,
        )
    }.combine(goalDays) { s, g -> s.copy(goalsDue = g.first, goalsReached = g.second) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarState(c.time.today()))

    fun select(d: LocalDate) { selected.value = d }

    /** Swipe-delete of a food entry with Undo. */
    fun deleteFood(e: com.lifehub.app.data.model.FoodEntry) {
        viewModelScope.launch {
            c.food.deleteEntry(e.id)
            com.lifehub.app.core.designsystem.SnackbarBus.show("Removed ${e.name}", "Undo") { viewModelScope.launch { c.food.restoreEntry(e) } }
        }
    }

    /** ⋮ Copy yesterday: duplicates the previous day's food entries (+ quick add if this day has none), with Undo. */
    fun copyYesterday(date: LocalDate) {
        viewModelScope.launch {
            val now = c.time.now()
            val src = c.food.observeEntries(date.minusDays(1), date.minusDays(1)).first()
            val srcMacro = c.macros.get(date.minusDays(1))
            val hadMacro = c.macros.get(date) != null
            if (src.isEmpty() && srcMacro == null) { com.lifehub.app.core.designsystem.SnackbarBus.show("Nothing logged yesterday"); return@launch }
            val copies = src.map { it.copy(id = java.util.UUID.randomUUID().toString(), date = date, createdAt = now) }
            copies.forEach { c.food.restoreEntry(it) }
            val copiedMacro = !hadMacro && srcMacro != null
            if (copiedMacro) c.macros.upsert(date, srcMacro!!.proteinG, srcMacro.carbsG, srcMacro.fatG, if (srcMacro.kcalIsAuto) null else srcMacro.kcal, now)
            val n = copies.size + if (copiedMacro) 1 else 0
            com.lifehub.app.core.designsystem.SnackbarBus.show("Copied $n item${if (n == 1) "" else "s"} from yesterday", "Undo") {
                viewModelScope.launch {
                    copies.forEach { c.food.deleteEntry(it.id) }
                    if (copiedMacro) c.macros.delete(date)
                }
            }
        }
    }

    /** ⋮ Clear day: removes all food entries and the quick-add record of [date], with Undo. */
    fun clearDay(date: LocalDate) {
        viewModelScope.launch {
            val entries = c.food.observeEntries(date, date).first()
            val macro = c.macros.get(date)
            entries.forEach { c.food.deleteEntry(it.id) }
            if (macro != null) c.macros.delete(date)
            com.lifehub.app.core.designsystem.SnackbarBus.show("Day cleared", "Undo") {
                viewModelScope.launch {
                    entries.forEach { c.food.restoreEntry(it) }
                    if (macro != null) c.macros.upsert(date, macro.proteinG, macro.carbsG, macro.fatG, if (macro.kcalIsAuto) null else macro.kcal, macro.updatedAt)
                }
            }
        }
    }

    companion object {
        private val MIN = LocalDate.of(1970, 1, 1)
        private val MAX = LocalDate.of(2200, 1, 1)
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { CalendarViewModel(c) } }
    }
}
