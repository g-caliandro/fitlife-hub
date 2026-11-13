package com.lifehub.app.domain

import com.lifehub.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DayNutritionTest {
    private val d1 = LocalDate.of(2026, 11, 10)
    private val d2 = LocalDate.of(2026, 11, 11)
    private val t = Instant.parse("2026-11-10T10:00:00Z")
    private fun e(id: String, d: LocalDate, g: Double, kcal: Double, p: Double, c: Double, f: Double, at: Instant = t) =
        FoodEntry(id, d, "f", id, null, g, kcal, p, c, f, at)

    @Test fun macrosForScalesPer100g() {
        val food = Food("f", FoodSource.CUSTOM, null, "Chicken", null, 165.0, 31.0, 0.0, 3.6, null, false, null)
        val m = food.macrosFor(150.0)
        assertEquals(247.5, m.kcal, 1e-9); assertEquals(46.5, m.proteinG, 1e-9); assertEquals(5.4, m.fatG, 1e-9)
    }

    @Test fun mergeSumsEntriesAndQuickAdd() {
        val quick = MacroDay(d1, 20, 30, 5, 245, true, t)
        val days = mergeNutrition(
            listOf(quick, MacroDay(d2, 100, 200, 50, 1650, true, t)),
            listOf(e("a", d1, 100.0, 300.0, 25.4, 10.0, 8.0), e("b", d1, 50.0, 100.0, 5.2, 12.0, 1.0, t.plusSeconds(5))),
        )
        assertEquals(listOf(d1, d2), days.map { it.date })
        val x = days[0]
        assertEquals(2, x.entries.size); assertEquals(quick, x.quickAdd)
        assertEquals(645.0, x.totals.kcal, 1e-9); assertEquals(50.6, x.totals.proteinG, 1e-9)
        val md = x.asMacroDay()
        assertEquals(51, md.proteinG); assertEquals(645, md.kcal)
        assertTrue(days[1].entries.isEmpty()); assertEquals(100, days[1].asMacroDay().proteinG)
    }

    @Test fun foodOnlyDayAppearsAndEmptyIsAbsent() {
        val days = mergeNutrition(emptyList(), listOf(e("a", d2, 100.0, 50.0, 3.0, 4.0, 1.0)))
        assertEquals(1, days.size); assertNull(days[0].quickAdd); assertEquals(3, days[0].totals.proteinInt)
        assertTrue(mergeNutrition(emptyList(), emptyList()).isEmpty())
    }

    @Test fun insightsUseTotals() {
        val days = mergeNutrition(
            listOf(MacroDay(d1, 30, 0, 0, 120, true, t)),
            listOf(e("a", d1, 100.0, 400.0, 110.0, 0.0, 0.0), e("b", d2, 100.0, 400.0, 150.0, 0.0, 0.0)),
        ).asMacroDays()
        val r = DateRange(d1, d2)
        assertEquals(145, avgProtein(days, r))        // (140 + 150) / 2
        assertEquals(1, daysOnTarget(days, r, 150).first)
        assertEquals(2, daysOnTarget(days, r, 150).second)
    }
}
