package com.lifehub.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.navigation.LhBottomBar
import com.lifehub.app.navigation.Routes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Composable
private fun Shell() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {}
        LhBottomBar(currentPattern = Routes.HOME_ROOT, onTab = {})
        Box(Modifier.weight(0.2f)) {}
        LhBottomBar(currentPattern = Routes.TRENDS, onTab = {}, addOpen = true)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class ShellScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shellLight() {
        rule.setContent { LhTheme(darkTheme = false) { Shell() } }
        rule.onRoot().captureRoboImage("build/screens/shell-light.png")
    }

    @Test fun shellDark() {
        rule.setContent { LhTheme(darkTheme = true) { Shell() } }
        rule.onRoot().captureRoboImage("build/screens/shell-dark.png")
    }
}
