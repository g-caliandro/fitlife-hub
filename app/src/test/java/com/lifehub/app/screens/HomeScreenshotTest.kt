package com.lifehub.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import com.lifehub.app.navigation.ActiveWorkoutBar
import com.lifehub.app.navigation.LhBottomBar
import com.lifehub.app.navigation.Routes
import com.lifehub.app.navigation.TabShell
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.ui.GoalCardModel
import com.lifehub.app.core.ui.charts.ChartPoint
import com.lifehub.app.data.model.*
import com.lifehub.app.domain.*
import com.lifehub.app.feature.home.HomeScreen
import com.lifehub.app.feature.home.HomeUiState
import com.lifehub.app.feature.home.TodayPlan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 11, 12)

/** Shared fixture for Home + Trends screenshots (TODAY = Thu 12 Nov 2026). */
internal object Fixture {
    val start: LocalDate = TODAY.minusDays(35)
    val plan = Plan("p1", "Pull & Bar", setOf(1, 4, 6), null, false, Instant.EPOCH)
    val plan2 = Plan("p2", "Legs & Core", setOf(3, 5), null, false, Instant.EPOCH)
    fun macros(empty: Boolean) = if (empty) emptyList() else (0..35).filter { it % 7 != 5 }.map { i ->
        val p = 120 + (i * 37) % 55
        MacroDay(start.plusDays(i.toLong()), p, 250, 70, 4 * p + 1000 + 630 + (i * 53) % 300, false, Instant.EPOCH)
    }
    fun body(empty: Boolean) = if (empty) emptyList() else listOf(0, 4, 8, 11, 15, 18, 22, 25, 29, 32, 35).mapIndexed { k, i ->
        BodyEntry(start.plusDays(i.toLong()), 78.4 - k * 0.2, 36.4 + k * 0.1, 13.2 - k * 0.1, Instant.EPOCH)
    }
    fun sessions(empty: Boolean) = if (empty) emptyList() else (0..35).filter { start.plusDays(it.toLong()).dayOfWeek.value in setOf(1, 3, 4, 6) }.map { i ->
        val d = start.plusDays(i.toLong())
        val s = WorkoutSession("s$i", if (d.dayOfWeek.value == 3) "p2" else "p1", "n", d, Instant.EPOCH, null, 3000, SessionStatus.COMPLETED, null, null, false, 0, null)
        val sets = (0 until 6).map { k ->
            SessionSet("s$i-$k", s.id, "e", "e", 0, k, if (k == 5) TargetType.SECONDS else TargetType.REPS, 8, 6 + (i + k) % 4, 25 + k, null, SetStatus.DONE, null, 90)
        }
        SessionWithSets(s, sets)
    }.filter { it.session.date <= TODAY }
}

private enum class Training { DUE, DONE, REST, NONE }

private fun sampleState(empty: Boolean = false, training: Training = Training.DUE): HomeUiState {
    val macros = Fixture.macros(empty)
    val body = Fixture.body(empty)
    val sessions = Fixture.sessions(empty)
    val plans = listOf(Fixture.plan, Fixture.plan2)
    val goals = if (empty) emptyList() else listOf(
        Goal("g1", "First strict muscle-up", null, TODAY.minusDays(50), TODAY.plusDays(40), false, GoalState.ACTIVE, null, Instant.EPOCH),
        Goal("g2", "Lose 3 kg", null, TODAY.minusDays(20), TODAY.plusDays(70), false, GoalState.ACTIVE, null, Instant.EPOCH),
    ).map { g ->
        val links = if (g.id == "g1") listOf(GoalPlanLink("g1", "p1", 36), GoalPlanLink("g1", "p2", 12)) else emptyList()
        GoalCardModel(g.id, g.name, g.state, timeProgress(g.startDate, g.deadline, TODAY), planProgress(GoalWithLinks(g, links), plans, sessions.map { it.session }, TODAY))
    }
    val tomorrow = listOf(TodayPlan("p2", "Legs & Core", false, DayType.LEGS, 6))
    return HomeUiState(
        TODAY, 150, goals, false,
        todayMacro = macros.firstOrNull { it.date == TODAY },
        todayPlans = when (training) {
            Training.DUE -> listOf(TodayPlan("p1", "Pull & Bar", false, DayType.PULL, 5))
            Training.DONE -> listOf(TodayPlan("p1", "Pull & Bar", true, DayType.PULL, 5))
            else -> emptyList()
        },
        tomorrowPlans = if (training == Training.NONE) emptyList() else tomorrow,
        hasPlans = training != Training.NONE,
        latestBody = body.lastOrNull(),
        kcalTarget = if (empty) null else 2600, carbsTargetG = 250, fatTargetG = 80,
        todayFood = if (empty) emptyList() else listOf(
            FoodEntry("f1", TODAY, null, "Greek yogurt", null, 200.0, 118.0, 20.0, 8.0, 6.0, Instant.EPOCH),
            FoodEntry("f2", TODAY, null, "Chicken breast", null, 180.0, 297.0, 56.0, 0.0, 6.5, Instant.EPOCH),
            FoodEntry("f3", TODAY, null, "Rolled oats", null, 70.0, 260.0, 9.0, 41.0, 5.0, Instant.EPOCH),
        ),
        streakDays = if (empty) 0 else 12,
        streakWeek = (if (empty) List(7) { StreakDay.NONE } else listOf(
            StreakDay.TRAINED, StreakDay.REST, StreakDay.FORGIVEN, StreakDay.TRAINED, StreakDay.TRAINED, StreakDay.REST, StreakDay.PENDING,
        )).mapIndexed { i, st -> TODAY.minusDays(6L - i) to st },
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1300dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, state: HomeUiState) {
        rule.setContent { LhTheme(darkTheme = dark) { HomeScreen(state) } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun homeLight() = shot("home-light", false, sampleState())
    @Test fun homeDark() = shot("home-dark", true, sampleState())
    @Test fun homeDone() = shot("home-done-light", false, sampleState(training = Training.DONE))
    @Test fun homeRestDay() = shot("home-rest-dark", true, sampleState(training = Training.REST))
    @Test fun homeEmpty() = shot("home-empty-light", false, sampleState(empty = true, training = Training.NONE))
}

/** Real tab chrome ([TabShell]) around Home: workout banner at the top, centered-notch bar at the bottom. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class HomeShellScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean) {
        rule.setContent {
            LhTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(LhTheme.colors.background)) {
                    TabShell(
                        banner = { ActiveWorkoutBar("Pull & Bar", 1930, onResume = {}) },
                        bottomBar = { LhBottomBar(currentPattern = Routes.HOME_ROOT, onTab = {}) },
                    ) { bottom -> HomeScreen(sampleState(), modifier = androidx.compose.ui.Modifier.padding(bottom = bottom)) }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun shellBannerLight() = shot("shell-banner-light", false)
    @Test fun shellBannerDark() = shot("shell-banner-dark", true)
}

/** Tapping the Home streak badge opens the streak sheet (on demand, not the daily auto-celebration). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StreakBadgeTapScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, empty: Boolean = false) {
        var seen = 0
        rule.setContent { LhTheme(darkTheme = dark) { HomeScreen(sampleState(empty = empty), onStreakSeen = { seen++ }) } }
        rule.onNodeWithTag("home-streak").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag("streak-celebration").assertExists()
        rule.onAllNodes(isRoot())[1].captureRoboImage("build/screens/$name.png")
        rule.onNodeWithTag("streak-continue").performClick()
        rule.onNodeWithTag("streak-celebration").assertDoesNotExist()
        org.junit.Assert.assertEquals("manual open must not count as the daily auto-show", 0, seen)
    }

    @Test fun badgeTapLight() = shot("streak-badge-tap-light", false)
    @Test fun badgeTapDark() = shot("streak-badge-tap-dark", true)
    @Test fun badgeTapZero() = shot("streak-badge-tap-zero-light", false, empty = true)
}
