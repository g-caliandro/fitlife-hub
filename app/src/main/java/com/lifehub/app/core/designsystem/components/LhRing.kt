package com.lifehub.app.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifehub.app.core.designsystem.LhMotion
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.lhTween

private fun clean(p: Float) = if (p.isNaN()) 0f else p

/** Animated progress fraction: first composition sweeps from 0 (700ms), later changes tween 400ms. */
@Composable
internal fun rememberRingProgress(target: Float, delayMs: Int = 0): Float {
    val a = remember { Animatable(if (LhMotion.reduced) target else 0f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(target) {
        if (first) { first = false; a.animateTo(target, lhTween(700, delayMs)) }
        else a.animateTo(target, lhTween(400))
    }
    return a.value
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.ring(progress: Float, stroke: Float, color: Color, track: Color, over: Color, rawProgress: Float) {
    val inset = stroke / 2
    val sz = Size(size.width - stroke, size.height - stroke)
    val tl = Offset(inset, inset)
    drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(stroke))
    if (rawProgress > 1f) {
        drawArc(over, -90f, 360f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    } else if (progress > 0.001f) {
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun LhRing(
    progress: Float, value: String, label: String, modifier: Modifier = Modifier,
    size: Dp = 168.dp, stroke: Dp = 16.dp, color: Color = LhTheme.colors.primary,
    trackColor: Color = LhTheme.colors.track, overColor: Color = LhTheme.colors.warning,
) {
    val c = LhTheme.colors
    val p = clean(progress)
    val anim = rememberRingProgress(p.coerceAtMost(1f))
    // Count-up on the numeric part when the value is an integer string.
    val asInt = value.replace(",", "").toIntOrNull()
    val shown = if (asInt != null) {
        val v by animateIntAsState(asInt, lhTween(700), label = "ringValue")
        if (value.contains(",")) String.format(java.util.Locale.US, "%,d", v) else v.toString()
    } else value
    Box(modifier.size(size).semantics { contentDescription = "$value $label" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            ring(if (p > 1f) 1f else anim, stroke.toPx(), color, trackColor, overColor, p)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(shown, style = if (size < 150.dp) LhType.stat.copy(fontSize = 32.sp, lineHeight = 40.sp) else LhType.display, color = c.foreground, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, style = LhType.body, color = c.mutedForeground, maxLines = 1)
        }
    }
}

@Composable
fun LhMiniRing(progress: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, stroke: Dp = 5.dp, delayMs: Int = 0) {
    val p = clean(progress)
    val anim = rememberRingProgress(p.coerceAtMost(1f), delayMs)
    val over = LhTheme.colors.warning
    val track = LhTheme.colors.track
    Canvas(modifier.size(size)) { ring(if (p > 1f) 1f else anim, stroke.toPx(), color, track, over, p) }
}

@Composable
fun LhMacroRingStat(value: String, label: String, progress: Float, color: Color, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        LhMiniRing(progress, color)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = LhType.tnum(LhType.label), color = c.foreground, maxLines = 1)
            Text(label, style = LhType.caption, color = c.mutedForeground, maxLines = 1)
        }
    }
}
