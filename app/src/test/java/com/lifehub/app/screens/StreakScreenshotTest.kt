package com.lifehub.app.screens

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.domain.StreakDay
import com.lifehub.app.feature.home.StreakCelebration
import com.lifehub.app.feature.home.StreakCelebrationContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
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

    /**
     * Short phone (640dp tall) with status bar + 3-button nav bar insets simulated: the "Keep it up" button must be
     * fully inside the screen and above the nav bar, the rest scrolls.
     */
    private fun short(name: String, widthDp: Int, days: Int = 12) {
        rule.mainClock.autoAdvance = true
        rule.setContent {
            LhTheme { StreakCelebrationContent(days, week, extended = true, insets = PaddingValues(top = 24.dp, bottom = 48.dp)) {} }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag("streak-continue").assertIsDisplayed()
        val btn = rule.onNodeWithTag("streak-continue").getBoundsInRoot()
        val root = rule.onRoot().getBoundsInRoot()
        assertTrue("button bottom ${btn.bottom} must clear the 48dp nav bar (root ${root.bottom})", btn.bottom <= root.bottom - 48.dp)
        assertTrue("button width at ${widthDp}dp", btn.right - btn.left > 200.dp)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Config(qualifiers = "w360dp-h640dp-xxhdpi") @Test fun shortScreen360() = short("streak-celebration-short-360", 360)
    @Config(qualifiers = "w411dp-h640dp-xxhdpi") @Test fun shortScreen411() = short("streak-celebration-short-411", 411)
    @Config(qualifiers = "w360dp-h640dp-xxhdpi") @Test fun shortScreenZero() = short("streak-celebration-short-zero", 360, days = 0)
}
