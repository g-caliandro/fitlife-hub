package com.lifehub.app.feature.workout

import com.lifehub.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WorkoutOpsTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private fun set(order: Int, idx: Int, type: TargetType = TargetType.REPS, v: Int? = 6, st: SetStatus = SetStatus.PENDING, w: Double? = null) =
        SessionSet("s$order-$idx", "sess", "ex$order", "Ex$order", order, idx, type, v, null, null, w, st, null, 90)
    private val ex = Exercise("new", "New", ExerciseCategory.PULL, TargetType.REPS, true, false)

    @Test fun orderedSorts() {
        val l = WorkoutOps.ordered(listOf(set(1, 0), set(0, 1), set(0, 0)))
        assertEquals(listOf("s0-0", "s0-1", "s1-0"), l.map { it.id })
    }

    @Test fun nextPendingForwardThenWrapThenNull() {
        val l = listOf(set(0, 0, st = SetStatus.PENDING), set(0, 1, st = SetStatus.DONE), set(0, 2, st = SetStatus.PENDING), set(0, 3, st = SetStatus.SKIPPED))
        assertEquals(2, WorkoutOps.nextPendingIndex(l, 0))
        assertEquals(0, WorkoutOps.nextPendingIndex(l, 2))
        assertNull(WorkoutOps.nextPendingIndex(l.map { it.copy(status = SetStatus.DONE) }, 1))
    }

    @Test fun completeReps() {
        val d = WorkoutOps.complete(set(0, 0), 7, null, 5.0, now)
        assertEquals(SetStatus.DONE, d.status); assertEquals(7, d.actualReps); assertNull(d.actualSeconds); assertEquals(now, d.completedAt)
    }

    @Test fun completeSecondsAndInvariant() {
        val s = set(0, 0, TargetType.SECONDS, 30)
        assertEquals(31, WorkoutOps.complete(s, null, 31, null, now).actualSeconds)
        assertThrows(IllegalArgumentException::class.java) { WorkoutOps.complete(s, 5, null, null, now) }
        assertThrows(IllegalArgumentException::class.java) { WorkoutOps.complete(set(0, 0, TargetType.MAX, null), null, null, null, now) }
    }

    @Test fun skipAndUnskip() {
        val sk = WorkoutOps.skip(set(0, 0).copy(actualReps = 3))
        assertEquals(SetStatus.SKIPPED, sk.status); assertNull(sk.actualReps)
        assertEquals(SetStatus.PENDING, WorkoutOps.unskip(sk).status)
    }

    @Test fun addSetCopiesLast() {
        val l = listOf(set(0, 0), set(0, 1, st = SetStatus.DONE, w = 10.0).copy(actualReps = 6), set(1, 0))
        val n = WorkoutOps.addSet(l, 0, "new")
        assertEquals(2, n.setIndex); assertEquals(SetStatus.PENDING, n.status); assertNull(n.actualReps); assertEquals(10.0, n.weightKg)
        assertEquals("ex0", n.exerciseId)
    }

    @Test fun removeSetRenumbers() {
        val l = listOf(set(0, 0), set(0, 1), set(0, 2), set(1, 0))
        val r = WorkoutOps.removeSet(l, "s0-1")
        assertEquals(listOf("s0-0", "s0-2"), r.map { it.id }); assertEquals(listOf(0, 1), r.map { it.setIndex })
    }

    @Test fun addExerciseAppends() {
        val r = WorkoutOps.addExercise("sess", listOf(set(0, 0), set(2, 0)), ex, 3, TargetType.MAX, 5, 2.5, 60)
        assertEquals(3, r.size); assertTrue(r.all { it.exerciseOrder == 3 && it.restSeconds == 60 && it.targetValue == null })
        assertEquals(listOf(0, 1, 2), r.map { it.setIndex })
        assertEquals(0, WorkoutOps.addExercise("s", emptyList(), ex, 1, TargetType.REPS, 5, null).first().exerciseOrder)
    }

    @Test fun swapOnlyPending() {
        val l = listOf(set(0, 0, st = SetStatus.DONE), set(0, 1), set(1, 0))
        val r = WorkoutOps.swapExercise(l, 0, ex)
        assertEquals(listOf("s0-1"), r.map { it.id }); assertEquals("new", r[0].exerciseId)
    }

    @Test fun applyWeightToRemaining() {
        val l = listOf(set(0, 0), set(0, 1), set(0, 2, st = SetStatus.DONE), set(0, 3), set(1, 1))
        val r = WorkoutOps.applyWeightToRemaining(l, "s0-0", 12.5)
        assertEquals(listOf("s0-1", "s0-3"), r.map { it.id }); assertTrue(r.all { it.weightKg == 12.5 })
    }

    @Test fun carryWeightToRemainingOnlyWhenChanged() {
        val l = listOf(set(0, 0, w = 10.0), set(0, 1, w = 10.0), set(0, 2, w = 10.0), set(1, 0, w = 10.0))
        val done = l[0].copy(status = SetStatus.DONE, weightKg = 12.5)
        val r = WorkoutOps.carryWeight(l, done, 10.0)
        assertEquals(listOf("s0-1", "s0-2"), r.map { it.id }); assertTrue(r.all { it.weightKg == 12.5 })
        assertTrue(WorkoutOps.carryWeight(l, l[0].copy(status = SetStatus.DONE), 10.0).isEmpty())
    }

    @Test fun rulerFallbackValue() {
        assertEquals("30", WorkoutOps.fallbackValue(set(0, 0, TargetType.SECONDS, null), emptyList(), null))
        assertEquals("1", WorkoutOps.fallbackValue(set(0, 0, TargetType.MAX, null), emptyList(), null))
        val earlier = set(0, 0, TargetType.MAX, null, st = SetStatus.DONE).copy(actualReps = 14)
        assertEquals("14", WorkoutOps.fallbackValue(set(0, 1, TargetType.MAX, null), listOf(earlier), null))
    }

    @Test fun prefillRules() {
        assertEquals(SetInput("", "6"), WorkoutOps.prefill(set(0, 0)))
        assertEquals(SetInput("", ""), WorkoutOps.prefill(set(0, 0, TargetType.MAX, null)))
        assertEquals(SetInput("20", "30"), WorkoutOps.prefill(set(0, 0, TargetType.SECONDS, 30, w = 20.0)))
        assertEquals(SetInput("2.5", "8"), WorkoutOps.prefill(set(0, 0, st = SetStatus.DONE, w = 2.5).copy(actualReps = 8)))
    }

    @Test fun maxNeedsInputAndCompleteFromInput() {
        val max = set(0, 0, TargetType.MAX, null)
        assertFalse(WorkoutOps.canComplete(WorkoutOps.prefill(max)))
        assertNull(WorkoutOps.completeFromInput(max, SetInput("", ""), now))
        assertEquals(9, WorkoutOps.completeFromInput(max, SetInput("", "9"), now)!!.actualReps)
        assertTrue(WorkoutOps.canComplete(WorkoutOps.prefill(set(0, 0))))
        assertEquals(0, WorkoutOps.completeFromInput(max, SetInput("", "0"), now)!!.actualReps)
        val sec = WorkoutOps.completeFromInput(set(0, 0, TargetType.SECONDS, 30), SetInput("5", "28"), now)!!
        assertEquals(28, sec.actualSeconds); assertEquals(5.0, sec.weightKg)
    }

    @Test fun weightChanged() {
        assertFalse(WorkoutOps.weightChanged(set(0, 0, w = 5.0), SetInput("5", "6")))
        assertTrue(WorkoutOps.weightChanged(set(0, 0, w = 5.0), SetInput("7.5", "6")))
        assertFalse(WorkoutOps.weightChanged(set(0, 0), SetInput("", "6")))
    }

    @Test fun manualSetsPrefill() {
        fun pe(t: TargetType, v: Int?) = PlanExercise("p", "plan", "e", 0, 2, t, v, null, 60)
        val e = ex
        val plan = PlanWithExercises(
            Plan("plan", "P", emptySet(), null, false, now),
            listOf(PlanExerciseItem(pe(TargetType.REPS, 6), e), PlanExerciseItem(pe(TargetType.MAX, null), e), PlanExerciseItem(pe(TargetType.SECONDS, 30), e)),
        )
        val s = WorkoutOps.manualSets("sess", plan, now)
        assertEquals(6, s.size)
        assertEquals(6, s[0].actualReps); assertEquals(SetStatus.DONE, s[0].status)
        assertEquals(SetStatus.SKIPPED, s[2].status); assertNull(s[2].actualReps)
        assertEquals(30, s[4].actualSeconds); assertEquals(listOf(0, 1, 2), s.map { it.exerciseOrder }.distinct())
    }

    @Test fun applyEditRules() {
        val s = set(0, 0)
        assertEquals(SetStatus.DONE, WorkoutOps.applyEdit(s, "10", "8", true, now).status)
        assertEquals(10.0, WorkoutOps.applyEdit(s, "10", "8", true, now).weightKg)
        assertEquals(SetStatus.SKIPPED, WorkoutOps.applyEdit(s, "", "", true, now).status)
        val un = WorkoutOps.applyEdit(s.copy(status = SetStatus.DONE, actualReps = 6), "", "6", false, now)
        assertEquals(SetStatus.SKIPPED, un.status); assertNull(un.actualReps)
        assertEquals(30, WorkoutOps.applyEdit(set(0, 0, TargetType.SECONDS, 30), "", "30", true, now).actualSeconds)
    }
}

class WorkoutOpsUxTest {
    private fun set(id: String, order: Int, idx: Int, type: com.lifehub.app.data.model.TargetType = com.lifehub.app.data.model.TargetType.REPS,
                    status: com.lifehub.app.data.model.SetStatus = com.lifehub.app.data.model.SetStatus.DONE, reps: Int? = null, secs: Int? = null, w: Double? = null) =
        com.lifehub.app.data.model.SessionSet(id, "s", "e$order", "Ex$order", order, idx, type, 8, reps, secs, w, status, null, 90)

    @org.junit.Test fun previousLabels() {
        val T = com.lifehub.app.data.model.TargetType.SECONDS
        assertEquals("6", WorkoutOps.previousLabel(set("a", 0, 0, reps = 6)))
        assertEquals("8 · +10kg", WorkoutOps.previousLabel(set("a", 0, 0, reps = 8, w = 10.0)))
        assertEquals("12 s", WorkoutOps.previousLabel(set("a", 0, 0, T, secs = 12)))
        assertEquals("—", WorkoutOps.previousLabel(null))
        assertEquals("—", WorkoutOps.previousLabel(set("a", 0, 0, status = com.lifehub.app.data.model.SetStatus.SKIPPED)))
        val cur = listOf(set("c0", 0, 0), set("c1", 0, 1), set("c2", 0, 2))
        val m = WorkoutOps.previousLabels(cur, mapOf("e0" to listOf(set("p0", 0, 0, reps = 6), set("p1", 0, 1, reps = 5))))
        assertEquals(mapOf("c0" to "6", "c1" to "5", "c2" to "—"), m)
    }

    @org.junit.Test fun summaryCounts() {
        val T = com.lifehub.app.data.model.TargetType.SECONDS
        val P = com.lifehub.app.data.model.SetStatus.PENDING
        val s = WorkoutOps.summarize(listOf(set("a", 0, 0, reps = 6), set("b", 0, 1, reps = 4), set("c", 1, 0, T, secs = 30), set("d", 1, 1, T, status = P)))
        assertEquals(WorkoutOps.Summary(done = 3, skipped = 0, pending = 1, reps = 10, holdSeconds = 30), s)
    }
}
