package com.lifehub.app.feature.trends

import com.lifehub.app.core.designsystem.components.DeltaGood
import org.junit.Assert.assertEquals
import org.junit.Test

class KcalDeltaGoodTest {
    @Test fun closerToTargetIsGood() {
        assertEquals(DeltaGood.Up, kcalDeltaGood(2500, 2300, 2600))     // up towards target -> up is good
        assertEquals(DeltaGood.Down, kcalDeltaGood(2700, 2900, 2600))   // down towards target -> down is good
    }

    @Test fun fartherFromTargetIsBad() {
        assertEquals(DeltaGood.Down, kcalDeltaGood(3000, 2700, 2600))   // up away from target -> up is bad
        assertEquals(DeltaGood.Up, kcalDeltaGood(2200, 2500, 2600))     // down away from target -> down is bad
    }

    @Test fun neutralCases() {
        assertEquals(DeltaGood.Neutral, kcalDeltaGood(2700, 2500, 2600))  // equally close
        assertEquals(DeltaGood.Neutral, kcalDeltaGood(2500, 2300, null))  // no target
        assertEquals(DeltaGood.Neutral, kcalDeltaGood(2500, null, 2600))
    }
}
