package com.lifehub.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.feature.settings.*
import com.lifehub.app.data.prefs.Settings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun Content(blocked: Boolean) = SettingsScreen(
        state = SettingsUiState(settings = Settings(), appVersion = "0.1.0"),
        notificationsBlocked = blocked, onBack = {}, onProteinTarget = {}, onNotify = { _, _ -> }, onTime = {},
        onOpenSystemSettings = {}, onShare = {}, onSave = {}, onImport = {}, onMock = {}, onClear = {},
        onConfirmImport = {}, onDismissImport = {}, onConfirm = {}, onDismissConfirm = {},
    )

    @Test fun settingsLight() {
        rule.setContent { LhTheme(darkTheme = false) { Content(false) } }
        rule.onRoot().captureRoboImage("build/screens/settings-light.png")
    }

    @Test fun settingsDarkBlocked() {
        rule.setContent { LhTheme(darkTheme = true) { Content(true) } }
        rule.onRoot().captureRoboImage("build/screens/settings-dark.png")
    }
}
