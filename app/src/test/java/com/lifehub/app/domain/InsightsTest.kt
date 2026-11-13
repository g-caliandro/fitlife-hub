package com.lifehub.app.domain

import com.lifehub.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InsightsTest {
    private val today = LocalDate.of(2026, 11, 12) // Thursday
    private val cur1 = Periods.period(today, HomePeriod.ONE_WEEK, 0)   // Nov 9..15
    private val prev1 = Periods.previous(cur1)                         // Nov 2..8
    private val cur2 = Periods.period(today, HomePeriod.TWO_WEEKS, 0)  // Nov 2..15
    private val prev2 = Periods.previous(cur2)                         // Oct 19..Nov 1

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun body(date: LocalDate, w: Double, m: Double? = null, f: Double? = null) = BodyEntry(date, w, m, f, Instant.EPOCH)
    private fun macro(date: LocalDate, p: Int, kcal: Int) = MacroDay(date, p, 0, 0, kcal, false, Instant.EPOCH)
    private fun plan(id: String, vararg wd: Int, archived: Boolean = false) =
        Plan(id, id, wd.toSet(), null, archived, Instant.EPOCH)
    private fun sess(id: String, date: LocalDate, planId: String? = "p", status: SessionStatus = SessionStatus.COMPLETED) =
        WorkoutSession(id, planId, "n", date, Instant.EPOCH, null, null, status, null, null, false, 0, null)
    private fun set(sid: String, type: TargetType, reps: Int? = null, secs: Int? = null, status: SetStatus = SetStatus.DONE) =
        SessionSet("$sid-${reps}-$secs-$status-${type}", sid, "e", "e", 0, 0, type, 5, reps, secs, null, status, null, 90)
    private fun sws(s: WorkoutSession, vararg sets: SessionSet) = SessionWithSets(s, sets.toList())

    // ---- Metric
    @Test fun metricDelta() {
        assertEquals(1.5, Metric(3.0, 1.5).delta!!, 1e-9)
        assertNull(Metric(3.0, null).delta)
        assertNull(Metric<Double>(null, 3.0).delta)
        assertEquals(-2.0, Metric(1, 3).delta!!, 1e-9)
    }

    // ---- 4.2 body
    @Test fun lastBodyValuePicksLatestNonNullInRange() {
        val e = listOf(
            body(d(11, 9), 77.0, 37.0), body(d(11, 11), 76.5, null), body(d(11, 20), 70.0, 30.0), body(d(11, 5), 99.0, 99.0),
        )
        assertEquals(76.5, lastBodyValue(e, cur1) { it.weightKg }!!, 1e-9)
        assertEquals(37.0, lastBodyValue(e, cur1) { it.muscleKg }!!, 1e-9) // skips null on later date
        assertNull(lastBodyValue(e, cur1) { it.fatKg })
        assertNull(lastBodyValue(emptyList(), cur1) { it.weightKg })
    }

    @Test fun bodyRangeBoundariesInclusive() {
        val e = listOf(body(cur1.start, 70.0), body(cur1.end, 71.0))
        assertEquals(71.0, lastBodyValue(e, cur1) { it.weightKg }!!, 1e-9)
        assertEquals(70.0, lastBodyValue(e.take(1), cur1) { it.weightKg }!!, 1e-9)
        assertNull(lastBodyValue(e, prev1) { it.weightKg })
    }

    @Test fun bodyInsightsDeltaAndUndefinedPrev() {
        val body = listOf(body(d(11, 3), 77.2, 37.0, 12.7), body(d(11, 10), 76.9, 37.3, 12.5), body(d(11, 11), 76.6))
        val i = computeInsights(input(body = body))
        assertEquals(76.6, i.weight.current!!, 1e-9)
        assertEquals(-0.6, i.weight.delta!!, 1e-9)
        assertEquals(0.3, i.muscle.delta!!, 1e-9)
        assertEquals(-0.2, i.fat.delta!!, 1e-9)
        assertEquals(12.5 / 76.9 * 100, i.fatPercent!!, 1e-9)
        val only = computeInsights(input(body = listOf(body(d(11, 10), 76.9))))
        assertEquals(76.9, only.weight.current!!, 1e-9)
        assertNull(only.weight.delta)
        val none = computeInsights(input(body = emptyList()))
        assertNull(none.weight.current); assertNull(none.fatPercent)
    }

    // ---- 4.3
    @Test fun sessionsCountOnlyCompletedInRange() {
        val s = listOf(
            sess("a", d(11, 9)), sess("b", d(11, 15)), sess("c", d(11, 8)),
            sess("d", d(11, 10), status = SessionStatus.IN_PROGRESS),
        )
        assertEquals(2, sessionsCount(s, cur1))
        assertEquals(1, sessionsCount(s, prev1))
        assertEquals(0, sessionsCount(emptyList(), cur1))
    }

    @Test fun avgRepsAndVolumeOnlyDoneRepSets() {
        val s1 = sess("a", d(11, 10))
        val data = listOf(
            sws(s1,
                set("a", TargetType.REPS, reps = 8), set("a", TargetType.MAX, reps = 5),
                set("a", TargetType.REPS, reps = 9, status = SetStatus.SKIPPED),
                set("a", TargetType.REPS, reps = null, status = SetStatus.PENDING),
                set("a", TargetType.SECONDS, secs = 30), set("a", TargetType.SECONDS, secs = 20),
            ),
        )
        assertEquals(6.5, avgRepsPerSet(data, cur1)!!, 1e-9)
        assertEquals(13, volumeReps(data, cur1))
        assertEquals(50, holdSeconds(data, cur1))
        assertNull(avgRepsPerSet(data, prev1))
        assertEquals(0, volumeReps(data, prev1))
        assertEquals(0, holdSeconds(data, prev1))
    }

    @Test fun nonCompletedSessionsExcludedFromVolume() {
        val ip = sess("x", d(11, 10), status = SessionStatus.IN_PROGRESS)
        val data = listOf(sws(ip, set("x", TargetType.REPS, reps = 10)))
        assertEquals(0, volumeReps(data, cur1))
        assertNull(avgRepsPerSet(data, cur1))
    }

    @Test fun volumeDeltaAndUndefinedAvg() {
        val data = listOf(
            sws(sess("a", d(11, 10)), set("a", TargetType.REPS, reps = 10), set("a", TargetType.REPS, reps = 10)),
            sws(sess("b", d(11, 4)), set("b", TargetType.REPS, reps = 6)),
        )
        val i = computeInsights(input(sessions = data))
        assertEquals(14.0, i.volume.delta!!, 1e-9)
        assertEquals(4.0, i.avgRepsPerSet.delta!!, 1e-9)
        val noPrev = computeInsights(input(sessions = data.take(1)))
        assertNull(noPrev.avgRepsPerSet.delta)
        assertEquals(20.0, noPrev.volume.delta!!, 1e-9) // 0 not undefined
    }

    // ---- 4.4
    @Test fun adherencePlannedCountsOnlyPastAndNonArchived() {
        // Thursday Nov 12 (today, no session yet -> not counted); plan on Mon(1), Thu(4), Sat(6): only Mon 9 counted; Sat 14 is future.
        val plans = listOf(plan("p", 1, 4, 6), plan("old", 1, archived = true), plan("none"))
        val a = adherence(plans, emptyList(), cur1, today)
        assertEquals(Adherence(0, 1), a)
        assertEquals(0, a.percent)
        // previous week fully past: Mon 2, Thu 5, Sat 7
        assertEquals(3, adherence(plans, emptyList(), prev1, today).planned)
    }

    @Test fun adherenceDoneNeedsMatchingPlanAndDate() {
        val plans = listOf(plan("p", 1, 4))
        val s = listOf(
            sess("a", d(11, 9), "p"),                 // done Mon
            sess("b", d(11, 10), "p"),                // non-planned day -> not counted
            sess("c", d(11, 12), "other"),            // different plan
            sess("d", d(11, 12), null),               // deleted plan
        )
        val a = adherence(plans, s, cur1, today)
        // Thu 12 is today without a matching session -> still open, not planned yet
        assertEquals(1, a.done); assertEquals(1, a.planned); assertEquals(100, a.percent)
    }

    @Test fun adherenceTwoSessionsSameDaySamePlanCountOnce() {
        val plans = listOf(plan("p", 1))
        val s = listOf(sess("a", d(11, 9), "p"), sess("b", d(11, 9), "p"))
        assertEquals(Adherence(1, 1), adherence(plans, s, cur1, today))
    }

    @Test fun adherencePercentRoundingAndUndefined() {
        assertEquals(67, Adherence(2, 3).percent)
        assertEquals(33, Adherence(1, 3).percent)
        assertNull(Adherence(0, 0).percent)
    }

    @Test fun adherenceIgnoresInProgressSession() {
        val plans = listOf(plan("p", 1))
        val s = listOf(sess("a", d(11, 9), "p", SessionStatus.IN_PROGRESS))
        assertEquals(0, adherence(plans, s, cur1, today).done)
    }

    @Test fun adherenceDeltaPoints() {
        val plans = listOf(plan("p", 1, 4))
        // current: Mon 9 missed + Thu 12 (today) done = 50; prev: Mon, Thu planned, 2 done = 100
        val s = listOf(
            sess("a", d(11, 12)), sess("b", d(11, 2)), sess("c", d(11, 5)),
        )
        val i = computeInsights(input(plans = plans, sessions = s.map { sws(it) }))
        assertEquals(-50, i.adherenceDeltaPts)
        assertEquals(Adherence(1, 2), i.adherence.first)
        assertEquals(Adherence(2, 2), i.adherence.second)
    }

    @Test fun adherenceDeltaNullWhenNoPlans() {
        assertNull(computeInsights(input()).adherenceDeltaPts)
    }

    // ---- 4.5
    @Test fun avgNutritionRoundsAndRange() {
        val m = listOf(macro(d(11, 9), 150, 2400), macro(d(11, 10), 145, 2501), macro(d(11, 11), 100, 2000), macro(d(11, 1), 999, 9999))
        assertEquals(132, avgProtein(m, cur1)) // 395/3 = 131.67
        assertEquals(2300, avgKcal(m, cur1))   // 6901/3 = 2300.33
        assertNull(avgProtein(m, Periods.period(today, HomePeriod.ONE_WEEK, 3)))
        assertNull(avgKcal(emptyList(), cur1))
    }

    @Test fun avgRoundingHalfUp() {
        val m = listOf(macro(d(11, 9), 100, 1000), macro(d(11, 10), 101, 1001))
        assertEquals(101, avgProtein(m, cur1)) // 100.5 -> 101
    }

    @Test fun daysOnTargetInclusive() {
        val m = listOf(macro(d(11, 9), 150, 1), macro(d(11, 10), 149, 1), macro(d(11, 11), 200, 1))
        assertEquals(2 to 3, daysOnTarget(m, cur1, 150))
        assertEquals(0 to 0, daysOnTarget(m, prev1, 150))
    }

    @Test fun nutritionInsightsDeltaAndTarget() {
        val m = listOf(macro(d(11, 9), 160, 2500), macro(d(11, 3), 140, 2300))
        val i = computeInsights(input(macros = m))
        assertEquals(20.0, i.avgProtein.delta!!, 1e-9)
        assertEquals(200.0, i.avgKcal.delta!!, 1e-9)
        assertEquals(10, i.proteinVsTarget)
        assertEquals(1 to 1, i.proteinOnTarget)
        val none = computeInsights(input())
        assertNull(none.avgProtein.current); assertNull(none.proteinVsTarget); assertEquals(0 to 0, none.proteinOnTarget)
    }

    @Test fun undefinedWhenPrevMissing() {
        val i = computeInsights(input(macros = listOf(macro(d(11, 9), 160, 2500))))
        assertEquals(160, i.avgProtein.current); assertNull(i.avgProtein.delta)
    }

    // ---- 2W period
    @Test fun twoWeekPeriodUsesTwoWeekWindows() {
        assertEquals(d(11, 2), cur2.start); assertEquals(d(11, 15), cur2.end)
        assertEquals(d(10, 19), prev2.start); assertEquals(d(11, 1), prev2.end)
        val s = listOf(sess("a", d(11, 3)), sess("b", d(11, 12)), sess("c", d(10, 25)))
        val i = computeInsights(input(cur = cur2, prev = prev2, sessions = s.map { sws(it) }))
        assertEquals(2, i.sessions.current); assertEquals(1, i.sessions.previous)
        assertEquals(1.0, i.sessions.delta!!, 1e-9)
        val b = listOf(body(d(10, 20), 78.0), body(d(11, 14), 76.0), body(d(11, 3), 77.0))
        assertEquals(-2.0, computeInsights(input(cur = cur2, prev = prev2, body = b)).weight.delta!!, 1e-9)
    }

    private fun input(
        cur: DateRange = cur1, prev: DateRange = prev1, plans: List<Plan> = emptyList(),
        sessions: List<SessionWithSets> = emptyList(), macros: List<MacroDay> = emptyList(),
        body: List<BodyEntry> = emptyList(), target: Int = 150,
    ) = InsightInput(today, cur, prev, plans, sessions, macros, body, target)
}
