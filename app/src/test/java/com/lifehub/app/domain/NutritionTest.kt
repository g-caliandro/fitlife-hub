package com.lifehub.app.domain

import com.lifehub.app.data.model.BodyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class NutritionTest {
    @Test fun autoKcal() { assertEquals(4 * 150 + 4 * 250 + 9 * 70, autoKcal(150, 250, 70)); assertEquals(0, autoKcal(0, 0, 0)) }

    @Test fun fatPct() {
        val e = BodyEntry(LocalDate.of(2026, 1, 1), 80.0, null, 16.0, Instant.EPOCH)
        assertEquals(20.0, fatPct(e)!!, 1e-9)
        assertNull(fatPct(e.copy(fatKg = null)))
    }
}
