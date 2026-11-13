package com.lifehub.app.domain

import com.lifehub.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AchievementEngineTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 12) // Thursday
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private var n = 0

    private fun session(date: LocalDate, planId: String? = null, id: String = "s${n++}") =
        WorkoutSession(id, planId, "P", date, date.atTime(18, 0).toInstant(zone), null, 3000, SessionStatus.COMPLETED, null, null, false, 0, null)

    private fun set(s: WorkoutSession, ex: String, reps: Int? = null, secs: Int? = null, w: Double? = null, idx: Int = 0,
                    status: SetStatus = SetStatus.DONE, order: Int = 0) =
        SessionSet("${s.id}-$ex-$idx-${n++}", s.id, ex, "Ex $ex", order, idx, if (secs != null) TargetType.SECONDS else TargetType.REPS, 5,
            reps, secs, w, status, null, 90)

    private fun hist(sessions: List<WorkoutSession>, sets: List<SessionSet>, plans: List<Plan> = emptyList(),
                     macros: List<MacroDay> = emptyList(), goals: List<Goal> = emptyList(), target: Int = 150, t: LocalDate = today) =
        AchievementHistory(t, sessions, sets, plans, macros, goals, target, zone)

    private fun kinds(c: List<AchievementCandidate>, k: AchievementKind) = c.filter { it.kind == k }

    // ---- PRs ----

    @Test fun firstEverSetIsNotAPr() {
        val s = session(today.minusDays(10))
        assertTrue(AchievementEngine.prs(hist(listOf(s), listOf(set(s, "pu", reps = 8)))).isEmpty())
        assertTrue(AchievementEngine.liveHits(set(s, "pu", reps = 8), Bests()).isEmpty())
    }

    @Test fun repsPrNeedsStrictlyMoreThanPriorBest() {
        val s1 = session(today.minusDays(20)); val s2 = session(today.minusDays(10)); val s3 = session(today.minusDays(5))
        val sets = listOf(set(s1, "pu", reps = 8), set(s2, "pu", reps = 8), set(s3, "pu", reps = 9))
        val prs = AchievementEngine.prs(hist(listOf(s1, s2, s3), sets))
        assertEquals(1, prs.size)
        val p = prs.single()
        assertEquals(AchievementKind.PR_REPS, p.kind); assertEquals(9.0, p.value!!, 0.0)
        assertEquals("PR_REPS:ex=pu:value=9", p.dedupeKey); assertEquals(s3.id, p.sessionId); assertEquals(s3.date, p.achievedOn)
        assertEquals("Ex pu: 9 reps in one set", p.title)
    }

    @Test fun onlyBestPerExercisePerSession() {
        val s1 = session(today.minusDays(20)); val s2 = session(today.minusDays(10))
        val sets = listOf(set(s1, "pu", reps = 8), set(s2, "pu", reps = 10, idx = 0), set(s2, "pu", reps = 11, idx = 1), set(s2, "pu", reps = 9, idx = 2))
        val prs = AchievementEngine.prs(hist(listOf(s1, s2), sets))
        assertEquals(listOf(11.0), prs.map { it.value })
    }

    @Test fun weightedRepsDoNotCountAsRepsPr() {
        val s1 = session(today.minusDays(20)); val s2 = session(today.minusDays(10))
        val sets = listOf(set(s1, "pu", reps = 8), set(s2, "pu", reps = 12, w = 5.0))
        val prs = AchievementEngine.prs(hist(listOf(s1, s2), sets))
        assertEquals(listOf(AchievementKind.PR_WEIGHT), prs.map { it.kind })
        assertEquals("Ex pu: +5 kg", prs[0].title)
        assertEquals("PR_WEIGHT:ex=pu:value=5", prs[0].dedupeKey)
    }

    @Test fun holdPr() {
        val s1 = session(today.minusDays(20)); val s2 = session(today.minusDays(10))
        val prs = AchievementEngine.prs(hist(listOf(s1, s2), listOf(set(s1, "fl", secs = 8), set(s2, "fl", secs = 12))))
        assertEquals(AchievementKind.PR_HOLD, prs.single().kind); assertEquals("Ex fl hold: 12 s", prs.single().title)
    }

    @Test fun skippedAndPendingSetsIgnored() {
        val s1 = session(today.minusDays(20)); val s2 = session(today.minusDays(10))
        val sets = listOf(set(s1, "pu", reps = 8), set(s2, "pu", reps = 20, status = SetStatus.SKIPPED), set(s2, "pu", reps = 30, status = SetStatus.PENDING, idx = 1))
        assertTrue(AchievementEngine.prs(hist(listOf(s1, s2), sets)).isEmpty())
    }

    @Test fun inProgressSessionsIgnored() {
        val s1 = session(today.minusDays(20)); val s2 = session(today.minusDays(10)).copy(status = SessionStatus.IN_PROGRESS)
        assertTrue(AchievementEngine.backfill(hist(listOf(s1, s2), listOf(set(s1, "pu", reps = 8), set(s2, "pu", reps = 12)))).isEmpty())
    }

    @Test fun liveHitsUsesEarlierSetsOfSameSession() {
        val s = session(today)
        val prior = Bests.of(listOf(set(session(today.minusDays(9)), "pu", reps = 8)))
        val first = set(s, "pu", reps = 9)
        assertEquals(listOf(AchievementKind.PR_REPS), AchievementEngine.liveHits(first, prior).map { it.kind })
        val second = set(s, "pu", reps = 9, idx = 1)
        assertTrue("equal to earlier set of this session is not a PR", AchievementEngine.liveHits(second, prior.plus(first)).isEmpty())
        val third = set(s, "pu", reps = 10, idx = 2)
        assertEquals("New PR · 10 reps Ex pu", AchievementEngine.liveHits(third, prior.plus(first)).single().bannerText)
    }

    // ---- milestones ----

    @Test fun sessionMilestones() {
        val ss = (1..26).map { session(today.minusDays(100L - it)) }
        val ms = AchievementEngine.milestones(hist(ss, emptyList()))
        assertEquals(listOf("SESSION_MILESTONE:10", "SESSION_MILESTONE:25"), ms.map { it.dedupeKey })
        assertEquals(ss[9].id, ms[0].sessionId); assertEquals(ss[9].date, ms[0].achievedOn)
        val many = (1..210).map { session(LocalDate.of(2025, 1, 1).plusDays(it.toLong())) }
        assertEquals(listOf(10, 25, 50, 100, 200), AchievementEngine.milestones(hist(many, emptyList())).map { it.value!!.toInt() })
    }

    // ---- perfect week / month ----

    private val planA = Plan("A", "A", setOf(1, 3), null, false, created)
    private val planB = Plan("B", "B", setOf(2), null, false, created)

    @Test fun perfectWeek() {
        val mon = LocalDate.of(2026, 11, 2)
        val ss = listOf(session(mon, "A"), session(mon.plusDays(1), "B"), session(mon.plusDays(2), "A"))
        val c = AchievementEngine.perfectWeeks(hist(ss, emptyList(), plans = listOf(planA, planB)))
        assertEquals(1, c.size)
        assertEquals("PERFECT_WEEK:2026-W45", c[0].dedupeKey); assertEquals(mon.plusDays(6), c[0].achievedOn)
    }

    @Test fun missedSlotOrWrongPlanBreaksWeek() {
        val mon = LocalDate.of(2026, 11, 2)
        val missing = listOf(session(mon, "A"), session(mon.plusDays(1), "B"))
        assertTrue(AchievementEngine.perfectWeeks(hist(missing, emptyList(), plans = listOf(planA, planB))).isEmpty())
        val wrongPlan = missing + session(mon.plusDays(2), "B")
        assertTrue(AchievementEngine.perfectWeeks(hist(wrongPlan, emptyList(), plans = listOf(planA, planB))).isEmpty())
    }

    @Test fun weekNeedsTwoPlannedAndMustBeComplete() {
        val single = Plan("S", "S", setOf(1), null, false, created)
        val mon = LocalDate.of(2026, 11, 2)
        assertTrue(AchievementEngine.perfectWeeks(hist(listOf(session(mon, "S")), emptyList(), plans = listOf(single))).isEmpty())
        // current (incomplete) week never counts, even if everything so far was done
        val cur = LocalDate.of(2026, 11, 9)
        val ss = listOf(session(cur, "A"), session(cur.plusDays(1), "B"), session(cur.plusDays(2), "A"))
        assertTrue(AchievementEngine.perfectWeeks(hist(ss, emptyList(), plans = listOf(planA, planB))).isEmpty())
    }

    @Test fun archivedPlansAndPlansCreatedLaterAreNotSlots() {
        val mon = LocalDate.of(2026, 11, 2)
        val archived = planB.copy(isArchived = true)
        val ss = listOf(session(mon, "A"), session(mon.plusDays(2), "A"))
        assertEquals(1, AchievementEngine.perfectWeeks(hist(ss, emptyList(), plans = listOf(planA, archived))).size)
        val late = planB.copy(createdAt = Instant.parse("2026-11-04T00:00:00Z"))  // created after that week's Tuesday
        assertEquals(1, AchievementEngine.perfectWeeks(hist(ss, emptyList(), plans = listOf(planA, late))).size)
    }

    @Test fun perfectMonth() {
        val p = Plan("M", "M", setOf(1, 4), null, false, created) // Mon+Thu; October 2026: 4 Mondays (5,12,19,26), 5 Thursdays (1,8,15,22,29)
        val oct = (1..31).map { LocalDate.of(2026, 10, it) }.filter { it.dayOfWeek.value in setOf(1, 4) }
        val ss = oct.map { session(it, "M") }
        val c = AchievementEngine.perfectMonths(hist(ss, emptyList(), plans = listOf(p)))
        assertEquals(listOf("PERFECT_MONTH:2026-10"), c.map { it.dedupeKey }); assertEquals(9.0, c[0].value!!, 0.0)
        assertTrue(AchievementEngine.perfectMonths(hist(ss.drop(1), emptyList(), plans = listOf(p))).isEmpty())
        // November is still running
        val nov = (1..30).map { LocalDate.of(2026, 11, it) }.filter { it.dayOfWeek.value in setOf(1, 4) && it < today }
        assertTrue(AchievementEngine.perfectMonths(hist(nov.map { session(it, "M") }, emptyList(), plans = listOf(p))).isEmpty())
    }

    // ---- protein week ----

    private fun macro(d: LocalDate, p: Int) = MacroDay(d, p, 200, 70, 2000, true, created)

    @Test fun proteinWeek() {
        val mon = LocalDate.of(2026, 11, 2)
        val full = (0..6).map { macro(mon.plusDays(it.toLong()), 150) }
        val c = AchievementEngine.proteinWeeks(hist(emptyList(), emptyList(), macros = full))
        assertEquals(listOf("PROTEIN_WEEK:2026-W45"), c.map { it.dedupeKey })
        assertTrue(AchievementEngine.proteinWeeks(hist(emptyList(), emptyList(), macros = full.drop(1))).isEmpty())
        assertTrue(AchievementEngine.proteinWeeks(hist(emptyList(), emptyList(), macros = full.map { if (it.date.dayOfMonth == 4) it.copy(proteinG = 149) else it })).isEmpty())
        val cur = (0..6).map { macro(LocalDate.of(2026, 11, 9).plusDays(it.toLong()), 200) }
        assertTrue("incomplete week", AchievementEngine.proteinWeeks(hist(emptyList(), emptyList(), macros = cur)).isEmpty())
    }

    // ---- goals + backfill ----

    @Test fun goalsAndIdempotentBackfill() {
        val goal = Goal("g1", "Muscle Up", null, today.minusDays(80), today.minusDays(5), false, GoalState.ACHIEVED, today.minusDays(7), created)
        val active = goal.copy(id = "g2", state = GoalState.ACTIVE, achievedAt = null)
        val s1 = session(today.minusDays(20)); val s2 = session(today.minusDays(10))
        val h = hist(listOf(s1, s2), listOf(set(s1, "pu", reps = 8), set(s2, "pu", reps = 9)), goals = listOf(goal, active))
        val a = AchievementEngine.backfill(h)
        assertEquals(setOf("GOAL:g1", "PR_REPS:ex=pu:value=9"), a.map { it.dedupeKey }.toSet())
        assertEquals(a, AchievementEngine.backfill(h))
        assertEquals(a.size, a.map { it.dedupeKey }.toSet().size)
        val big = a.first { it.kind == AchievementKind.GOAL_ACHIEVED }
        assertEquals("Muscle Up", big.title); assertEquals(today.minusDays(7), big.achievedOn); assertTrue(big.kind.isBig)
    }
}
