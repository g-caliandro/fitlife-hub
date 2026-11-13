package com.lifehub.app.domain

import com.lifehub.app.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GoalProgressTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun goal(start: LocalDate, deadline: LocalDate, achieved: LocalDate? = null) =
        Goal("g", "G", null, start, deadline, false, GoalState.ACTIVE, achieved, Instant.EPOCH)
    private fun session(planId: String?, date: LocalDate) = WorkoutSession(
        "s$date$planId", planId, "P", date, Instant.EPOCH, Instant.EPOCH, 0, SessionStatus.COMPLETED, null, null, false, 0, null,
    )

    @Test fun timeProgressMiddle() {
        val t = timeProgress(d(1, 1), d(1, 11), d(1, 4))
        assertEquals(10, t.totalDays); assertEquals(3, t.elapsedDays); assertEquals(0.3f, t.fraction, 1e-6f); assertEquals(7, t.daysLeft)
    }

    @Test fun timeProgressClampsAndOverdue() {
        val before = timeProgress(d(1, 10), d(1, 20), d(1, 1))
        assertEquals(0, before.elapsedDays)
        val after = timeProgress(d(1, 1), d(1, 11), d(1, 14))
        assertEquals(10, after.elapsedDays); assertEquals(1f, after.fraction, 0f); assertEquals(-3, after.daysLeft)
    }

    @Test fun totalDaysAtLeastOne() {
        assertEquals(1, timeProgress(d(1, 1), d(1, 1), d(1, 1)).totalDays)
    }

    @Test fun sessionsDoneRespectsWindow() {
        val g = goal(d(1, 1), d(3, 1))
        val ss = listOf(session("p", d(1, 1)), session("p", d(1, 10)), session("p", d(2, 1)), session("q", d(1, 5)), session("p", d(12, 31).minusYears(1)))
        assertEquals(2, sessionsDone(g, "p", ss, d(1, 31)))
    }

    @Test fun sessionsDoneUsesAchievedAt() {
        val g = goal(d(1, 1), d(3, 1), achieved = d(1, 15))
        val ss = listOf(session("p", d(1, 10)), session("p", d(1, 20)))
        assertEquals(1, sessionsDone(g, "p", ss, d(2, 1)))
    }

    @Test fun planProgressFractionCapped() {
        val g = goal(d(1, 1), d(3, 1))
        val plan = Plan("p", "Pull", emptySet(), null, false, Instant.EPOCH)
        val gl = GoalWithLinks(g, listOf(GoalPlanLink("g", "p", 2), GoalPlanLink("g", "missing", 1)))
        val out = planProgress(gl, listOf(plan), listOf(session("p", d(1, 2)), session("p", d(1, 3)), session("p", d(1, 4))), d(2, 1))
        assertEquals(1, out.size)
        assertEquals(3, out[0].done); assertEquals(1f, out[0].fraction!!, 0f)
        assertNull(PlanProgress("p", "x", 4, null).fraction)
    }
}
