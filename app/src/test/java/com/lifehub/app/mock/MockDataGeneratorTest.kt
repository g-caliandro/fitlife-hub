package com.lifehub.app.mock

import com.lifehub.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MockDataGeneratorTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val zone = ZoneId.of("Europe/Paris")
    private val s = MockDataGenerator.generate(today, zone)

    @Test fun deterministic() = assertEquals(s, MockDataGenerator.generate(today, zone))

    @Test fun structure() {
        assertEquals(4, s.goals.size); assertEquals(4, s.plans.size); assertEquals(4, s.links.size)
        assertEquals(listOf(setOf(1), setOf(3), setOf(5), setOf(6)), s.plans.map { it.weekdays })
        assertEquals(listOf(DayType.PULL, DayType.PUSH, DayType.LEGS, DayType.CORE), s.plans.map { it.dayType })
        assertEquals(24, s.links[0].sessionsNeeded)
        assertEquals(today.minusDays(70), s.goals[0].startDate); assertEquals(today.plusDays(14), s.goals[0].deadline)
        assertTrue(s.sessions.none { it.date == today })
        for (p in s.plans) { val n = s.sessions.count { it.planId == p.id }; assertTrue("${p.name}=$n", n in 7..11) }
        assertTrue(s.sets.any { it.targetType == TargetType.MAX && it.actualReps != null })
        assertTrue(s.sets.any { it.targetType == TargetType.SECONDS && it.actualSeconds != null })
        assertTrue(s.sets.any { it.status == SetStatus.SKIPPED })
        assertTrue(s.sets.any { it.weightKg != null })
        val foodDays = s.foodEntries.map { it.date }.toSet()
        assertTrue("food days=${foodDays.size}", foodDays.size in 45..71)
        assertTrue(s.macros.size in 5..30)     // quick adds only
        assertTrue(s.foods.size >= 10 && s.foodEntries.all { e -> s.foods.any { it.id == e.foodId } })
        assertTrue(s.bodyEntries.any { it.date == today.minusDays(1) })
        val exIds = s.exercises.map { it.id }.toSet()
        assertTrue(s.sets.all { it.exerciseId in exIds }); assertTrue(s.planExercises.all { it.exerciseId in exIds })
    }

    @Test fun progression() {
        val pu = s.sets.filter { it.exerciseId == "ex-pull-up" && it.setIndex == 0 && it.actualReps != null }
        val sessionDate = s.sessions.associate { it.id to it.date }
        val sorted = pu.sortedBy { sessionDate[it.sessionId] }
        assertTrue(sorted.first().actualReps!! <= 8); assertTrue(sorted.last().actualReps!! >= 8)
    }

    @Test fun everyHomePeriodHasData() {
        for (days in listOf(7, 14)) for (k in 0..1) {
            val to = today.minusDays((days * k).toLong()); val from = to.minusDays(days - 1L)
            assertTrue("sessions $days/$k", s.sessions.any { it.date in from..to })
            assertTrue("food $days/$k", s.foodEntries.any { it.date in from..to })
            assertTrue("body $days/$k", s.bodyEntries.any { it.date in from..to })
        }
    }
}

class MockAchievementsTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val s = MockDataGenerator.generate(today, ZoneId.of("Europe/Paris"))

    @Test fun richLog() {
        val kinds = s.achievements.map { it.kind }.toSet()
        assertTrue(kinds.containsAll(setOf(AchievementKind.GOAL_ACHIEVED, AchievementKind.PR_REPS, AchievementKind.PR_HOLD,
            AchievementKind.PERFECT_MONTH_TRAINING, AchievementKind.PERFECT_WEEK_TRAINING, AchievementKind.PROTEIN_WEEK, AchievementKind.SESSION_MILESTONE)))
        assertEquals(s.achievements.size, s.achievements.map { it.dedupeKey }.toSet().size)
    }

    @Test fun olderAchievedGoalAndActiveDragonFlag() {
        val g = s.goals.first { it.name == "10 strict pull-ups" }
        assertEquals(GoalState.ACHIEVED, g.state); assertEquals(today.minusDays(63), g.achievedAt)
        assertTrue(s.achievements.any { it.dedupeKey == "GOAL:${g.id}" })
        assertEquals(GoalState.ACTIVE, s.goals.first { it.name.startsWith("Dragon Flag") }.state)
    }
}

class MockNutritionTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val s = MockDataGenerator.generate(today, ZoneId.of("Europe/Paris"))
    private val days = com.lifehub.app.domain.mergeNutrition(s.macros, s.foodEntries)

    @Test fun dailyLevelsStayRealistic() {
        val avgP = days.map { it.totals.proteinG }.average()
        val avgK = days.map { it.totals.kcal }.average()
        assertTrue("protein $avgP", avgP in 130.0..175.0)
        assertTrue("kcal $avgK", avgK in 2100.0..3100.0)
        assertTrue(days.count { it.entries.isNotEmpty() } > days.count { it.entries.isEmpty() })
    }

    @Test fun entriesAreConsistentSnapshots() {
        val byId = s.foods.associateBy { it.id }
        for (e in s.foodEntries) {
            val f = byId.getValue(e.foodId!!)
            assertEquals(f.protein100 * e.grams / 100, e.proteinG, 0.06)
            assertEquals(f.kcal100 * e.grams / 100, e.kcal, 0.06)
        }
        assertTrue(s.foods.any { it.favorite } && s.foods.any { it.lastUsedAt != null })
    }
}

/**
 * The Diary month header / day markers have something to show in the current and previous month, but only on a minority
 * of days: a few missed workouts, many trained days, some low-protein days and a few cheat days (> 115 % kcal).
 */
class MockDiaryDemoTest {
    @Test fun currentAndPreviousMonthHaveFlags() {
        val zone = ZoneId.of("Europe/Paris")
        for (today in listOf(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), LocalDate.of(2027, 3, 28))) {
            val s = MockDataGenerator.generate(today, zone)
            val days = com.lifehub.app.domain.mergeNutrition(s.macros, s.foodEntries)
            val flags = com.lifehub.app.feature.calendar.DiaryFlags.dayFlags(days, today, s.settings.proteinTargetG, s.settings.kcalTarget)
            val trained = s.sessions.map { it.date }.toSet()
            val missed = com.lifehub.app.feature.calendar.DiaryFlags.missedTrainingDays(s.plans, trained, today, zone)
            assertTrue(trained.none { it in missed })
            for (k in 0L..1L) {
                val sum = com.lifehub.app.feature.calendar.DiaryFlags.monthSummary(java.time.YearMonth.from(today).minusMonths(k), today, flags, missed)
                val msg = "$today -$k $sum"
                assertTrue(msg, sum.trainingMissed in 1..4)
                assertTrue(msg, sum.proteinMissed in 1..(sum.elapsedDays / 3 + 1))
                assertTrue(msg, sum.kcalExceeded in 1..(sum.elapsedDays / 6 + 1))
                val month = java.time.YearMonth.from(today).minusMonths(k)
                val trainedInMonth = trained.count { java.time.YearMonth.from(it) == month && it.isBefore(today) }
                assertTrue("$msg trained=$trainedInMonth", trainedInMonth >= sum.elapsedDays / 3 && trainedInMonth > sum.trainingMissed)
                // Cheat days are clearly over the line, not borderline.
                days.filter { java.time.YearMonth.from(it.date) == month && flags[it.date]?.kcalExceeded == true }
                    .forEach { assertTrue("$msg ${it.date} ${it.totals.kcal}", it.totals.kcal > s.settings.kcalTarget!! * 1.18) }
            }
            // Stars to show: a silver one (active goal due this month or next) and a gold one (reached this month or last).
            val due = com.lifehub.app.feature.calendar.DiaryFlags.goalsDue(s.goals, today)
            assertTrue("$today goals due", (0L..1L).any { com.lifehub.app.feature.calendar.DiaryFlags.goalsIn(java.time.YearMonth.from(today).plusMonths(it), due).isNotEmpty() })
            val reached = com.lifehub.app.feature.calendar.DiaryFlags.goalsReached(s.goals)
            assertTrue("$today goals reached", (0L..1L).any { com.lifehub.app.feature.calendar.DiaryFlags.goalsIn(java.time.YearMonth.from(today).minusMonths(it), reached).isNotEmpty() })
        }
    }
}
