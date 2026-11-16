package com.lifehub.app.core.designsystem.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Plain number: "12", "12.5". */
fun wheelNumber(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

/**
 * Compact vertical wheel (iOS / Android number-picker style): three rows visible, the selected one centred on a
 * highlighted pill. Snaps to a row, ticks the haptics on each row while scrolling, a tap on a neighbour selects it.
 * [values] must be sorted ascending; a [value] that is not in the list is shown exactly in the nearest row until the
 * wheel is moved.
 */
@Composable
fun LhWheelPicker(
    values: List<Double>,
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    format: (Double) -> String = ::wheelNumber,
    rowHeight: Dp = 40.dp,
) {
    val c = LhTheme.colors
    fun nearest(v: Double): Int {
        var best = 0
        for (i in values.indices) if (abs(values[i] - v) < abs(values[best] - v)) best = i
        return best
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = nearest(value))
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val centered by remember(values) {
        derivedStateOf { (state.firstVisibleItemIndex + (state.firstVisibleItemScrollOffset / rowPx).roundToInt()).coerceIn(0, values.lastIndex) }
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onValueChange)
    val current by rememberUpdatedState(value)
    val list by rememberUpdatedState(values)

    // Wheel -> value: every row passing the centre while the user scrolls (with a haptic tick).
    LaunchedEffect(state) {
        snapshotFlow { centered }.distinctUntilChanged().collect { i ->
            if (state.isScrollInProgress && nearest(current) != i) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onChange(list[i])
            }
        }
    }
    // Value -> wheel (prefill, another set, accessibility).
    LaunchedEffect(value, values) {
        val i = nearest(value)
        if (!state.isScrollInProgress && centered != i) state.scrollToItem(i)
    }

    fun text(v: Double) = format(v) + (unit?.let { " $it" } ?: "")
    val exactHere = nearest(value)
    Column(
        modifier.testTag("picker-$label").semantics {
            contentDescription = "$label ${text(value)}"
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), values.first().toFloat()..values.last().toFloat())
            setProgress { t -> onChange(values[nearest(t.toDouble())]); true }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = LhType.labelSmall, color = c.mutedForeground, modifier = Modifier.padding(bottom = 4.dp))
        Box(Modifier.fillMaxWidth().height(rowHeight * 3)) {
            // Selection pill behind the centre row.
            Box(Modifier.fillMaxWidth().height(rowHeight).align(Alignment.Center).background(c.primary.copy(alpha = 0.12f), LhRadius.fullShape))
            LazyColumn(
                state = state, flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
                contentPadding = PaddingValues(vertical = rowHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.4f to Color.Black, 0.6f to Color.Black, 1f to Color.Black.copy(alpha = 0.25f)),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                itemsIndexed(values) { i, v ->
                    val selected = i == centered
                    Box(
                        Modifier.fillMaxWidth().height(rowHeight).clickable(remember { MutableInteractionSource() }, null) {
                            if (!selected) {
                                scope.launch { state.animateScrollToItem(i) }
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onChange(v)
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            val shown = if (exactHere == i) value else v
                            Text(
                                buildAnnotatedString {
                                    append(format(shown))
                                    if (unit != null) withStyle(SpanStyle(fontSize = LhType.body.fontSize, color = c.mutedForeground)) { append(" $unit") }
                                },
                                style = LhType.tnum(LhType.stat), color = c.foreground, maxLines = 1,
                                modifier = Modifier.testTag("picker-value-$label"),
                            )
                        } else {
                            Text(format(v), style = LhType.tnum(LhType.bodyLarge), color = c.mutedForeground, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
