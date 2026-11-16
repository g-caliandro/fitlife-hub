package com.lifehub.app.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.domain.DateRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartPoint(val date: LocalDate, val value: Double)

/** Smallest of {1,1.5,2,2.5,3,4,5,6,8,10}x10^n that is >= [v] (tight top so bars use the height); 1.0 for non-positive input. */
fun niceMax(v: Double): Double {
    if (v <= 0.0 || v.isNaN()) return 1.0
    val exp = floor(log10(v))
    val base = 10.0.pow(exp)
    for (m in listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)) if (m * base >= v - 1e-9) return m * base
    return 10 * base
}

internal val ChartHeight: Dp = 180.dp

internal class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

internal fun Modifier.chartSize(): Modifier = this.fillMaxWidth().height(ChartHeight)

internal fun DrawScope.plotArea(leftDp: Dp = 32.dp, rightDp: Dp = 8.dp): PlotArea =
    PlotArea(leftDp.toPx(), 12.dp.toPx(), size.width - rightDp.toPx(), size.height - 20.dp.toPx())

internal fun DrawScope.drawGrid(plot: PlotArea, color: Color, lines: Int = 3) {
    for (i in 0 until lines) {
        val y = plot.bottom - plot.height * i / (lines - 1)
        drawLine(color, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
    }
}

internal fun DrawScope.drawLabel(
    m: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, align: Align = Align.Center,
): TextLayoutResult {
    val r = m.measure(text, style, maxLines = 1)
    val ox = when (align) { Align.Start -> x; Align.Center -> x - r.size.width / 2f; Align.End -> x - r.size.width }
    drawText(r, topLeft = Offset(ox, y - r.size.height / 2f))
    return r
}

internal enum class Align { Start, Center, End }

internal fun labelStyle(color: Color, bold: Boolean = false, size: Int = 10) =
    TextStyle(fontFamily = com.lifehub.app.core.designsystem.Poppins, fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)

/** True for Saturday and Sunday. */
internal fun LocalDate.isWeekend(): Boolean = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

/**
 * Which day indexes get an x label. Up to 7 days: all (weekday letters). Up to 14: every other day from the first
 * (periods start on Monday, so Mon/Wed/Fri/Sun). Longer: Mondays and Thursdays. Today is added when it does not
 * crowd a neighbouring label.
 */
internal fun xLabelIndexes(days: List<LocalDate>, today: LocalDate?): Set<Int> {
    val n = days.size
    val base = days.indices.filter { i ->
        when {
            n <= 7 -> true
            n <= 14 -> i % 2 == 0
            else -> days[i].dayOfWeek == DayOfWeek.MONDAY || days[i].dayOfWeek == DayOfWeek.THURSDAY
        }
    }.toMutableSet()
    val t = today?.let { days.indexOf(it) } ?: -1
    if (t >= 0 && t !in base && (t - 1) !in base && (t + 1) !in base) base += t
    return base
}

/**
 * X labels: 1W "M T W T F S S", 2W day-of-month every other day, 4W day-of-month on Mondays and Thursdays
 * (see [xLabelIndexes]). Today is bold; with [weekendColor], Saturday/Sunday labels use that tone.
 */
internal fun DrawScope.drawXLabels(
    m: TextMeasurer, range: DateRange, plot: PlotArea, muted: Color, strong: Color, today: LocalDate?,
    weekendColor: Color? = null,
) {
    val days = range.days
    val slot = plot.width / days.size
    val letters = "MTWTFSS"
    val shown = xLabelIndexes(days, today)
    days.forEachIndexed { i, d ->
        if (i !in shown) return@forEachIndexed
        val isToday = d == today
        val t = if (days.size <= 7) letters[d.dayOfWeek.value - 1].toString() else d.dayOfMonth.toString()
        val col = when {
            isToday -> strong
            weekendColor != null && d.isWeekend() -> weekendColor
            else -> muted
        }
        drawLabel(m, t, labelStyle(col, isToday, 11), plot.left + slot * (i + 0.5f), plot.bottom + 10.dp.toPx())
    }
}

/**
 * Subtle shaded columns behind each weekend (Sat+Sun merged into one band), running from the top of the plot down
 * through the x-label row so the weekend labels sit inside them.
 */
internal fun DrawScope.drawWeekendBands(range: DateRange, plot: PlotArea, color: Color) {
    val days = range.days
    val slot = plot.width / days.size
    val inset = 1.dp.toPx()
    val r = CornerRadius(4.dp.toPx())
    var i = 0
    while (i < days.size) {
        if (!days[i].isWeekend()) { i++; continue }
        var j = i
        while (j + 1 < days.size && days[j + 1].isWeekend()) j++
        val left = plot.left + slot * i + inset
        val right = plot.left + slot * (j + 1) - inset
        drawRoundRect(color, Offset(left, plot.top - 4.dp.toPx()), Size(right - left, size.height - plot.top + 4.dp.toPx()), r)
        i = j + 1
    }
}

/** Shared value axis: [lo]..[hi] in steps of [step] (lo and hi are multiples of step). */
data class AxisTicks(val lo: Double, val hi: Double, val step: Double) {
    val values: List<Double> get() = (0..((hi - lo) / step + 1e-6).toInt()).map { lo + it * step }

    /** Fraction digits needed to print every tick exactly (0..2). */
    val decimals: Int get() = (0..2).firstOrNull { d -> val v = step * 10.0.pow(d); abs(v - v.roundToLong()) < 1e-6 } ?: 2

    fun format(v: Double): String = String.format(Locale.ENGLISH, "%.${decimals}f", v)
}

/**
 * Nice axis covering [min]..[max] with some padding (3 % of the span, at least 0.25, plus the snap to the step) and at most [maxIntervals]
 * intervals; steps are 1, 2, 2.5 or 5 x 10^n and the ends snap to multiples of the step.
 */
fun niceTicks(min: Double, max: Double, maxIntervals: Int = 5): AxisTicks {
    // Small inner padding keeps points off the frame; snapping the ends to the step adds the rest.
    val pad = maxOf((max - min) * 0.03, 0.25)
    val lo = min - pad
    val hi = max + pad
    var exp = floor(log10((hi - lo) / maxIntervals)).toInt()
    while (true) {
        for (m in listOf(1.0, 2.0, 2.5, 5.0)) {
            val step = m * 10.0.pow(exp)
            val a = floor(lo / step + 1e-9)
            val b = ceil(hi / step - 1e-9)
            if (b - a <= maxIntervals) return AxisTicks(a * step, b * step, step)
        }
        exp++
    }
}

internal fun DrawScope.drawNoData(m: TextMeasurer, plot: PlotArea, color: Color, bg: Color) {
    val style = labelStyle(color).copy(fontSize = 12.sp)
    val r = m.measure("No data", style, maxLines = 1)
    val cx = (plot.left + plot.right) / 2; val cy = (plot.top + plot.bottom) / 2
    val pad = 8.dp.toPx()
    // knock out the grid line behind the label
    drawRect(bg, Offset(cx - r.size.width / 2f - pad, cy - r.size.height / 2f), androidx.compose.ui.geometry.Size(r.size.width + 2 * pad, r.size.height.toFloat()))
    drawLabel(m, "No data", style, cx, cy)
}

@Composable
internal fun chartColors() = LhTheme.colors

@Composable
internal fun rememberChartMeasurer(): TextMeasurer = rememberTextMeasurer()


/**
 * Fritsch-Carlson tangents (slopes dy/dx) for a monotone cubic Hermite spline through [pts] (x strictly increasing).
 * Each segment stays within its endpoints' y values, so the curve never overshoots the data.
 */
internal fun monotoneTangents(pts: List<Offset>): FloatArray {
    val n = pts.size
    if (n < 2) return FloatArray(n)
    val d = FloatArray(n - 1) { (pts[it + 1].y - pts[it].y) / (pts[it + 1].x - pts[it].x) }
    val m = FloatArray(n)
    m[0] = d[0]; m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]; val b = m[i + 1] / d[i]
        val h = kotlin.math.hypot(a, b)
        if (h > 3f) { val t = 3f / h; m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
    }
    return m
}

/** Monotone cubic path through [pts] (see [monotoneTangents]); appended to [path] starting with moveTo. */
internal fun monotonePath(path: Path, pts: List<Offset>) {
    if (pts.isEmpty()) return
    path.moveTo(pts[0].x, pts[0].y)
    val m = monotoneTangents(pts)
    for (i in 0 until pts.size - 1) {
        val dx = (pts[i + 1].x - pts[i].x) / 3f
        path.cubicTo(pts[i].x + dx, pts[i].y + m[i] * dx, pts[i + 1].x - dx, pts[i + 1].y - m[i + 1] * dx, pts[i + 1].x, pts[i + 1].y)
    }
}

@Composable
internal fun rememberReveal(durationMs: Int): Float {
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (com.lifehub.app.core.designsystem.LhMotion.reduced) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { a.animateTo(1f, com.lifehub.app.core.designsystem.lhTween(durationMs, 0, androidx.compose.animation.core.LinearEasing)) }
    return a.value
}
