package com.lifehub.app.screens

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.domain.StreakDay
import com.lifehub.app.feature.home.StreakCelebration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StreakScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)
    private val week = listOf(StreakDay.TRAINED, StreakDay.REST, StreakDay.REST, StreakDay.TRAINED, StreakDay.REST, StreakDay.TRAINED, StreakDay.TRAINED)
        .mapIndexed { i, s -> today.minusDays(6L - i) to s }

    private fun shot(name: String, dark: Boolean) {
        rule.mainClock.autoAdvance = true
        rule.setContent { LhTheme(darkTheme = dark) { StreakCelebration(12, week) {} } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onAllNodes(isRoot())[1].captureRoboImage("build/screens/$name.png")
    }

    @Test fun celebration() = shot("streak-celebration", false)
    @Test fun celebrationDark() = shot("streak-celebration-dark", true)
}
