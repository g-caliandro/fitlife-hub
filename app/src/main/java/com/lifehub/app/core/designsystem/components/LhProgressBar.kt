package com.lifehub.app.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.MetricIcon
import com.lifehub.app.core.designsystem.MetricKind
import com.lifehub.app.core.designsystem.color
import com.lifehub.app.core.designsystem.LhMotion
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.lhTween

@Composable
fun LhProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = LhTheme.colors.primary,
    height: Dp = 8.dp,
    trackColor: Color = LhTheme.colors.track,
) {
    val f = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val a = remember { Animatable(if (LhMotion.reduced) f else 0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(f) {
        if (first) { first = false; a.animateTo(f, lhTween(700)) } else a.animateTo(f, lhTween(400))
    }
    Box(modifier.fillMaxWidth().height(height).clip(LhRadius.pill).background(trackColor)) {
        if (a.value > 0.001f) Box(Modifier.fillMaxHeight().fillMaxWidth(a.value).clip(LhRadius.pill).background(color))
    }
}

/** Labeled bar: metric icon + label, value text on the right, bar, optional caption. Home calories / macro averages, food detail. */
@Composable
fun LhMetricBar(
    metric: MetricKind,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    label: String = metric.label,
    color: Color = metric.color(),
    caption: String? = null,
    height: Dp = 8.dp,
) {
    val c = LhTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MetricIcon(metric, 18.dp, tint = color)
            Spacer(Modifier.width(8.dp))
            Text(label, style = LhType.body, color = c.foreground, modifier = Modifier.weight(1f), maxLines = 1)
            Text(value, style = LhType.tnum(LhType.label), color = c.foreground, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        LhProgressBar(fraction, color = color, height = height)
        if (caption != null) Text(caption, style = LhType.captionMuted, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
    }
}
