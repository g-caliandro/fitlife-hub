package com.lifehub.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.*
import com.lifehub.app.mock.MockDataGenerator
import com.lifehub.app.feature.body.*
import com.lifehub.app.feature.goals.*
import com.lifehub.app.feature.nutrition.*
import com.lifehub.app.feature.plans.*
import com.lifehub.app.feature.workout.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class FormsScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    private fun both(name: String, content: @Composable () -> Unit) {
        var dark by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { LhTheme(darkTheme = dark) { content() } }
        rule.onRoot().captureRoboImage("build/screens/form-$name.png")
        dark = true
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/form-$name-dark.png")
    }

    private fun ex(n: String) = MockDataGenerator.exercises.first { it.name == n }

    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    @Test fun macro() = both("macro") {
        MacroEditScreen(
            MacroEditState(today, today, protein = "150", carbs = "240", fat = "70", kcal = "",
                copySource = QuickAddEntry("y", today.minusDays(1), null, 300, 1, 1, 1, false, now)),
            {}, {}, {}, {}, {}, {}, {}, {}, autoFocus = false,
        )
    }
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    @Test fun macroManual() = both("macro-manual") {
        MacroEditScreen(MacroEditState(today, today, id = "q", protein = "150", carbs = "240", fat = "70", kcal = "2500", existing = true), {}, {}, {}, {}, {}, {}, {}, {}, autoFocus = false)
    }
    /** kcal only, macros unknown (n/a), with a name. */
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    @Test fun macroKcalOnly() = both("macro-kcal-only") {
        MacroEditScreen(MacroEditState(today, today, id = "q", name = "Pizza out", kcal = "1050", existing = true), {}, {}, {}, {}, {}, {}, {}, {}, autoFocus = false)
    }
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    @Test fun macroError() = both("macro-error") {
        MacroEditScreen(MacroEditState(today, today, protein = "30", showError = true), {}, {}, {}, {}, {}, {}, {}, {}, autoFocus = false)
    }
    @Test fun body() = both("body") {
        BodyEditScreen(BodyEditState(today, today, "76.9", "37.3", "12.5", lastDate = today.minusDays(10), lastWeight = 77.2), {}, {}, {}, {}, {}, {}, {}, autoFocus = false)
    }
    @Test fun goal() = both("goal") { GoalEditScreen(newGoalForm(today).copy(name = "Muscle Up"), {}, {}, {}, {}, {}, {}, autoFocus = false) }
    @Test fun exercise() = both("exercise") {
        ExerciseEditScreen(ExerciseForm("Muscle-up kip", ExerciseCategory.PULL, isNew = false), {}, {}, {}, {}, {}, {})
    }
    @Test fun plan() = both("plan") {
        val rows = listOf(
            RowForm("a", ex("Pull-up"), "4", TargetType.REPS, "6", "", "120"),
            RowForm("b", ex("Dip"), "3", TargetType.MAX, "", "10", "90"),
        )
        PlanEditScreen(PlanForm("Pull", setOf(1), rows, listOf(GoalLinkForm("g1", "Muscle Up", true, "36")), isNew = false, dayType = DayType.PULL), MockDataGenerator.exercises, PlanEditActions())
    }
    @Test fun session() = both("session") {
        fun set(i: Int) = SessionSet("s$i", "s1", "pu", "Pull-up", 0, i, TargetType.REPS, 6, 6, null, 10.0, SetStatus.DONE, now, 90)
        SessionEditScreen(
            SessionEditUi(true, title = "Pull", date = today, today = today, durationMin = "55", rpe = 8, rows = (0..2).map { EditRow(set(it), "10", "6", true) }),
            {}, {}, {}, {}, {}, {}, {}, { _, _ -> },
        )
    }
}
