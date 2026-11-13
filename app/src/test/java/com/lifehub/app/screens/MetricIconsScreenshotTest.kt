package com.lifehub.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.R
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.MetricIcon
import com.lifehub.app.core.designsystem.MetricKind
import com.lifehub.app.core.designsystem.color
import com.lifehub.app.core.designsystem.components.LhProgressBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every metric icon (incl. the custom drumstick) and the barcode icon at 16/18/24/48dp, with macro colors. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h520dp-xxhdpi")
class MetricIconsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean) {
        rule.setContent {
            LhTheme(darkTheme = dark) {
                val c = LhTheme.colors
                Column(Modifier.background(c.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricKind.entries.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(16, 18, 24, 48).forEach { MetricIcon(m, it.dp) }
                            Text(m.label, style = LhType.body, color = c.foreground)
                            LhProgressBar(0.6f, color = m.color(), height = 8.dp, modifier = Modifier.size(80.dp, 8.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(16, 18, 24, 48).forEach {
                            Icon(ImageVector.vectorResource(R.drawable.ic_barcode), null, Modifier.size(it.dp), tint = c.foreground)
                        }
                        Text("Scan", style = LhType.body, color = c.foreground)
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screens/metric-icons-$name.png")
    }

    @Test fun light() = shot("light", false)
    @Test fun dark() = shot("dark", true)
}
