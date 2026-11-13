package com.lifehub.app.domain

import com.lifehub.app.data.model.MacroDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 11, 12)


    @Test fun macroAveragesOverLoggedDaysOnly() {
        fun m(d: Long, p: Int, c: Int, f: Int) = MacroDay(today.minusDays(d), p, c, f, 4 * p + 4 * c + 9 * f, true, Instant.EPOCH)
        val r = DateRange(today.minusDays(6), today)
        val a = macroAverages(listOf(m(0, 100, 200, 50), m(1, 150, 300, 70), m(10, 999, 999, 999)), r)!!
        assertEquals(125, a.proteinG); assertEquals(250, a.carbsG); assertEquals(60, a.fatG); assertEquals(2, a.days)
        assertNull(macroAverages(emptyList(), r))
    }
}
