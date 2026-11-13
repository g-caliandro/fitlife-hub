package com.lifehub.app.domain

import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ComplianceTest {
    private fun s(order: Int, i: Int, type: TargetType, target: Int?, reps: Int? = null, secs: Int? = null, st: SetStatus = SetStatus.DONE, ex: String = "e$order") =
        SessionSet("$order-$i", "s", ex, ex, order, i, type, target, reps, secs, null, st, Instant.EPOCH, 90)

    @Test fun repsScoreCappedAtTarget() {
        assertEquals(0.5, ComplianceCalc.setScore(s(0, 0, TargetType.REPS, 10, reps = 5)), 1e-9)
        assertEquals(1.0, ComplianceCalc.setScore(s(0, 0, TargetType.REPS, 10, reps = 14)), 1e-9)
    }

    @Test fun maxIsOneWhenDone() {
        assertEquals(1.0, ComplianceCalc.setScore(s(0, 0, TargetType.MAX, null, reps = 3)), 1e-9)
        assertEquals(0.0, ComplianceCalc.setScore(s(0, 0, TargetType.MAX, null, st = SetStatus.SKIPPED)), 1e-9)
    }

    @Test fun secondsProportionalAndCapped() {
        assertEquals(0.75, ComplianceCalc.setScore(s(0, 0, TargetType.SECONDS, 40, secs = 30)), 1e-9)
        assertEquals(1.0, ComplianceCalc.setScore(s(0, 0, TargetType.SECONDS, 40, secs = 60)), 1e-9)
    }

    @Test fun pendingAndSkippedScoreZero() {
        assertEquals(0.0, ComplianceCalc.setScore(s(0, 0, TargetType.REPS, 10, st = SetStatus.PENDING)), 1e-9)
        assertEquals(0.0, ComplianceCalc.setScore(s(0, 0, TargetType.REPS, 10, reps = 10, st = SetStatus.SKIPPED)), 1e-9)
    }

    @Test fun aggregateMeanAndExerciseCount() {
        val sets = listOf(
            s(0, 0, TargetType.REPS, 10, reps = 10), s(0, 1, TargetType.REPS, 10, reps = 5),        // 1.0, 0.5, exercise done
            s(1, 0, TargetType.MAX, null, reps = 4), s(1, 1, TargetType.MAX, null, st = SetStatus.SKIPPED), // 1, 0, not complete
        )
        val c = ComplianceCalc.compute(sets)!!
        assertEquals(63, c.percent) // (1+.5+1+0)/4 = 62.5 -> 63
        assertEquals(1, c.exercisesDone); assertEquals(2, c.exercisesTotal)
    }

    @Test fun extraSetsBeyondPlanIgnored() {
        val sets = listOf(
            s(0, 0, TargetType.REPS, 10, reps = 10), s(0, 1, TargetType.REPS, 10, reps = 10), s(0, 2, TargetType.REPS, 10, st = SetStatus.PENDING),
            s(1, 0, TargetType.REPS, 5, reps = 1, ex = "adhoc"),
        )
        val c = ComplianceCalc.compute(sets, mapOf("e0" to 2))!!
        assertEquals(100, c.percent); assertEquals(1, c.exercisesDone); assertEquals(1, c.exercisesTotal)
    }

    @Test fun emptyIsNull() = assertNull(ComplianceCalc.compute(emptyList()))
}
