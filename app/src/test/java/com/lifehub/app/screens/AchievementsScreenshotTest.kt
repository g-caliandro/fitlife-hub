package com.lifehub.app.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.AchievementKind
import com.lifehub.app.feature.achievements.AchievementsScreen
import com.lifehub.app.feature.achievements.AchievementsUi
import com.lifehub.app.mock.MockDataGenerator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class AchievementsScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)
    private val snap = MockDataGenerator.generate(today, ZoneId.of("UTC"))
    private val items = snap.achievements.sortedWith(compareByDescending<com.lifehub.app.data.model.Achievement> { it.achievedOn }.thenBy { it.dedupeKey })
    private val ui = AchievementsUi(items, snap.goals.map { it.id }.toSet(), snap.sessions.map { it.id }.toSet())

    private fun shot(name: String, u: AchievementsUi, dark: Boolean) {
        rule.setContent { LhTheme(darkTheme = dark) { AchievementsScreen(u, today) } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun light() = shot("achievements", ui, false)
    @Test fun dark() = shot("achievements-dark", ui, true)
    @Test fun empty() = shot("achievements-empty", AchievementsUi(), false)
}
