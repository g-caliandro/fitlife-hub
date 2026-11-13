package com.lifehub.app.domain

import com.lifehub.app.data.model.MacroDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun days(vararg back: Long) = back.map { today.minusDays(it) }.toSet()

    @Test fun countsConsecutiveDaysEndingToday() = assertEquals(3, loggingStreak(days(0, 1, 2, 4), today))
    @Test fun todayEmptyKeepsYesterdaysStreak() = assertEquals(2, loggingStreak(days(1, 2), today))
    @Test fun brokenStreakIsZero() = assertEquals(0, loggingStreak(days(2, 3), today))
    @Test fun emptyIsZero() = assertEquals(0, loggingStreak(emptySet(), today))

    @Test fun macroAveragesOverLoggedDaysOnly() {
        fun m(d: Long, p: Int, c: Int, f: Int) = MacroDay(today.minusDays(d), p, c, f, 4 * p + 4 * c + 9 * f, true, Instant.EPOCH)
        val r = DateRange(today.minusDays(6), today)
        val a = macroAverages(listOf(m(0, 100, 200, 50), m(1, 150, 300, 70), m(10, 999, 999, 999)), r)!!
        assertEquals(125, a.proteinG); assertEquals(250, a.carbsG); assertEquals(60, a.fatG); assertEquals(2, a.days)
        assertNull(macroAverages(emptyList(), r))
    }
}
