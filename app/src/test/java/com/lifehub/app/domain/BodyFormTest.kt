package com.lifehub.app.domain

import com.lifehub.app.feature.body.*
import com.lifehub.app.feature.nutrition.*
import org.junit.Assert.*
import org.junit.Test

class BodyFormTest {
    @Test fun weightRange() {
        assertEquals(BodyError.WeightRange, validateBody("19.9", "", "").weightError)
        assertEquals(BodyError.WeightRange, validateBody("300.1", "", "").weightError)
        assertEquals(BodyError.WeightRange, validateBody("", "", "").weightError)
        assertTrue(validateBody("20", "", "").ok)
        assertTrue(validateBody("76,9", "", "").ok)
    }
    @Test fun muscleFat() {
        assertEquals(BodyError.MuscleExceeds, validateBody("70", "71", "").muscleError)
        assertEquals(BodyError.FatExceeds, validateBody("70", "", "70.1").fatError)
        assertEquals(BodyError.SumExceeds, validateBody("70", "40", "31").fatError)
        assertTrue(validateBody("70", "40", "30").ok)
    }
    @Test fun pct() {
        assertEquals(16.25, liveFatPct(76.9, 12.5)!!, 0.1)
        assertNull(liveFatPct(76.9, null))
    }
    @Test fun macros() {
        // Only calories are required; blank macros are unknown (null), not 0.
        assertFalse(macrosValid(parseMacros("", "0", "", "")))
        assertFalse(macrosValid(parseMacros("30", "", "", "")))          // no kcal and macros incomplete
        assertTrue(macrosValid(parseMacros("", "", "", "450")))          // kcal only
        assertTrue(macrosValid(parseMacros("1", "1", "1", "")))          // all three -> auto kcal
        assertFalse(macrosValid(parseMacros("0", "0", "0", "")))
        assertEquals(ParsedMacros(null, null, 5, 450), parseMacros("", " ", "5", "450"))
        assertEquals(4 * 150 + 4 * 240 + 9 * 70, macroAutoKcal(parseMacros("150", "240", "70", "")))
        assertNull(macroAutoKcal(parseMacros("150", "", "70", "")))
        assertEquals(450, effectiveKcal(parseMacros("", "", "", "450")))
        assertEquals("P 12 · C n/a · F 4", macroSummary(12, null, 4))
    }
}
