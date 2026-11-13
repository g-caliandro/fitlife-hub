package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private val TICK = 12.dp

/**
 * Vertical scroll ruler ("wheel") for a number: drag up/down, it snaps to [step]; the value under the centre line
 * is shown big on top. Minor tick every step, long labelled tick every [labelEvery] steps.
 * External [value] changes (new set prefilled) scroll the ruler without animation. A value that is not on the [step]
 * grid (e.g. a 32 s target on a 5 s ruler) is shown exactly until the ruler is moved.
 */
@Composable
fun LhRuler(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double = 1.0,
    labelEvery: Int = 5,
    unit: String? = null,
    format: (Double) -> String = { val r = Math.round(it * 100) / 100.0; if (r % 1.0 == 0.0) r.toLong().toString() else r.toString() },
    display: (Double) -> String = format,
    /** Ruler window height; null = fill the remaining height of [modifier] (which must then be bounded). */
    height: Dp? = 196.dp,
) {
    val c = LhTheme.colors
    val count = ((max - min) / step).roundToInt() + 1
    fun indexOf(v: Double) = ((v.coerceIn(min, max) - min) / step).roundToInt()
    fun valueAt(i: Int) = min + i.coerceIn(0, count - 1) * step
    val state = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(value))
    val tickPx = with(LocalDensity.current) { TICK.toPx() }
    val centered by remember { derivedStateOf { state.firstVisibleItemIndex + (state.firstVisibleItemScrollOffset / tickPx).roundToInt() } }
    val haptics = LocalHapticFeedback.current
    val onChange by rememberUpdatedState(onValueChange)
    val current by rememberUpdatedState(value)

    // Ruler -> value (every snapped tick while dragging, with a light haptic tick).
    LaunchedEffect(state) {
        snapshotFlow { centered }.distinctUntilChanged().collect { i ->
            if (indexOf(current) != i) {
                if (state.isScrollInProgress) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(valueAt(i))
            }
        }
    }
    // Value -> ruler (prefill / another set).
    LaunchedEffect(value) {
        val i = indexOf(value)
        if (!state.isScrollInProgress && centered != i) state.scrollToItem(i)
    }

    val shown = if (indexOf(value) == centered) value.coerceIn(min, max) else valueAt(centered)
    Column(
        modifier.testTag("ruler-$label").background(c.card, LhRadius.cardShape).border(1.dp, c.border, LhRadius.cardShape).padding(top = 10.dp)
            .semantics {
                contentDescription = "$label ${display(shown)}${unit?.let { " $it" }.orEmpty()}"
                progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), min.toFloat()..max.toFloat(), count - 2)
                setProgress { t -> onChange(valueAt(indexOf(t.toDouble()))); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = LhType.labelSmall, color = c.mutedForeground)
        Text(
            display(shown) + (unit?.let { " $it" } ?: ""), style = LhType.tnum(LhType.stat), color = c.foreground,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.testTag("ruler-value-$label"),
        )
        BoxWithConstraints(Modifier.fillMaxWidth().then(if (height != null) Modifier.height(height) else Modifier.weight(1f))) {
            val pad = (maxHeight - TICK) / 2
            LazyColumn(
                state = state, flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
                contentPadding = PaddingValues(vertical = pad),
                modifier = Modifier.fillMaxSize()
                    // Fade ticks out towards the top and bottom edges.
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black, 0.65f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                items(count) { i -> Tick(i, labelEvery, format(valueAt(i))) }
            }
            // Centre marker.
            Canvas(Modifier.fillMaxWidth().height(TICK).align(Alignment.Center)) {
                val y = size.height / 2
                // Stops short of the label column so it never runs into a 2-3 digit tick label.
                drawLine(c.primary, Offset(size.width * 0.18f, y), Offset(size.width * 0.62f, y), 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun Tick(i: Int, labelEvery: Int, text: String) {
    val c = LhTheme.colors
    val major = i % labelEvery == 0
    val half = labelEvery % 2 == 0 && i % (labelEvery / 2) == 0
    Box(Modifier.fillMaxWidth().height(TICK)) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val w = size.width * when { major -> 0.30f; half -> 0.20f; else -> 0.12f }
            val x0 = size.width * 0.18f
            drawLine(if (major) c.foreground.copy(alpha = 0.7f) else c.mutedForeground.copy(alpha = 0.5f), Offset(x0, y), Offset(x0 + w, y), (if (major) 2 else 1).dp.toPx(), StrokeCap.Round)
        }
        if (major) Text(
            text, style = LhType.caption, color = c.mutedForeground, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).wrapContentHeight(unbounded = true),
        )
    }
}
