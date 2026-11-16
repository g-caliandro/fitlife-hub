package com.lifehub.app.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.domain.DateRange
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

data class LineSeries(val name: String, val points: List<ChartPoint>, val color: Color, val unit: String)

/** One tooltip row: optional colour dot + text. */
data class TooltipRow(val text: String, val color: Color? = null)

/** Two-series convenience wrapper around [LineChart] (both series share one value axis). */
@Composable
fun DualLineChart(
    range: DateRange,
    left: LineSeries,
    right: LineSeries?,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    tooltip: ((LocalDate) -> List<TooltipRow>)? = null,
    initialSelection: LocalDate? = null,
) = LineChart(range, listOfNotNull(left, right), modifier, today, tooltip, initialSelection)

/**
 * Line series on a date axis sharing ONE neutral value axis ([niceTicks] over all series, padded, nice steps).
 * Points sit at their dates and each series is one monotone curve through all of its points (gaps are bridged,
 * missing values never drop to 0).
 * With [tooltip], tapping or dragging selects the nearest date that has data and shows its rows;
 * [initialSelection] preselects a date (screenshot tests).
 */
@Composable
fun LineChart(
    range: DateRange,
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    tooltip: ((LocalDate) -> List<TooltipRow>)? = null,
    initialSelection: LocalDate? = null,
) {
    val c = LhTheme.colors
    val m = rememberChartMeasurer()
    val inRange = series.map { s -> s to s.points.filter { it.date in range }.sortedBy { it.date } }
    val all = inRange.flatMap { it.second }
    val dates = all.map { it.date }.distinct().sorted()
    var selected by remember(range, initialSelection) { mutableStateOf(initialSelection) }
    val first = inRange.firstOrNull()
    val latest = first?.second?.lastOrNull()
    val desc = "${first?.first?.name ?: "Line"} chart, ${range.days.size} days, latest ${latest?.let { String.format(Locale.ENGLISH, "%.1f %s", it.value, first.first.unit) } ?: "none"}"
    val ticks = if (all.isEmpty()) null else niceTicks(all.minOf { it.value }, all.maxOf { it.value })
    val leftPad = 32.dp
    val rightPad = 8.dp
    val days = range.days

    val gestures = if (tooltip == null || dates.isEmpty()) Modifier else Modifier
        .pointerInput(range, dates) {
            val pick = { x: Float -> nearestDate(x, size.width.toFloat(), leftPad.toPx(), rightPad.toPx(), days, dates) }
            detectTapGestures { o -> pick(o.x).let { selected = if (it == selected) null else it } }
        }
        .pointerInput(range, dates) {
            val pick = { x: Float -> nearestDate(x, size.width.toFloat(), leftPad.toPx(), rightPad.toPx(), days, dates) }
            detectHorizontalDragGestures(onDragStart = { selected = pick(it.x) }) { change, _ -> selected = pick(change.position.x) }
        }

    Canvas(modifier.chartSize().then(gestures).semantics { contentDescription = desc }) {
        val plot = plotArea(leftPad, rightPad)
        drawXLabels(m, range, plot, c.mutedForeground, c.foreground, today)
        if (ticks == null) {
            drawGrid(plot, c.border)
            drawNoData(m, plot, c.mutedForeground, c.card)
            return@Canvas
        }
        val span = ticks.hi - ticks.lo
        fun yOf(v: Double) = plot.bottom - ((v - ticks.lo) / span).toFloat() * plot.height
        ticks.values.forEach { v ->
            val y = yOf(v)
            drawLine(c.border, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
            drawLabel(m, ticks.format(v), labelStyle(c.mutedForeground), plot.left - 4.dp.toPx(), y, Align.End)
        }
        val slot = plot.width / days.size
        fun xOf(d: LocalDate) = plot.left + slot * (days.indexOf(d) + 0.5f)
        val sel = selected?.takeIf { it in dates && tooltip != null }
        if (sel != null) drawLine(c.mutedForeground.copy(alpha = 0.5f), Offset(xOf(sel), plot.top), Offset(xOf(sel), plot.bottom), 1.dp.toPx())

        inRange.forEach { (s, pts) ->
            if (pts.isEmpty()) return@forEach
            val xy = pts.map { p -> Offset(xOf(p.date), yOf(p.value)) }
            if (xy.size > 1) {
                val path = Path()
                monotonePath(path, xy)
                drawPath(path, s.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            pts.zip(xy).forEach { (p, o) ->
                val big = p.date == sel
                drawCircle(c.card, (if (big) 7 else 5).dp.toPx(), o)
                drawCircle(s.color, (if (big) 5f else 3.5f).dp.toPx(), o)
            }
        }

        if (sel != null && tooltip != null) {
            val pad = 8.dp.toPx(); val dot = 6.dp.toPx(); val gap = 6.dp.toPx(); val lineGap = 2.dp.toPx()
            val title = m.measure(fmtDate(sel, sel.year), labelStyle(c.foreground, bold = true, size = 11), maxLines = 1)
            val rows = tooltip(sel).map { it to m.measure(it.text, labelStyle(c.foreground, size = 11), maxLines = 1) }
            val w = 2 * pad + maxOf(title.size.width.toFloat(), rows.maxOfOrNull { it.second.size.width + dot + gap } ?: 0f)
            val h = 2 * pad + title.size.height + rows.sumOf { it.second.size.height } + lineGap * rows.size
            val x = xOf(sel)
            // Beside the guide line, kept inside the plot so it never covers the axis labels.
            val bx = (if (x + 8.dp.toPx() + w <= plot.right) x + 8.dp.toPx() else x - 8.dp.toPx() - w)
                .coerceIn(plot.left, (plot.right - w).coerceAtLeast(plot.left))
            val by = plot.top
            val r = CornerRadius(8.dp.toPx())
            drawRoundRect(c.card, Offset(bx, by), Size(w, h), r)
            drawRoundRect(c.border, Offset(bx, by), Size(w, h), r, style = Stroke(1.dp.toPx()))
            var y = by + pad
            drawText(title, topLeft = Offset(bx + pad, y))
            y += title.size.height
            rows.forEach { (row, layout) ->
                y += lineGap
                row.color?.let { drawCircle(it, dot / 2, Offset(bx + pad + dot / 2, y + layout.size.height / 2f)) }
                drawText(layout, topLeft = Offset(bx + pad + dot + gap, y))
                y += layout.size.height
            }
        }
    }
}

/** Date in [dates] whose slot centre is closest to [x] (same geometry as [plotArea]). */
private fun nearestDate(x: Float, width: Float, leftPx: Float, rightPx: Float, days: List<LocalDate>, dates: List<LocalDate>): LocalDate {
    val slot = (width - rightPx - leftPx) / days.size
    return dates.minBy { abs(leftPx + slot * (days.indexOf(it) + 0.5f) - x) }
}
