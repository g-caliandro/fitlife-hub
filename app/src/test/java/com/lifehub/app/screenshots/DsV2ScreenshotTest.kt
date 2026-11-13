package com.lifehub.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.navigation.LhBottomBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Composable
private fun DsV2Catalog() {
    Column(Modifier.fillMaxWidth().background(LhTheme.colors.background)) {
        LhTopBar("Macros", onBack = {}, actions = {
            LhIconAction(Icons.Outlined.Edit, "Edit", {}); LhIconAction(Icons.Outlined.Today, "Today", {})
        })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhActionTile(Icons.Outlined.FitnessCenter, "Workout", {}, Modifier.weight(1f))
                LhActionTile(Icons.Outlined.Restaurant, "Macros", {}, Modifier.weight(1f))
                LhActionTile(Icons.Outlined.MonitorWeight, "Weight", {}, Modifier.weight(1f))
            }
            LhSectionHeader("Goals", action = { LhSeeAllChip({}) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhButton("Primary", {}, leadingIcon = Icons.Outlined.PlayArrow)
                LhButton("Tonal", {}, variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.ContentCopy)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhButton("Outline", {}, variant = LhButtonVariant.Outline)
                LhButton("Ghost", {}, variant = LhButtonVariant.Ghost)
                LhButton("Delete", {}, variant = LhButtonVariant.Destructive, leadingIcon = Icons.Outlined.Delete)
            }
            LhEmptyState("No sessions yet", description = "Log your first workout.", icon = Icons.Outlined.FitnessCenter, actionText = "Start workout", onAction = {}, actionIcon = Icons.Outlined.PlayArrow)
        }
        LhSaveBar({}, secondary = { LhButton("Copy yesterday", {}, Modifier.fillMaxWidth(), variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.ContentCopy) })
        LhBottomBar(currentPattern = com.lifehub.app.navigation.Tab.Train.route, onTab = {})
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class DsV2ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun light() {
        rule.setContent { LhTheme(darkTheme = false) { DsV2Catalog() } }
        rule.onRoot().captureRoboImage("build/screens/ds-v2-light.png")
    }

    @Test fun dark() {
        rule.setContent { LhTheme(darkTheme = true) { DsV2Catalog() } }
        rule.onRoot().captureRoboImage("build/screens/ds-v2-dark.png")
    }
}
