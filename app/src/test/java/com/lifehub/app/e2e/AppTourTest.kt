package com.lifehub.app.e2e

import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onRoot
import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.lifehub.app.MainActivity
import com.lifehub.app.container
import com.lifehub.app.data.model.SessionStatus
import com.lifehub.app.data.model.HomePeriod
import com.lifehub.app.domain.Periods
import com.lifehub.app.core.designsystem.fmtRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * End-to-end tour of the real app (MainActivity + real AppContainer + fresh Room DB under Robolectric).
 * Mock data is generated through the Settings button; every step is reached by clicks and captured to
 * build/screens/tour-XX-name.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class AppTourTest : AppTourBase("tour")

/** Same tour with the system in night mode -> build/screens/tour-dark-XX-name.png (UX item 23). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-night-xhdpi")
class AppTourDarkTest : AppTourBase("tour-dark")

abstract class AppTourBase(private val prefix: String) {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private var step = 0

    private fun shot(name: String) {
        rule.waitForIdle()
        step++
        captureScreenRoboImage("build/screens/%s-%02d-%s.png".format(prefix, step, name))
    }

    private fun waitFor(m: SemanticsMatcher, timeoutMs: Long = 20_000) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitText(t: String, substring: Boolean = false) = waitFor(hasText(t, substring = substring))
    private fun waitGone(m: SemanticsMatcher, timeoutMs: Long = 20_000) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
    private fun click(text: String) = rule.onNodeWithText(text).performClick()
    private fun clickDesc(desc: String) = rule.onAllNodesWithContentDescription(desc).onFirst().performClick()
    private fun back() = clickDesc("Back")
    private fun tab(name: String) = rule.onNodeWithTag("tab-$name").performClick()
    private fun field(tag: String) =
        rule.onNode(hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))), useUnmergedTree = true)

    private fun tapRow(tag: String) {
        val m = hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))
        rule.onNode(m).performScrollTo().performClick()
    }

    /** An "… · Undo" snackbar overlays the bottom of the list (as on a device): let it time out before tapping there. */
    private fun snackbarTimeout() {
        rule.mainClock.advanceTimeBy(11_000)
        waitGone(hasText("Undo"))
    }

    private val c get() = rule.activity.container

    @Test
    fun tour() {
        shadowOf(rule.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val today = c.time.today()

        // ---- Home (empty) -> Profile avatar -> Data -> generate mock data (same path as the user) ----
        waitFor(hasTestTag("home-list"))
        shot("home-empty")
        clickDesc("Profile")
        waitText("Data")
        shot("you-empty")
        click("Data")
        waitText("Generate mock data")
        click("Generate mock data")
        waitText("Sample data generated")
        shot("settings-mock-generated")
        assertTrue(runBlocking { !c.backup.isEmpty() })
        back()
        waitText("Achievements")
        back()
        waitFor(hasTestTag("home-list"))
        waitFor(hasTestTag("home-training"))
        shot("home-mock")

        // ---- Trends: 1W, previous period, 2W ----
        fun range(p: HomePeriod, k: Int) = fmtRange(Periods.period(today, p, k))
        tab("Trends")
        waitFor(hasTestTag("trends-list"))
        waitText(range(HomePeriod.ONE_WEEK, 0))
        shot("trends-1w-current")
        clickDesc("Previous period")
        waitText(range(HomePeriod.ONE_WEEK, 1))
        shot("trends-1w-previous")
        click("2W")
        waitText(range(HomePeriod.TWO_WEEKS, 1))
        shot("trends-2w-previous")
        clickDesc("Next period")
        waitText(range(HomePeriod.TWO_WEEKS, 0))
        shot("trends-2w-current")
        click("1W")
        waitText(range(HomePeriod.ONE_WEEK, 0))

        // ---- Calendar: previous month, tap a day with a workout -> day summary ----
        tab("Diary")
        waitText("Workouts")
        shot("calendar-current")
        clickDesc("Previous month")
        val prev = YearMonth.from(today).minusMonths(1)
        waitText("${prev.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${prev.year}")
        val day = runBlocking { c.db.sessionDao().getAll() }
            .filter { it.status == SessionStatus.COMPLETED && YearMonth.from(it.date) == prev && it.date.dayOfMonth in 8..22 }
            .maxOf { it.date }
        rule.onNodeWithTag("day-$day").performClick()
        waitText("Workouts")
        shot("calendar-prev-month-day-summary")

        // ---- Profile (Home avatar) -> Goals -> detail -> edit; achievements log ----
        tab("Home")
        waitFor(hasTestTag("home-list"))
        clickDesc("Profile")
        waitText("Targets")
        click("Goals")
        waitText("Muscle Up")
        shot("goals-list")
        click("Muscle Up")
        waitText("Plans")
        waitText("/ 24 sessions", substring = true)
        shot("goal-detail")
        clickDesc("Edit goal")
        waitText("Edit goal")
        shot("goal-edit")
        back()
        waitText("Plans")
        back()
        waitText("Muscle Up")
        clickDesc("Achievements")
        waitText("Achievements")
        shot("achievements-log")
        back()
        back()
        waitText("Targets")
        shot("you")
        back()
        waitFor(hasTestTag("home-list"))

        // ---- Train -> plan detail -> plan editor -> start workout ----
        tab("Train")
        waitText("Pull")
        shot("train-plans")
        click("Pull")
        waitText("Start this plan")
        shot("plan-detail")
        clickDesc("Edit plan")
        waitText("Edit plan")
        shot("plan-editor")
        back()
        waitText("Start this plan")
        click("Start this plan")
        waitText("Pull")
        shot("workout-start")
        click("Pull")
        waitText("Start workout")
        shot("workout-preview")
        click("Start workout")

        // ---- Live workout (focus mode): set step, rest countdown, ready-ack, overview jump, MAX input, hold, finish ----
        fun title(t: String) = waitFor(hasTestTag("focus-title") and hasText(t))
        fun skipUntil(t: String) {
            var n = 0
            while (n++ < 30) {
                rule.waitForIdle()
                val titles = rule.onAllNodes(hasTestTag("focus-title"), useUnmergedTree = true).fetchSemanticsNodes()
                    .mapNotNull { (if (it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { x -> x.text } else null) }
                if (titles == listOf(t)) return
                if (titles.size != 1) { Thread.sleep(50); continue }
                click("Skip")
            }
            title(t)
        }
        fun skipSets(n: Int) = repeat(n) { click("Skip"); rule.waitForIdle() }
        title("Pull-up")
        waitFor(hasTestTag("set-prev")) // "Previous" loaded from history
        shot("live-set-step")
        click("Done")
        waitText("Skip rest")
        shot("live-rest-countdown")
        var guard = 0
        while (rule.onAllNodesWithText("-15 s").fetchSemanticsNodes().isNotEmpty() && guard++ < 40) {
            rule.onNodeWithText("-15 s").performClick()
            Thread.sleep(80)
            rule.waitForIdle()
        }
        waitText("Ready \u2014 next set")
        shot("live-ready-ack")
        click("Start next set")
        title("Pull-up")
        skipUntil("Explosive pull-up") // skips rest of Pull-up, Muscle-up negative, Straight-bar dip transition

        // Overview sheet: full list for editing; jump back to an earlier pending set and forward again.
        clickDesc("Overview")
        waitFor(hasTestTag("live-list"))
        shot("live-overview-sheet")
        rule.onNodeWithTag("live-list").performScrollToNode(hasTestTag("ex-header-4"))
        rule.onNodeWithTag("ex-header-4").performClick() // collapsed (not the current exercise) -> expand
        rule.onNodeWithTag("live-list").performScrollToNode(hasTestTag("set-4-1"))
        rule.onNodeWithTag("set-4-1", useUnmergedTree = true).performClick()
        title("Tuck front lever")
        clickDesc("Overview")
        waitFor(hasTestTag("live-list"))
        rule.onNodeWithTag("live-list").performScrollToNode(hasTestTag("ex-header-3"))
        rule.onNodeWithTag("ex-header-3").performClick()
        rule.onNodeWithTag("live-list").performScrollToNode(hasTestTag("set-3-0"))
        rule.onNodeWithTag("set-3-0", useUnmergedTree = true).performClick()
        title("Explosive pull-up")

        // MAX set: the reps ruler is on screen; set 25 and log with Done.
        rule.onNodeWithTag("ruler-Reps").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(25f) }
        shot("live-max-input")
        click("Done")
        waitText("New PR", substring = true)
        shot("live-max-pr-banner")
        waitText("Skip rest")
        click("Skip rest")
        skipUntil("Tuck front lever")

        // SECONDS set: Start hold -> running countdown -> Stop & Done.
        title("Tuck front lever")
        waitText("Start hold timer")
        click("Start hold timer")
        waitText("Stop & Done")
        shot("live-hold-running")
        click("Stop & Done")
        waitText("Skip rest")
        click("Skip rest")
        title("Tuck front lever")

        // Finish early: unlogged sets -> "Finish anyway".
        clickDesc("More")
        click("Finish workout")
        waitText("Finish anyway")
        rule.onNodeWithTag("rpe-8", useUnmergedTree = true).performClick()
        shot("live-finish-summary")
        click("Finish anyway")
        waitFor(hasTestTag("home-list"))
        val saved = runBlocking { c.db.sessionDao().getAll() }.filter { it.date == today && it.status == SessionStatus.COMPLETED }
        assertEquals(1, saved.size)
        assertEquals(8, saved.single().rpe)
        assertNotNull(saved.single().durationSec)
        shot("home-after-workout")

        // ---- The new session appears in the calendar (today) ----
        tab("Diary")
        waitFor(hasContentDescription("Today"))
        clickDesc("Today")
        rule.waitForIdle()
        rule.onNodeWithTag("day-$today").performClick()
        waitText("Workouts")
        waitText("Pull", substring = true)
        shot("calendar-today-with-workout")

        // ---- Add food (Recent tab, offline) ----
        val foodBefore = runBlocking { c.food.observeEntries(today, today).first().size }
        rule.onNodeWithTag("add-food").performScrollTo().performClick()
        waitFor(hasTestTag("food-tabs"))
        shot("food-add")
        val foodRow = SemanticsMatcher("food row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("food-row-") == true }
        waitFor(foodRow)
        shot("food-add-recent")
        rule.onAllNodes(foodRow, useUnmergedTree = true)[0].performClick()
        waitFor(hasTestTag("food-grams"))
        shot("food-detail")
        click("Add to today")
        waitFor(hasTestTag("add-food"))
        assertEquals(foodBefore + 1, runBlocking { c.food.observeEntries(today, today).first().size })
        shot("calendar-after-food")
        snackbarTimeout()

        // ---- Macro entry (kcal auto) ----
        // (from the calendar's inline day detail: today is still selected; mock data has macros today -> "Edit")
        tapRow("macros-row")
        waitFor(hasTestTag("macro-protein"))
        field("macro-protein").performTextReplacement("160")
        field("macro-carbs").performTextReplacement("250")
        field("macro-fat").performTextReplacement("70")
        field("macro-kcal").performTextReplacement("") // empty kcal => auto 4/4/9 (mock may have stored a manual kcal today)
        waitText("2,270", substring = true)
        shot("macro-entry")
        click("Save")
        waitText("Workouts")
        val m = runBlocking { c.macros.get(today) }!!
        assertEquals(160, m.proteinG)
        assertEquals(4 * 160 + 4 * 250 + 9 * 70, m.kcal)
        assertTrue(m.kcalIsAuto)
        snackbarTimeout()

        // ---- Body entry ----
        tapRow("body-row")
        waitFor(hasTestTag("body-weight"))
        field("body-weight").performTextReplacement("77.2")
        field("body-muscle").performTextReplacement("37.4")
        field("body-fat").performTextReplacement("12.3")
        shot("body-entry")
        click("Save")
        waitText("Workouts")
        val b = runBlocking { c.body.get(today) }!!
        assertEquals(77.2, b.weightKg, 1e-9)
        assertEquals(37.4, b.muscleKg!!, 1e-9)
        assertEquals(12.3, b.fatKg!!, 1e-9)
        tab("Home")
        waitFor(hasTestTag("home-list"))
        shot("home-final")

        // ---- Settings (Profile -> Data) ----
        clickDesc("Profile")
        waitText("Data")
        click("Data")
        waitText("Export JSON")
        shot("settings")
        back()
        waitText("Targets")
        back()
        waitFor(hasTestTag("home-list"))

        // ---- (+) add sheet: every action reaches its screen ----
        fun fab() { rule.onNodeWithTag("fab-add").performClick(); waitText("Quick macros") }
        fab()
        shot("add-sheet")
        click("Add food"); waitFor(hasTestTag("food-tabs")); back(); waitFor(hasTestTag("home-list"))
        fab(); click("Quick macros"); waitFor(hasTestTag("macro-protein")); back(); waitFor(hasTestTag("home-list"))
        fab(); click("Log weight"); waitFor(hasTestTag("body-weight")); back(); waitFor(hasTestTag("home-list"))
        fab(); click("Start workout")
        waitText("Legs")
        click("Legs")
        waitText("Start workout")
        click("Start workout")
        waitFor(hasTestTag("focus-title"))
        back() // minimize: the workout stays active
        waitFor(hasTestTag("home-list"))
        fab()
        waitText("Resume workout")
        shot("add-sheet-resume")
        click("Resume workout")
        waitFor(hasTestTag("focus-title"))
        shot("live-resumed")
    }
}
