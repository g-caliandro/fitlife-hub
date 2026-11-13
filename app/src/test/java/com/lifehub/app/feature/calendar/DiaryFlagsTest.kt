package com.lifehub.app.feature.calendar

import com.lifehub.app.data.model.Plan
import com.lifehub.app.domain.DayNutrition
import com.lifehub.app.domain.Totals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class DiaryFlagsTest {
    private val today = LocalDate.of(2026, 11, 12) // Thursday
    private fun day(d: LocalDate, p: Double, k: Double) = DayNutrition(d, Totals(kcal = k, proteinG = p), emptyList(), null)

    @Test fun flagsOnlyFinishedLoggedDays() {
        val f = DiaryFlags.dayFlags(
            listOf(
                day(today.minusDays(5), 150.0, 2800.0), // slightly over kcal (108 %) -> not a cheat day
                day(today.minusDays(4), 150.0, 2990.0), // exactly 115 % -> not a cheat day
                day(today.minusDays(3), 120.0, 2000.0), // protein missed
                day(today.minusDays(2), 160.0, 2991.0), // cheat day (> 115 %)
                day(today.minusDays(1), 150.0, 2600.0), // exactly on targets -> no flag
                day(today, 10.0, 4000.0),               // today unfinished -> no flag
            ),
            today, proteinTarget = 150, kcalTarget = 2600,
        )
        assertEquals(mapOf(today.minusDays(3) to DayFlags(true, false), today.minusDays(2) to DayFlags(false, true)), f)
        assertEquals(115, DiaryFlags.CHEAT_DAY_KCAL_PERCENT)
        assertTrue(DiaryFlags.isCheatDay(2991, 2600)); assertTrue(!DiaryFlags.isCheatDay(2990, 2600)); assertTrue(!DiaryFlags.isCheatDay(5000, 0))
        // No kcal target -> never "exceeded"; days without data never appear.
        assertTrue(DiaryFlags.dayFlags(listOf(day(today.minusDays(2), 160.0, 9000.0)), today, 150, null).isEmpty())
    }

    @Test fun missedTrainingAndMonthSummary() {
        val created = Instant.parse("2026-11-02T08:00:00Z") // Monday
        val plans = listOf(
            Plan("a", "Pull", setOf(1, 3), null, false, created),             // Mon + Wed
            Plan("b", "Core", setOf(3), null, false, created),                // Wed again: one day, counted once
            Plan("x", "Old", setOf(2), null, true, created),                  // archived: ignored
        )
        // Scheduled before today: Nov 2, 4, 9, 11. Done: Nov 4 and 9.
        val missed = DiaryFlags.missedTrainingDays(plans, setOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 9)), today, ZoneOffset.UTC)
        assertEquals(setOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 11)), missed)

        val flags = mapOf(
            LocalDate.of(2026, 11, 3) to DayFlags(true, true), LocalDate.of(2026, 11, 5) to DayFlags(true, false),
            LocalDate.of(2026, 10, 30) to DayFlags(true, false), // other month
        )
        assertEquals(MonthSummary(2, 2, 1, 11), DiaryFlags.monthSummary(YearMonth.of(2026, 11), today, flags, missed))
        assertEquals(MonthSummary(0, 1, 0, 31), DiaryFlags.monthSummary(YearMonth.of(2026, 10), today, flags, missed))
        assertEquals(0, DiaryFlags.monthSummary(YearMonth.of(2026, 12), today, flags, missed).elapsedDays)
    }

    @Test fun goalStars() {
        val at = Instant.parse("2026-11-01T08:00:00Z")
        fun goal(id: String, name: String, due: LocalDate, st: com.lifehub.app.data.model.GoalState) =
            com.lifehub.app.data.model.Goal(id, name, null, LocalDate.of(2026, 9, 1), due, false, st, null, at)
        val d1 = LocalDate.of(2026, 11, 26); val d2 = LocalDate.of(2026, 12, 3)
        val reachedOn = LocalDate.of(2026, 11, 3)
        val goals = listOf(
            goal("a", "Muscle Up", d1, com.lifehub.app.data.model.GoalState.ACTIVE),
            goal("b", "Dragon Flag", d2, com.lifehub.app.data.model.GoalState.ACTIVE),
            goal("e", "Overdue", today.minusDays(1), com.lifehub.app.data.model.GoalState.ACTIVE), // deadline passed: no star
            goal("t", "Today", today, com.lifehub.app.data.model.GoalState.ACTIVE),                // due today: silver
            goal("c", "Old", d1, com.lifehub.app.data.model.GoalState.ACHIEVED).copy(achievedAt = reachedOn),
            goal("n", "No date", d1, com.lifehub.app.data.model.GoalState.ACHIEVED),               // no achievedAt: no gold star
            goal("d", "Gone", d1, com.lifehub.app.data.model.GoalState.ARCHIVED),
        )
        val due = DiaryFlags.goalsDue(goals, today)
        assertEquals(setOf(today, d1, d2), due.keys)
        assertEquals(listOf("a"), due.getValue(d1).map { it.id })
        assertEquals(listOf("t", "a"), DiaryFlags.goalsIn(YearMonth.of(2026, 11), due).map { it.id })
        assertTrue(DiaryFlags.goalsIn(YearMonth.of(2026, 10), due).isEmpty())
        val reached = DiaryFlags.goalsReached(goals)
        assertEquals(mapOf(reachedOn to listOf("c")), reached.mapValues { (_, g) -> g.map { it.id } })
    }
}
