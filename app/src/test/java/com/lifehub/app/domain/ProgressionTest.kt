package com.lifehub.app.domain

import com.lifehub.app.data.model.PlanExercise
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private fun pe(i: Int, ex: String, type: TargetType, v: Int?, w: Double? = null) = PlanExercise("pe$i", "p", ex, i, 3, type, v, w, 90)
    private fun set(order: Int, idx: Int, ex: String, type: TargetType, actual: Int?, w: Double? = null, st: SetStatus = SetStatus.DONE) =
        SessionSet("$order-$idx", "s", ex, ex.replaceFirstChar { it.uppercase() }, order, idx, type, 8,
            if (type == TargetType.SECONDS) null else actual, if (type == TargetType.SECONDS) actual else null, w, st, null, 90)

    @Test fun oneMoreRepRaisesTheRepTarget() {
        val r = Progression.raise(listOf(pe(0, "pullup", TargetType.REPS, 8)), listOf(
            set(0, 0, "pullup", TargetType.REPS, 9), set(0, 1, "pullup", TargetType.REPS, 8), set(0, 2, "pullup", TargetType.REPS, 7),
        ))
        assertEquals(9, r.single().updated.targetValue); assertEquals("Pullup 9 reps", r.single().label)
    }

    @Test fun heavierWeightBecomesTheNewTarget() {
        val r = Progression.raise(listOf(pe(0, "bench", TargetType.REPS, 8, 60.0)), listOf(
            set(0, 0, "bench", TargetType.REPS, 8, 60.0), set(0, 1, "bench", TargetType.REPS, 6, 62.5), set(0, 2, "bench", TargetType.REPS, 7, 62.5),
        )).single().updated
        assertEquals(62.5, r.weightKg!!, 0.0); assertEquals(7, r.targetValue)
    }

    @Test fun neverLowered() {
        assertTrue(Progression.raise(listOf(pe(0, "bench", TargetType.REPS, 8, 60.0)), listOf(
            set(0, 0, "bench", TargetType.REPS, 6, 60.0), set(0, 1, "bench", TargetType.REPS, 8, 50.0),
        )).isEmpty())
    }

    @Test fun skippedAndPendingSetsAreIgnored() {
        assertTrue(Progression.raise(listOf(pe(0, "pullup", TargetType.REPS, 8)), listOf(
            set(0, 0, "pullup", TargetType.REPS, 12, st = SetStatus.SKIPPED), set(0, 1, "pullup", TargetType.REPS, 8),
        )).isEmpty())
    }

    @Test fun holdsAndMaxSets() {
        val r = Progression.raise(
            listOf(pe(0, "hang", TargetType.SECONDS, 30), pe(1, "dip", TargetType.MAX, null)),
            listOf(set(0, 0, "hang", TargetType.SECONDS, 40), set(1, 0, "dip", TargetType.MAX, 15), set(1, 1, "dip", TargetType.MAX, 12, 5.0)),
        )
        assertEquals(40, r[0].updated.targetValue)
        assertEquals(null, r[1].updated.targetValue); assertEquals(5.0, r[1].updated.weightKg!!, 0.0)
    }

    @Test fun matchesTheBlockEvenIfExercisesWereReordered() {
        // Exercise 0 removed mid-workout -> the bench block is the only one of that exercise.
        val r = Progression.raise(listOf(pe(0, "pullup", TargetType.REPS, 8), pe(1, "bench", TargetType.REPS, 8, 60.0)),
            listOf(set(3, 0, "bench", TargetType.REPS, 10, 60.0)))
        assertEquals("pe1", r.single().updated.id); assertEquals(10, r.single().updated.targetValue)
    }
}
