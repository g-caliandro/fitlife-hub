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

/**
 * Two independently scaled series on a date axis: left axis = [left], right axis = [right] (optional), axis labels
 * tinted with their series colour. Points sit at their dates and each series is one monotone curve through all of
 * its points (gaps are bridged, missing values never drop to 0).
 * With [tooltip], tapping or dragging selects the nearest date that has data and shows its rows;
 * [initialSelection] preselects a date (screenshot tests).
 */
@Composable
fun DualLineChart(
    range: DateRange,
    left: LineSeries,
    right: LineSeries?,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    tooltip: ((LocalDate) -> List<TooltipRow>)? = null,
    initialSelection: LocalDate? = null,
) {
    val c = LhTheme.colors
    val m = rememberChartMeasurer()
    val lp = left.points.filter { it.date in range }.sortedBy { it.date }
    val rp = right?.points?.filter { it.date in range }?.sortedBy { it.date }.orEmpty()
    val dates = (lp + rp).map { it.date }.distinct().sorted()
    var selected by remember(range, initialSelection) { mutableStateOf(initialSelection) }
    val latest = lp.lastOrNull()
    val desc = "${left.name} chart, ${range.days.size} days, latest ${latest?.let { String.format(Locale.ENGLISH, "%.1f %s", it.value, left.unit) } ?: "none"}"
    val hasRight = right != null && rp.isNotEmpty()
    val rightPad = if (hasRight) 32.dp else 8.dp
    val days = range.days

    val gestures = if (tooltip == null || dates.isEmpty()) Modifier else Modifier
        .pointerInput(range, dates, hasRight) {
            val pick = { x: Float -> nearestDate(x, size.width.toFloat(), 32.dp.toPx(), rightPad.toPx(), days, dates) }
            detectTapGestures { o -> pick(o.x).let { selected = if (it == selected) null else it } }
        }
        .pointerInput(range, dates, hasRight) {
            val pick = { x: Float -> nearestDate(x, size.width.toFloat(), 32.dp.toPx(), rightPad.toPx(), days, dates) }
            detectHorizontalDragGestures(onDragStart = { selected = pick(it.x) }) { change, _ -> selected = pick(change.position.x) }
        }

    Canvas(modifier.chartSize().then(gestures).semantics { contentDescription = desc }) {
        val plot = plotArea(32.dp, rightPad)
        drawGrid(plot, c.border)
        drawXLabels(m, range, plot, c.mutedForeground, c.foreground, today)
        if (lp.isEmpty() && rp.isEmpty()) {
            drawNoData(m, plot, c.mutedForeground, c.card)
            return@Canvas
        }
        val slot = plot.width / days.size
        fun xOf(d: LocalDate) = plot.left + slot * (days.indexOf(d) + 0.5f)
        val sel = selected?.takeIf { it in dates && tooltip != null }
        if (sel != null) drawLine(c.mutedForeground.copy(alpha = 0.5f), Offset(xOf(sel), plot.top), Offset(xOf(sel), plot.bottom), 1.dp.toPx())

        fun drawSeries(pts: List<ChartPoint>, color: Color, axisLeft: Boolean) {
            if (pts.isEmpty()) return
            val (lo, hi) = paddedRange(pts.minOf { it.value }, pts.maxOf { it.value })
            val span = hi - lo
            for (i in 0..2) {
                val txt = String.format(Locale.ENGLISH, "%.1f", lo + span * i / 2)
                val y = plot.bottom - plot.height * i / 2f
                if (axisLeft) drawLabel(m, txt, labelStyle(color), plot.left - 4.dp.toPx(), y, Align.End)
                else drawLabel(m, txt, labelStyle(color), plot.right + 4.dp.toPx(), y, Align.Start)
            }
            val xy = pts.map { p -> Offset(xOf(p.date), plot.bottom - ((p.value - lo) / span).toFloat() * plot.height) }
            if (xy.size > 1) {
                val path = Path()
                monotonePath(path, xy)
                drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            pts.zip(xy).forEach { (p, o) ->
                val big = p.date == sel
                drawCircle(c.card, (if (big) 7 else 5).dp.toPx(), o)
                drawCircle(color, (if (big) 5f else 3.5f).dp.toPx(), o)
            }
        }
        drawSeries(lp, left.color, true)
        if (right != null) drawSeries(rp, right.color, false)

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
