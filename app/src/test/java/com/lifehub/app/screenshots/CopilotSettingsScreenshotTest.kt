package com.lifehub.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.feature.settings.ConnectionTestState
import com.lifehub.app.feature.settings.CopilotSettingsScreen
import com.lifehub.app.feature.settings.CopilotUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class CopilotSettingsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun copilotSettingsLightSuccess() {
        rule.setContent { LhTheme(darkTheme = false) {
            CopilotSettingsScreen(CopilotUiState(llmKeyMask = "••••6789", test = ConnectionTestState.Success("anthropic/claude-haiku-5.5")), onBack = {})
        } }
        rule.onRoot().captureRoboImage("build/screens/copilot-settings-light.png")
    }

    @Test fun copilotSettingsDarkToolsError() {
        rule.setContent { LhTheme(darkTheme = true) {
            CopilotSettingsScreen(CopilotUiState(test = ConnectionTestState.Failed("This model does not support tool calling, which the copilot needs. Choose a model that does.")), onBack = {})
        } }
        rule.onRoot().captureRoboImage("build/screens/copilot-settings-dark.png")
    }
}
