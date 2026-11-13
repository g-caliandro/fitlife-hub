package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.MetricIcon
import com.lifehub.app.core.designsystem.MetricKind
import kotlin.math.roundToInt

@Composable
fun LhMacroCard(
    label: String, color: Color, current: Double, target: Double?, modifier: Modifier = Modifier, unit: String = "g", metric: MetricKind? = null,
) {
    val c = LhTheme.colors
    LhCard(modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (metric != null) MetricIcon(metric, 16.dp, tint = color) else Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(if (metric != null) 4.dp else 8.dp))
            Text(label, style = LhType.body, color = c.foreground, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        LhProgressBar(if (target != null && target > 0) (current / target).toFloat() else 0f, color = color, height = 8.dp)
        Spacer(Modifier.height(12.dp))
        val cur = current.roundToInt()
        val txt = if (target != null) "$cur/${target.roundToInt()} $unit" else "$cur $unit"
        Text(txt, style = LhType.tnum(LhType.label), color = c.foreground, maxLines = 1)
    }
}
