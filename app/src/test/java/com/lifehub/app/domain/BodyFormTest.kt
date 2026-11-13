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
        assertFalse(macrosValid(parseMacros("", "0", "", "")))
        assertTrue(macrosValid(parseMacros("1", "", "", "")))
        assertEquals(4 * 150 + 4 * 240 + 9 * 70, macroAutoKcal(parseMacros("150", "240", "70", "")))
    }
}
