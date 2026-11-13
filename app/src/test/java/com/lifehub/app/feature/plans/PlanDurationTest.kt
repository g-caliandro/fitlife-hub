package com.lifehub.app.feature.plans

import com.lifehub.app.data.model.PlanExercise
import com.lifehub.app.data.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanDurationTest {
    private fun pe(sets: Int, type: TargetType, target: Int?, rest: Int) = PlanExercise("id", "p", "e", 0, sets, type, target, null, rest)

    @Test fun estimate() {
        assertEquals(0, estimatePlanMinutes(emptyList()))
        assertEquals(5, estimatePlanMinutes(listOf(pe(1, TargetType.REPS, 8, 30))))                // 70 s -> min 5
        // 4×(40+120) + 3×(40+90) + 3×(30+60) = 640 + 390 + 270 = 1300 s ≈ 21.7 min -> 20
        assertEquals(20, estimatePlanMinutes(listOf(pe(4, TargetType.REPS, 6, 120), pe(3, TargetType.MAX, null, 90), pe(3, TargetType.SECONDS, 30, 60))))
        // 2650 s ≈ 44.2 min -> ~45
        assertEquals(45, estimatePlanMinutes(listOf(pe(10, TargetType.REPS, 8, 225), pe(1, TargetType.REPS, 8, 0))))
    }
}
