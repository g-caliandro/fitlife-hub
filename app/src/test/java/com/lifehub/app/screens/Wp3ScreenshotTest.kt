package com.lifehub.app.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.*
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.feature.body.*
import com.lifehub.app.feature.calendar.*
import com.lifehub.app.feature.nutrition.*
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
class Wp3ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    private fun set(ex: String, name: String, i: Int, reps: Int?, secs: Int? = null, w: Double? = null) = SessionSet(
        "$ex$i", "s1", ex, name, 0, i, if (secs != null) TargetType.SECONDS else TargetType.REPS, 6, reps, secs, w,
        SetStatus.DONE, now, 90,
    )

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { LhTheme(darkTheme = false) { content() } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun macroForm() = shot("wp3-macro-form") {
        MacroEditScreen(MacroEditState(today, today, protein = "150", carbs = "240", fat = "70", kcal = ""), {}, {}, {}, {}, {}, {}, {}, {}, autoFocus = false)
    }

    @Test fun bodyForm() = shot("wp3-body-form") {
        BodyEditScreen(
            BodyEditState(today, today, "76.9", "37.3", "12.5", lastDate = today.minusDays(10), lastWeight = 77.2),
            {}, {}, {}, {}, {}, {}, {}, autoFocus = false,
        )
    }

    @Test fun bodyList() = shot("wp3-body-list") {
        BodyListScreen(listOf(BodyEntry(today, 76.9, 37.3, 12.5, now), BodyEntry(today.minusDays(10), 77.2, null, null, now)), today, {}, {}, {})
    }
}
