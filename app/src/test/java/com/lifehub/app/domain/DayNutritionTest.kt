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

    private fun q(id: String, d: LocalDate, kcal: Int, p: Int?, c: Int?, f: Int?, at: Instant = t, name: String? = null) =
        QuickAddEntry(id, d, name, kcal, p, c, f, false, at)

    @Test fun mergeSumsEntriesAndQuickAdds() {
        val quick = q("q1", d1, 245, 20, 30, 5)
        val quick2 = q("q2", d1, 300, null, null, null, t.plusSeconds(9), "Snack")
        val days = mergeNutrition(
            listOf(quick2, quick, q("q3", d2, 1650, 100, 200, 50)),
            listOf(e("a", d1, 100.0, 300.0, 25.4, 10.0, 8.0), e("b", d1, 50.0, 100.0, 5.2, 12.0, 1.0, t.plusSeconds(5))),
        )
        assertEquals(listOf(d1, d2), days.map { it.date })
        val x = days[0]
        assertEquals(2, x.entries.size); assertEquals(listOf(quick, quick2), x.quickAdds)
        // kcal: everything; protein: only the known values (the n/a snack adds nothing).
        assertEquals(945.0, x.totals.kcal, 1e-9); assertEquals(50.6, x.totals.proteinG, 1e-9)
        val md = x.asMacroDay()
        assertEquals(51, md.proteinG); assertEquals(945, md.kcal)
        assertTrue(md.proteinKnown); assertFalse(md.proteinComplete); assertFalse(md.proteinExact)
        assertTrue(days[1].entries.isEmpty()); assertEquals(100, days[1].asMacroDay().proteinG); assertTrue(days[1].asMacroDay().proteinExact)
    }

    @Test fun kcalOnlyDayHasUnknownMacros() {
        val md = mergeNutrition(listOf(q("a", d1, 800, null, null, null), q("b", d1, 200, null, 30, null)), emptyList()).single().asMacroDay()
        assertEquals(1000, md.kcal); assertEquals(0, md.proteinG)
        assertFalse(md.proteinKnown); assertFalse(md.fatKnown)
        assertTrue(md.carbsKnown); assertFalse(md.carbsComplete); assertEquals(30, md.carbsG)
    }

    @Test fun foodOnlyDayAppearsAndEmptyIsAbsent() {
        val days = mergeNutrition(emptyList(), listOf(e("a", d2, 100.0, 50.0, 3.0, 4.0, 1.0)))
        assertEquals(1, days.size); assertTrue(days[0].quickAdds.isEmpty()); assertEquals(3, days[0].totals.proteinInt)
        assertTrue(days[0].asMacroDay().proteinExact)
        assertTrue(mergeNutrition(emptyList(), emptyList()).isEmpty())
    }

    @Test fun insightsUseTotals() {
        val days = mergeNutrition(
            listOf(q("q", d1, 120, 30, 0, 0)),
            listOf(e("a", d1, 100.0, 400.0, 110.0, 0.0, 0.0), e("b", d2, 100.0, 400.0, 150.0, 0.0, 0.0)),
        ).asMacroDays()
        val r = DateRange(d1, d2)
        assertEquals(145, avgProtein(days, r))        // (140 + 150) / 2
        assertEquals(1, daysOnTarget(days, r, 150).first)
        assertEquals(2, daysOnTarget(days, r, 150).second)
    }

    @Test fun insightsSkipUnknownProtein() {
        val d3 = d2.plusDays(1)
        val days = mergeNutrition(
            listOf(
                q("u", d1, 900, null, null, null),          // protein n/a for the whole day
                q("p", d2, 300, null, null, null),          // partial: 120 known + n/a, below target -> undecided
                q("h", d3, 300, null, null, null),          // partial but known part already >= target -> hit
            ),
            listOf(e("a", d2, 100.0, 400.0, 120.0, 0.0, 0.0), e("b", d3, 100.0, 400.0, 155.0, 0.0, 0.0)),
        ).asMacroDays()
        val r = DateRange(d1, d3)
        assertNull(avgProtein(days, r))                    // no day with fully known protein
        assertEquals(767, avgKcal(days, r))                // (900 + 700 + 700) / 3: kcal is always known
        assertEquals(1 to 1, daysOnTarget(days, r, 150))
        val avg = macroAverages(days, r)!!
        assertEquals(0, avg.proteinG); assertEquals(3, avg.days)
        // Diary flag: an unknown/partial day is never "protein missed".
        val flags = com.lifehub.app.feature.calendar.DiaryFlags.dayFlags(
            mergeNutrition(listOf(q("u", d1, 900, null, null, null), q("p", d2, 300, null, null, null)), listOf(e("a", d2, 100.0, 400.0, 120.0, 0.0, 0.0))),
            d3, 150, null,
        )
        assertFalse(flags[d1]?.proteinMissed ?: false); assertFalse(flags[d2]?.proteinMissed ?: false)
    }
}
