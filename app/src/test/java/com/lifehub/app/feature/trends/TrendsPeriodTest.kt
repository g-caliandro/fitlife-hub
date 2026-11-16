package com.lifehub.app.feature.trends

import com.lifehub.app.data.model.HomePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendsPeriodTest {
    @Test fun storedMapping() {
        assertEquals(TrendsPeriod.TWO_WEEKS, TrendsPeriod.fromStored(HomePeriod.ONE_WEEK)) // setting default -> 2W
        assertEquals(TrendsPeriod.FOUR_WEEKS, TrendsPeriod.fromStored(HomePeriod.TWO_WEEKS))
        TrendsPeriod.entries.forEach { assertEquals(it, TrendsPeriod.fromStored(it.toStored())) }
    }

    @Test fun deepLinkPeriod() {
        assertEquals(TrendsPeriod.TWO_WEEKS, TrendsPeriod.parse("1W"))
        assertEquals(TrendsPeriod.TWO_WEEKS, TrendsPeriod.parse("2W"))
        assertEquals(TrendsPeriod.FOUR_WEEKS, TrendsPeriod.parse("4W"))
        assertNull(TrendsPeriod.parse(null))
        assertNull(TrendsPeriod.parse("3W"))
    }

    @Test fun oldWeekLinkOffsetLandsOnThePeriodHoldingThatWeek() {
        // Protein notification: "period=1W&offset=1" = last week, which is inside the current 2W period.
        assertEquals(0, TrendsPeriod.deepLinkOffset("1W", 1))
        assertEquals(1, TrendsPeriod.deepLinkOffset("1W", 2))
        assertEquals(1, TrendsPeriod.deepLinkOffset("1W", 3))
        assertEquals(1, TrendsPeriod.deepLinkOffset("2W", 1))
        assertEquals(0, TrendsPeriod.deepLinkOffset("4W", -2))
    }
}
