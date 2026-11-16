package com.lifehub.app.domain

import com.lifehub.app.data.model.HomePeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(d: LocalDate): Boolean = !d.isBefore(start) && !d.isAfter(end)
    val days: List<LocalDate>
        get() = (0..ChronoUnit.DAYS.between(start, end)).map { start.plusDays(it) }
}

object Periods {
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** offset 0 = current period; k = k periods back. */
    fun period(today: LocalDate, p: HomePeriod, offset: Int): DateRange = period(today, p.days, offset)

    /** Period of [len] days (a multiple of 7) ending on the Sunday of today's week; offset k = k periods back. */
    fun period(today: LocalDate, len: Int, offset: Int): DateRange {
        val start = weekStart(today).minusDays((len - 7).toLong()).minusDays(offset.toLong() * len)
        return DateRange(start, start.plusDays(len - 1L))
    }

    fun previous(r: DateRange): DateRange {
        val len = ChronoUnit.DAYS.between(r.start, r.end) + 1
        return DateRange(r.start.minusDays(len), r.start.minusDays(1))
    }

    /** Monday-aligned grid covering the month: 35 or 42 days (28-day Feb starting Monday yields 35). */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val first = weekStart(month.atDay(1))
        val last = month.atEndOfMonth()
        val weeks = (ChronoUnit.DAYS.between(first, last) / 7 + 1).toInt()
        val count = if (weeks <= 5) 35 else 42
        return (0 until count).map { first.plusDays(it.toLong()) }
    }
}
