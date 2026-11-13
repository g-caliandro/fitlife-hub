package com.lifehub.app.domain

import com.lifehub.app.data.model.HomePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class PeriodTest {
    private val wed = LocalDate.of(2026, 11, 11)   // Wednesday

    @Test fun weekStartIsMonday() {
        assertEquals(LocalDate.of(2026, 11, 9), Periods.weekStart(wed))
        assertEquals(LocalDate.of(2026, 11, 9), Periods.weekStart(LocalDate.of(2026, 11, 9)))
        assertEquals(LocalDate.of(2026, 11, 9), Periods.weekStart(LocalDate.of(2026, 11, 15)))
    }

    @Test fun oneWeekPeriods() {
        val r0 = Periods.period(wed, HomePeriod.ONE_WEEK, 0)
        assertEquals(DateRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)), r0)
        val r1 = Periods.period(wed, HomePeriod.ONE_WEEK, 1)
        assertEquals(DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)), r1)
        assertEquals(r1, Periods.previous(r0))
    }

    @Test fun twoWeekPeriods() {
        val r0 = Periods.period(wed, HomePeriod.TWO_WEEKS, 0)
        assertEquals(DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 15)), r0)
        val r1 = Periods.period(wed, HomePeriod.TWO_WEEKS, 1)
        assertEquals(DateRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 1)), r1)
        assertEquals(r1, Periods.previous(r0))
        assertEquals(14, r0.days.size)
    }

    @Test fun contains() {
        val r = Periods.period(wed, HomePeriod.ONE_WEEK, 0)
        assertTrue(LocalDate.of(2026, 11, 9) in r)
        assertTrue(LocalDate.of(2026, 11, 15) in r)
        assertTrue(LocalDate.of(2026, 11, 16) !in r)
    }

    @Test fun monthGrid() {
        val nov = Periods.monthGrid(YearMonth.of(2026, 11))   // Nov 1 2026 is Sunday -> starts Mon Oct 26, 6 weeks
        assertEquals(42, nov.size)
        assertEquals(DayOfWeek.MONDAY, nov.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 26), nov.first())
        val feb = Periods.monthGrid(YearMonth.of(2027, 2))    // Feb 1 2027 Monday, 28 days -> 35
        assertEquals(35, feb.size)
        assertEquals(LocalDate.of(2027, 2, 1), feb.first())
    }
}
