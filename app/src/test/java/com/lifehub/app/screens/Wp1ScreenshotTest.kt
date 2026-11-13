package com.lifehub.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.ui.GoalCardModel
import com.lifehub.app.data.model.*
import com.lifehub.app.core.ui.ExercisePickerSheet
import com.lifehub.app.mock.MockDataGenerator
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.lifehub.app.domain.PlanProgress
import com.lifehub.app.domain.timeProgress
import com.lifehub.app.feature.goals.*
import com.lifehub.app.feature.plans.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
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
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class Wp1ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private fun ex(name: String) = MockDataGenerator.exercises.first { it.name == name }
    private fun goal(id: String, name: String, start: LocalDate, state: GoalState = GoalState.ACTIVE) =
        Goal(id, name, null, start, start.plusDays(84), true, state, null, Instant.EPOCH)
    private fun pe(name: String, sets: Int, t: TargetType, v: Int?, w: Double? = null) =
        PlanExerciseItem(PlanExercise(name, "p1", ex(name).id, 0, sets, t, v, w, 90), ex(name))
    private val plan = Plan("p1", "Pull & Bar", setOf(2, 4), null, false, Instant.EPOCH)
    private val planWithEx = PlanWithExercises(
        plan,
        listOf(pe("Pull-up", 4, TargetType.REPS, 6), pe("Dip", 3, TargetType.REPS, 8, 10.0), pe("Dip", 3, TargetType.MAX, null)
            ?: pe("Dead hang", 2, TargetType.SECONDS, 30)),
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        val dark = androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { LhTheme(darkTheme = dark.value) { content() } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
        dark.value = true; rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/$name-dark.png")
    }

    private fun cards(): List<GoalCardModel> {
        val g1 = goal("g1", "Muscle Up", today.minusDays(70)); val g2 = goal("g2", "Dragon Flag (clean)", today.minusDays(100))
        fun m(g: Goal, plans: List<PlanProgress>): GoalCardModel {
            val t = timeProgress(g.startDate, g.deadline, today)
            return GoalCardModel(g.id, g.name, g.state, t, plans, goalCaption(t, g.deadline, g.state))
        }
        return listOf(m(g2, listOf(PlanProgress("p2", "Core", 12, null))), m(g1, listOf(PlanProgress("p1", "Pull & Bar", 26, 36))))
    }

    @Test fun goalsList() = shot("wp1-goals") { GoalsScreen(cards(), {}, {}) }
    @Test fun goalsEmpty() = shot("wp1-goals-empty") { GoalsScreen(emptyList(), {}, {}) }

    @Test fun goalDetail() = shot("wp1-goal-detail") {
        val g = goal("g1", "Muscle Up", today.minusDays(70)).copy(notes = "Strict form first.")
        GoalDetailScreen(
            GoalDetailState(g, timeProgress(g.startDate, g.deadline, today), listOf(GoalPlanItem(planWithEx, 26, 36)), emptyList(), 4),
            {}, {}, {}, {}, {}, {}, { _, _ -> }, {},
        )
    }

    @Test fun goalEdit() = shot("wp1-goal-edit") {
        GoalEditScreen(newGoalForm(today).copy(name = "Muscle Up"), {}, {}, {}, {}, {}, {}, autoFocus = false)
    }

    @Test fun plansList() = shot("wp1-plans") {
        PlansScreen(
            PlansUiState(
                listOf(
                    PlanRowModel("p1", "Pull & Bar", setOf(2, 4), 5, listOf("Muscle Up"), false, DayType.PULL, estMinutes = 45),
                    PlanRowModel("p2", "Core", setOf(1, 3, 5), 4, emptyList(), false, DayType.CORE, estMinutes = 25),
                    PlanRowModel("p3", "Push", setOf(3), 5, listOf("Dragon Flag (clean)", "Handstand", "Planche lean"), false, DayType.PUSH, estMinutes = 30),
                ),
                todayIso = 4,
            ), {}, {}, {}, {},
        )
    }
    @Test fun plansEmpty() = shot("wp1-plans-empty") { PlansScreen(PlansUiState(), {}, {}, {}, {}) }

    @Test fun planDetail() = shot("wp1-plan-detail") {
        PlanDetailScreen(PlanDetailState(planWithEx, listOf(PlanGoalChip("g1", "Muscle Up", 26, 36)), emptyList(), 4), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun planEdit() = shot("wp1-plan-edit") {
        val rows = listOf(
            RowForm("a", ex("Pull-up"), "4", TargetType.REPS, "6", "", "120"),
            RowForm("b", ex("Dip"), "3", TargetType.MAX, "", "10", "90"),
            RowForm("c", ex("Dead hang"), "2", TargetType.SECONDS, "30", "", "60"),
        )
        PlanEditScreen(
            PlanForm("Pull & Bar", setOf(2, 4), rows, listOf(GoalLinkForm("g1", "Muscle Up", true, "36"), GoalLinkForm("g2", "Dragon Flag", false)), isNew = false),
            MockDataGenerator.exercises, PlanEditActions(),
        )
    }

    @Test fun planEditV2() = shot("plan-editor-v2") {
        val rows = listOf(
            RowForm("a", ex("Pull-up"), "4", TargetType.REPS, "6", "", "120"),
            RowForm("b", ex("Dip"), "3", TargetType.MAX, "", "10", "90"),
            RowForm("c", ex("Dead hang"), "2", TargetType.SECONDS, "30", "", "60"),
        )
        PlanEditScreen(
            PlanForm("Pull", setOf(1), rows, listOf(GoalLinkForm("g1", "Muscle Up", true, "36")), isNew = false, dayType = DayType.PULL),
            MockDataGenerator.exercises, PlanEditActions(),
        )
    }

    @Test fun exercises() = shot("wp1-exercises") {
        ExercisesScreen(MockDataGenerator.exercises.take(12) + Exercise("c1", "Muscle-up kip", ExerciseCategory.PULL, TargetType.REPS, true, true), {}, {}, {})
    }

    @Test fun exercisesEmpty() = shot("exercises-empty") { ExercisesScreen(emptyList(), {}, {}, {}) }

    /** Picker on an empty library: empty state, then the inline form, then the saved exercise ready to pick. */
    @Test fun pickerEmptyAndCreate() {
        val list = androidx.compose.runtime.mutableStateListOf<Exercise>()
        var picked: Exercise? = null
        rule.setContent { LhTheme { ExercisePickerSheet(list, { picked = it }, {}, onCreate = { list += it }) } }
        rule.waitForIdle(); captureScreenRoboImage("build/screens/exercise-picker-empty.png")
        rule.onNodeWithText("Add your first exercise").performClick()
        rule.waitForIdle(); captureScreenRoboImage("build/screens/exercise-picker-create.png")
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("Tuck front lever")
        rule.onNodeWithText("Seconds").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle(); captureScreenRoboImage("build/screens/exercise-picker-created.png")
        assertEquals(listOf("Tuck front lever"), list.map { it.name })
        assertEquals(TargetType.SECONDS, list.single().defaultTargetType)
        rule.onAllNodesWithText("Tuck front lever").onLast().performClick()   // [search field, list row]
        assertEquals(list.single(), picked)
    }

    @Test fun exerciseEdit() = shot("wp1-exercise-edit") {
        ExerciseEditScreen(ExerciseForm("Muscle-up kip", ExerciseCategory.PULL, isNew = false), {}, {}, {}, {}, {}, {})
    }

    @Composable private fun settings(sec: com.lifehub.app.feature.settings.SettingsSection) = com.lifehub.app.feature.settings.SettingsScreen(
        state = com.lifehub.app.feature.settings.SettingsUiState(settings = com.lifehub.app.data.prefs.Settings(kcalTarget = 2600), appVersion = "0.3"),
        notificationsBlocked = false, onBack = {}, onProteinTarget = {}, onNotify = { _, _ -> }, onTime = {}, onOpenSystemSettings = {},
        onShare = {}, onSave = {}, onImport = {}, onMock = {}, onClear = {}, onConfirmImport = {}, onDismissImport = {}, onConfirm = {}, onDismissConfirm = {}, section = sec,
    )
    @Test fun targets() = shot("v3b-targets") { settings(com.lifehub.app.feature.settings.SettingsSection.TARGETS) }
    @Test fun notifications() = shot("v3b-notifications") { settings(com.lifehub.app.feature.settings.SettingsSection.NOTIFICATIONS) }
    @Test fun data() = shot("v3b-data") { settings(com.lifehub.app.feature.settings.SettingsSection.DATA) }
    @Test fun about() = shot("v3b-about") { com.lifehub.app.feature.settings.AboutRoute {} }
    @Test fun you() = shot("v3b-you") { com.lifehub.app.feature.you.YouScreen(com.lifehub.app.feature.you.YouState(76.7, 14.2, 2, 2, 12, true, "Protein 150 g · 2,600 kcal"), {}, today) }
    @Test fun youEmpty() = shot("v3b-you-empty") { com.lifehub.app.feature.you.YouScreen(com.lifehub.app.feature.you.YouState(), {}, today) }

    @Test fun validation() {
        val r = RowForm("a", ex("Pull-up"), "0", TargetType.REPS, "", "x", "900")
        val f = PlanForm("", rows = listOf(r)).validated()
        assertFalse(f.isValid); assertTrue(f.rows[0].setsError != null && f.rows[0].valueError != null && f.rows[0].restError != null)
        assertTrue(PlanForm("A", rows = listOf(RowForm("a", ex("Pull-up")))).validated().isValid)
        assertFalse(PlanForm("A").validated().isValid)
        assertFalse(newGoalForm(today).copy(name = "x", deadline = today).validated().isValid)
    }
}
