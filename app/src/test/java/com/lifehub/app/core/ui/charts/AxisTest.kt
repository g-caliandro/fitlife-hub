package com.lifehub.app.core.ui.charts

import com.lifehub.app.domain.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AxisTest {
    @Test fun sharedAxisCoversWeightAndMuscle() {
        val t = niceTicks(34.6, 78.4)
        assertTrue(t.lo < 34.6 && t.hi > 78.4)
        assertEquals(listOf(30.0, 40.0, 50.0, 60.0, 70.0, 80.0), t.values)
        assertEquals("30", t.format(30.0))
    }

    @Test fun narrowRangeGetsDecimalTicks() {
        val t = niceTicks(76.9, 77.6)
        assertTrue(t.lo < 76.9 && t.hi > 77.6)
        assertTrue(t.values.size in 3..6)
        assertEquals(1, t.decimals)
    }

    @Test fun singleValue() {
        val t = niceTicks(77.0, 77.0)
        assertTrue(t.lo < 77.0 && t.hi > 77.0 && t.values.size >= 2)
    }

    private val mon = LocalDate.of(2026, 10, 19)

    @Test fun xLabels4wShowMondaysAndThursdays() {
        val days = DateRange(mon, mon.plusDays(27)).days
        assertEquals(setOf(0, 3, 7, 10, 14, 17, 21, 24), xLabelIndexes(days, null))
        // today (Sat, index 26) is far enough from its neighbours to get its own label
        assertTrue(26 in xLabelIndexes(days, mon.plusDays(26)))
        // today right next to a Monday label is not squeezed in
        assertEquals(xLabelIndexes(days, null), xLabelIndexes(days, mon.plusDays(22)))
    }

    @Test fun xLabels2wEveryOtherDay() {
        val days = DateRange(mon, mon.plusDays(13)).days
        assertEquals(setOf(0, 2, 4, 6, 8, 10, 12), xLabelIndexes(days, null))
    }
}
