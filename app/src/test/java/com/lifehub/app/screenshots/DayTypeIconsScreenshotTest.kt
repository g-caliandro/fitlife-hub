package com.lifehub.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.DayTypeIcon
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.dayType
import com.lifehub.app.core.designsystem.label
import com.lifehub.app.data.model.DayType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h420dp-xxhdpi")
class DayTypeIconsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun Panel(dark: Boolean) {
        LhTheme(darkTheme = dark) {
            val c = LhTheme.colors
            Column(Modifier.background(c.background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DayType.entries.forEach { t ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(72.dp).border(1.5.dp, c.dayType(t), LhRadius.lgShape), contentAlignment = Alignment.Center) {
                                DayTypeIcon(t, size = 48.dp)
                            }
                            Text(t.label(), style = LhType.caption, color = c.foreground)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DayType.entries.forEach { t ->
                        Box(Modifier.size(32.dp).border(1.5.dp, c.dayType(t), LhRadius.mdShape), contentAlignment = Alignment.Center) { DayTypeIcon(t, size = 16.dp) }
                        DayTypeIcon(t, size = 20.dp)
                    }
                }
            }
        }
    }

    @Test fun icons() {
        rule.setContent {
            Column {
                Panel(false); Panel(true)
            }
        }
        rule.onRoot().captureRoboImage("build/screens/daytype-icons.png")
    }
}
