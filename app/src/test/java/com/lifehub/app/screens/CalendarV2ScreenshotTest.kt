package com.lifehub.app.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.*
import com.lifehub.app.domain.ComplianceCalc
import com.lifehub.app.feature.calendar.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2000dp-xxhdpi")
class CalendarV2ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)
    private val selected = today.minusDays(1)
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    private fun set(ex: String, name: String, order: Int, i: Int, type: TargetType, target: Int?, actual: Int?, st: SetStatus = SetStatus.DONE, w: Double? = null) =
        SessionSet("$ex$i", "s1", ex, name, order, i, type, target,
            if (type != TargetType.SECONDS) actual else null, if (type == TargetType.SECONDS) actual else null, w, st, now, 90)

    private val sets = listOf(
        set("dip", "Dip", 0, 0, TargetType.REPS, 8, 8), set("dip", "Dip", 0, 1, TargetType.REPS, 8, 8), set("dip", "Dip", 0, 2, TargetType.REPS, 8, 7),
        set("pp", "Pseudo-planche push-up", 1, 0, TargetType.REPS, 8, 8), set("pp", "Pseudo-planche push-up", 1, 1, TargetType.REPS, 8, 8),
        set("pk", "Pike push-up", 2, 0, TargetType.REPS, 8, 6), set("pk", "Pike push-up", 2, 1, TargetType.REPS, 8, null, SetStatus.SKIPPED),
        set("pu", "Push-up", 3, 0, TargetType.MAX, null, 22), set("pu", "Push-up", 3, 1, TargetType.MAX, null, 19),
        set("tp", "Triceps pushdown", 4, 0, TargetType.REPS, 12, 12, w = 20.0), set("tp", "Triceps pushdown", 4, 1, TargetType.REPS, 12, 12, w = 20.0),
    )

    private val card = WorkoutCard(
        "s1", "Push", DayType.PUSH, 3120, 8, ComplianceCalc.compute(sets),
        sets.groupBy { it.exerciseOrder }.values.map { ExerciseResult(it.first().exerciseName, it) },
    )
    private val card2 = WorkoutCard("s2", "Core", DayType.CORE, 1800, null, ComplianceCalc.compute(sets.take(2)), listOf(ExerciseResult("Dragon flag", sets.take(2))))

    private val state = CalendarState(
        today, selected,
        flags = mapOf(
            today.minusDays(9) to DayFlags(proteinMissed = true, kcalExceeded = false),
            today.minusDays(4) to DayFlags(proteinMissed = false, kcalExceeded = true),
            today.minusDays(2) to DayFlags(proteinMissed = true, kcalExceeded = true),
            selected to DayFlags(proteinMissed = true, kcalExceeded = false),
            today.minusDays(15) to DayFlags(proteinMissed = true, kcalExceeded = true), // Oct 28: faded, no markers
        ),
        missedTraining = setOf(today.minusDays(7), today.minusDays(10), today.minusDays(3), today.minusDays(13)), // + Oct 30: faded, unstyled
        trainedDays = setOf(today.minusDays(11), today.minusDays(9), today.minusDays(8), today.minusDays(5), today.minusDays(4), today.minusDays(2),
            selected, today.minusDays(16), today.minusDays(18)), // Oct 27/25: faded, no markers
        day = DaySummary(selected, listOf(card, card2), BodyEntry(selected, 76.9, 37.3, 12.5, now),
            com.lifehub.app.domain.mergeNutrition(listOf(QuickAddEntry("q1", selected, null, 184, 12, 25, 4, true, now.plusSeconds(5))), listOf(
                FoodEntry("1", selected, null, "Greek yogurt", "Fage", 200.0, 118.0, 20.0, 8.0, 6.0, now),
                FoodEntry("2", selected, null, "Chicken breast", null, 180.0, 297.0, 56.0, 0.0, 6.5, now.plusSeconds(1)),
            )).single()),
        kcalTarget = 2600, carbsTarget = 250, fatTarget = 80,
        goalsDue = mapOf(
            today.plusDays(14) to listOf(Goal("g1", "Muscle Up", null, today.minusDays(70), today.plusDays(14), true, GoalState.ACTIVE, null, now)),
        ),
        // Reached on a missed-workout day (star wins over the red circle).
        goalsReached = mapOf(
            today.minusDays(3) to listOf(Goal("g2", "L-sit 20 s", null, today.minusDays(56), today.minusDays(2), false, GoalState.ACHIEVED, today.minusDays(3), now)),
        ),
    )

    private fun shot(name: String, dark: Boolean) {
        rule.setContent { LhTheme(darkTheme = dark) { CalendarScreen(state, {}, {}, initiallyExpanded = setOf("s1")) } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun light() = shot("calendar-v2-light", false)
    @Test fun dark() = shot("calendar-v2-dark", true)

    /** Selected day = a goal due date (silver star inset in the selected fill), goal row in the detail; today also starred. */
    private fun goalDueShot(name: String, dark: Boolean) {
        val due = today.plusDays(14)
        val st = state.copy(
            selected = due, day = DaySummary(due, emptyList(), null, null),
            goalsDue = state.goalsDue + (today to listOf(Goal("g3", "Handstand 30 s", null, today.minusDays(40), today, false, GoalState.ACTIVE, null, now))),
        )
        rule.setContent { LhTheme(darkTheme = dark) { CalendarScreen(st, {}, {}) } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }
    @Test fun goalDue() = goalDueShot("calendar-v2-goal-due", false)
    @Test fun goalDueDark() = goalDueShot("calendar-v2-goal-due-dark", true)

    /** Selected day = the day a goal was reached: gold star (inset) + "Goal reached" row. */
    @Test fun goalReached() {
        val d = today.minusDays(3)
        rule.setContent { LhTheme { CalendarScreen(state.copy(selected = d, day = DaySummary(d, emptyList(), null, null)), {}, {}) } }
        rule.onRoot().captureRoboImage("build/screens/calendar-v2-goal-reached.png")
    }

    @Test fun onTrack() {
        rule.setContent { LhTheme { CalendarScreen(state.copy(flags = emptyMap(), missedTraining = emptySet(), day = null), {}, {}) } }
        rule.onRoot().captureRoboImage("build/screens/calendar-v2-on-track.png")
    }
}
