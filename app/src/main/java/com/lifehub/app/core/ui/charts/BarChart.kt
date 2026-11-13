package com.lifehub.app.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.domain.DateRange
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Daily bar chart (one slot per day). Y axis from 0. Bars at/above [targetLine] use [aboveTargetColor] if given.
 * [today] only affects x-label emphasis.
 */
@Composable
fun BarChart(
    range: DateRange,
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    barColor: Color = LhTheme.colors.primary,
    targetLine: Double? = null,
    targetLabel: String? = null,
    valueFormatter: (Double) -> String = { it.roundToInt().toString() },
    aboveTargetColor: Color? = null,
    today: LocalDate? = null,
    name: String = "Bar chart",
) {
    val c = LhTheme.colors
    val m = rememberChartMeasurer()
    val inRange = points.filter { it.date in range }
    val latest = inRange.maxByOrNull { it.date }
    val desc = "$name, ${range.days.size} days, latest ${latest?.let { valueFormatter(it.value) } ?: "none"}"
    val grow = rememberReveal(500 + 25 * range.days.size)
    val totalMs = 500f + 25f * range.days.size
    Canvas(modifier.chartSize().semantics { contentDescription = desc }) {
        val plot = plotArea(32.dp, 8.dp)
        val dataMax = max(inRange.maxOfOrNull { it.value } ?: 0.0, targetLine ?: 0.0)
        val top = niceMax(dataMax)
        drawGrid(plot, c.border)
        val muted = c.mutedForeground
        if (inRange.isNotEmpty() || targetLine != null) {
            listOf(0.0, top / 2, top).forEachIndexed { i, v ->
                val y = plot.bottom - plot.height * i / 2f
                drawLabel(m, valueFormatter(v), labelStyle(muted), plot.left - 4.dp.toPx(), y, Align.End)
            }
        }
        drawXLabels(m, range, plot, muted, c.foreground, today)
        val days = range.days
        val slot = plot.width / days.size
        val bw = min(slot * 0.56f, 20.dp.toPx())
        val byDay = inRange.associateBy { it.date }
        if (inRange.isNotEmpty()) days.forEachIndexed { i, d ->
            val p = byDay[d]
            val x = plot.left + slot * (i + 0.5f) - bw / 2
            val barT = ((grow * totalMs - 25f * i) / 500f).coerceIn(0f, 1f)
            if (p == null || p.value <= 0) {
                val h = 4.dp.toPx()
                drawRoundRect(c.track, Offset(x, plot.bottom - h), androidx.compose.ui.geometry.Size(bw, h), CornerRadius(bw / 2, bw / 2))
                return@forEachIndexed
            }
            val h = max((p.value / top).toFloat() * plot.height * barT, 4.dp.toPx())
            val col = if (aboveTargetColor != null && targetLine != null && p.value >= targetLine) aboveTargetColor else barColor
            val rr = min(bw / 2, h)
            val path = Path().apply {
                addRoundRect(RoundRect(x, plot.bottom - h, x + bw, plot.bottom, CornerRadius(rr, rr), CornerRadius(rr, rr), CornerRadius.Zero, CornerRadius.Zero))
            }
            drawPath(path, col)
        }
        if (inRange.isEmpty()) drawNoData(m, plot, muted, c.card)
        if (targetLine != null) {
            val y = plot.bottom - (targetLine / top).toFloat() * plot.height
            val tcol = c.foreground.copy(alpha = 0.55f)
            drawLine(
                tcol, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
            if (targetLabel != null) drawLabel(m, targetLabel, labelStyle(muted, size = 12), plot.right, y - 9.dp.toPx(), Align.End)
        }
    }
}
