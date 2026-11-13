package com.lifehub.app.domain

import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.SessionStatus
import com.lifehub.app.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TrainingStreakTest {
    private val today = LocalDate.of(2026, 11, 12)                       // Thursday
    private val plan = Plan("p", "Full body", setOf(1, 3, 5), null, false, Instant.parse("2026-01-01T00:00:00Z")) // Mon/Wed/Fri
    private fun s(d: LocalDate, st: SessionStatus = SessionStatus.COMPLETED) =
        WorkoutSession(d.toString(), "p", "Full body", d, Instant.EPOCH, null, null, st, null, null, false, 0, null)
    private fun streak(vararg trained: LocalDate, plans: List<Plan> = listOf(plan)) = Streak(trained.map { s(it) }, plans, today, ZoneOffset.UTC)

    private val mon = LocalDate.of(2026, 11, 9); private val wed = mon.plusDays(2); private val fri = mon.minusDays(3)

    @Test fun restDaysKeepTheStreak() {
        // Fri trained, Sat/Sun rest, Mon trained, Tue rest, Wed trained, Thu (today) rest -> Fri..Thu = 7 days.
        assertEquals(7, streak(fri, mon, wed).current)
    }

    @Test fun missedPlannedDayBreaksIt() {
        // Mon skipped and Tue (rest) not used to make it up -> Mon MISSED; only Tue (rest), Wed, Thu.
        assertEquals(3, streak(fri, wed).current)
        assertEquals(StreakDay.MISSED, streak(fri, wed).day(mon))
    }

    @Test fun plannedTodayNotDoneYetIsPending() {
        val wedToday = Streak(listOf(s(fri), s(mon)), listOf(plan), wed, ZoneOffset.UTC)
        assertEquals(StreakDay.PENDING, wedToday.day(wed))
        assertEquals(5, wedToday.current)                                 // Fri..Tue, today not counted yet
        assertFalse(wedToday.extendedToday)
    }

    @Test fun trainingTodayExtendsIt() {
        val wedToday = Streak(listOf(s(fri), s(mon), s(wed)), listOf(plan), wed, ZoneOffset.UTC)
        assertEquals(6, wedToday.current); assertTrue(wedToday.extendedToday)
    }

    @Test fun noHistoryIsZeroAndInProgressDoesNotCount() {
        assertEquals(0, streak().current)
        assertEquals(0, Streak(listOf(s(today, SessionStatus.IN_PROGRESS)), listOf(plan), today, ZoneOffset.UTC).current)
    }

    @Test fun daysBeforeAPlanExistedAreRest() {
        val newPlan = plan.copy(createdAt = Instant.parse("2026-11-10T00:00:00Z"))
        // Mon 9 was "planned" by weekday but the plan didn't exist yet -> rest.
        assertEquals(StreakDay.REST, streak(fri, wed, plans = listOf(newPlan)).day(mon))
        assertEquals(7, streak(fri, wed, plans = listOf(newPlan)).current)
    }

    @Test fun archivedPlansDoNotSchedule() =
        assertEquals(7, streak(fri, plans = listOf(plan.copy(isArchived = true))).current)

    // ---- grace rule: a skipped planned day is forgiven by a workout on the next day ----

    private val tue = mon.plusDays(1)
    private val monTue = Plan("mt", "Upper", setOf(1, 2), null, false, Instant.parse("2026-01-01T00:00:00Z"))

    @Test fun skippedPlannedDayForgivenByNextDayWorkout() {
        // Mon skipped, made up on Tue: Mon FORGIVEN, Tue TRAINED, both count -> Fri..Thu = 7, same as never skipping.
        val st = streak(fri, tue, wed)
        assertEquals(StreakDay.FORGIVEN, st.day(mon))
        assertEquals(StreakDay.TRAINED, st.day(tue))
        assertEquals(7, st.current)
    }

    @Test fun yesterdaySkippedIsPendingUntilTodayEnds() {
        // Today Tue (rest), Mon skipped: still pending, not counted, not broken. Tue rest + Sun/Sat/Fri = 4.
        val open = Streak(listOf(s(fri)), listOf(plan), tue, ZoneOffset.UTC)
        assertEquals(StreakDay.PENDING, open.day(mon))
        assertTrue(open.yesterdayPending)
        assertEquals(4, open.current)
        // Training today forgives Monday: Fri..Tue = 5.
        assertEquals(5, Streak(listOf(s(fri), s(tue)), listOf(plan), tue, ZoneOffset.UTC).current)
    }

    @Test fun graceExpiredBreaksIt() {
        // Today Wed (planned, not done yet): Mon skipped and Tue without workout -> Mon MISSED. Only Tue (rest) counts.
        val st = Streak(listOf(s(fri)), listOf(plan), wed, ZoneOffset.UTC)
        assertEquals(StreakDay.MISSED, st.day(mon))
        assertEquals(StreakDay.PENDING, st.day(wed))
        assertEquals(1, st.current)
    }

    @Test fun makeUpDayThatIsAlsoPlannedCoversBoth() {
        // Mon + Tue planned, Mon skipped, Tue trained: one session forgives Mon and fulfils Tue.
        val st = streak(fri, tue, plans = listOf(monTue))
        assertEquals(StreakDay.FORGIVEN, st.day(mon))
        assertEquals(7, st.current)
    }

    @Test fun twoPlannedDaysSkippedInARowBreakAtTheFirst() {
        // Mon + Tue planned and both skipped, Wed trained: Mon MISSED (Tue had nothing), Tue FORGIVEN by Wed.
        val st = streak(fri, wed, plans = listOf(monTue))
        assertEquals(StreakDay.MISSED, st.day(mon))
        assertEquals(StreakDay.FORGIVEN, st.day(tue))
        assertEquals(3, st.current)                                       // Tue, Wed, Thu
    }

    @Test fun todayAndYesterdayBothPending() {
        // Today Tue, Mon + Tue planned, nothing done: both pending -> Fri..Sun = 3, at risk, only today can save Mon.
        val st = Streak(listOf(s(fri)), listOf(monTue), tue, ZoneOffset.UTC)
        assertEquals(StreakDay.PENDING, st.day(mon)); assertEquals(StreakDay.PENDING, st.day(tue))
        assertEquals(3, st.current)
        assertTrue(st.atRiskToday); assertTrue(st.yesterdayPending)
    }

    @Test fun plannedDayBeforeFirstWorkoutIsOutside() {
        // Mon skipped before ever training, first workout Tue: Mon is NONE, the streak starts on Tue.
        val st = Streak(listOf(s(tue)), listOf(plan), wed, ZoneOffset.UTC)
        assertEquals(StreakDay.NONE, st.day(mon))
        assertEquals(StreakDay.PENDING, st.day(wed))
        assertEquals(1, st.current)
        assertFalse(st.yesterdayPending)
    }
}
