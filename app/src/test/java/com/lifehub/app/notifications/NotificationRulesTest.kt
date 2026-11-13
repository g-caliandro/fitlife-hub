package com.lifehub.app.notifications

import com.lifehub.app.data.model.*
import com.lifehub.app.data.prefs.Settings
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class NotificationRulesTest {
    private val mon = LocalDate.of(2026, 11, 9) // Monday
    private val t0 = Instant.EPOCH

    private fun plan(id: String, name: String, vararg wd: Int, archived: Boolean = false) = Plan(id, name, wd.toSet(), null, archived, t0)
    private fun sess(d: LocalDate) = WorkoutSession("s$d", null, "P", d, t0, t0, 100, SessionStatus.COMPLETED, null, null, false, 0, null)
    private fun macro(d: LocalDate, p: Int) = MacroDay(d, p, 200, 70, 2000, true, t0)
    private fun input(
        today: LocalDate = mon, settings: Settings = Settings(), last: Map<NotifType, LocalDate?> = emptyMap(),
        plans: List<Plan> = emptyList(), completed: List<WorkoutSession> = emptyList(), inProg: Boolean = false,
        macros: List<MacroDay> = emptyList(), body: LocalDate? = today, earliest: LocalDate? = null,
    ) = RuleInput(today, settings, last, plans, completed, inProg, macros, body, earliest)

    @Test fun trainDayPlanSinglePlanPreselected() {
        val n = NotificationRules.evaluate(input(plans = listOf(plan("a", "Pull", 1))))
        assertEquals(1, n.size)
        assertEquals("Training day — Pull. You haven't trained yet today.", n[0].text)
        assertTrue(n[0].route.contains("planId=a") || n[0].route.endsWith("a"))
    }
    @Test fun trainSeveralPlansNoPlanId() {
        val n = NotificationRules.train(input(plans = listOf(plan("a", "Zed", 1), plan("b", "Alpha", 1))))!!
        assertTrue(n.text.contains("Alpha")); assertFalse(n.route.contains("a") && n.route.contains("planId"))
    }
    @Test fun trainSuppressedWhenTrainedOrInProgressOrArchived() {
        assertNull(NotificationRules.train(input(plans = listOf(plan("a", "P", 1)), completed = listOf(sess(mon)))))
        assertNull(NotificationRules.train(input(plans = listOf(plan("a", "P", 1)), inProg = true)))
        assertNull(NotificationRules.train(input(plans = listOf(plan("a", "P", 1, archived = true)))))
    }
    @Test fun trainIdleDays() {
        val n = NotificationRules.train(input(completed = listOf(sess(mon.minusDays(3)))))!!
        assertEquals("3 days without training. Time to move?", n.text)
        assertNull(NotificationRules.train(input(completed = listOf(sess(mon.minusDays(2))))))
        assertNull(NotificationRules.train(input(completed = emptyList())))
    }
    @Test fun trainAPrecedesB() {
        val n = NotificationRules.train(input(plans = listOf(plan("a", "P", 1)), completed = listOf(sess(mon.minusDays(5)))))!!
        assertTrue(n.text.startsWith("Training day"))
    }

    private fun week(start: LocalDate, p: Int, n: Int = 7) = (0 until n).map { macro(start.plusDays(it.toLong()), p) }
    @Test fun proteinBelowTarget() {
        val n = NotificationRules.protein(input(macros = week(mon.minusDays(7), 120)))!!
        assertEquals("Last week avg protein was 120 g/day (target 150 g).", n.text)
        assertEquals("trends?period=1W&offset=1", n.route)
    }
    @Test fun proteinDroppedVsPrevious() {
        val m = week(mon.minusDays(14), 200) + week(mon.minusDays(7), 160)
        val n = NotificationRules.protein(input(macros = m))!!
        assertEquals("Last week avg protein was 160 g/day, down from 200 g/day the week before.", n.text)
    }
    @Test fun proteinNoneWhenFineFewDaysOrNotMonday() {
        assertNull(NotificationRules.protein(input(macros = week(mon.minusDays(7), 155))))
        assertNull(NotificationRules.protein(input(macros = week(mon.minusDays(7), 100, 2))))
        assertNull(NotificationRules.protein(input(today = mon.plusDays(1), macros = week(mon.minusDays(6), 100))))
    }

    @Test fun weightThresholds() {
        fun fires(days: Long) = NotificationRules.weight(input(body = mon.minusDays(days))) != null
        assertFalse(fires(6)); assertTrue(fires(7)); assertFalse(fires(8)); assertTrue(fires(14)); assertFalse(fires(15))
        assertTrue(fires(21)); assertTrue(fires(28)); assertFalse(fires(20))
        assertEquals("You haven't logged your weight for 1 week — weigh yourself.", NotificationRules.weight(input(body = mon.minusDays(7)))!!.text)
        assertEquals("You haven't logged your weight for 3 weeks — weigh yourself.", NotificationRules.weight(input(body = mon.minusDays(21)))!!.text)
    }
    @Test fun weightNoEntries() {
        assertNull(NotificationRules.weight(input(body = null, earliest = null)))
        val n = NotificationRules.weight(input(body = null, earliest = mon.minusDays(14)))!!
        assertEquals("You haven't logged your weight yet — weigh yourself.", n.text)
    }

    @Test fun togglesAndDailyCap() {
        val base = input(body = mon.minusDays(7), plans = listOf(plan("a", "P", 1)), macros = week(mon.minusDays(7), 100))
        assertEquals(3, NotificationRules.evaluate(base).size)
        val off = base.copy(settings = Settings(notifyTrain = false, notifyWeight = false))
        assertEquals(listOf(NotifType.PROTEIN), NotificationRules.evaluate(off).map { it.type })
        val capped = base.copy(lastNotified = mapOf(NotifType.TRAIN to mon, NotifType.WEIGHT to mon.minusDays(1)))
        assertEquals(setOf(NotifType.PROTEIN, NotifType.WEIGHT), NotificationRules.evaluate(capped).map { it.type }.toSet())
    }

    @Test fun delayUntilNext() {
        val z = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 11, 9, 10, 0, 0, 0, z)
        assertEquals(Duration.ofHours(8), NotificationRules.delayUntilNext(now, LocalTime.of(18, 0)))
        assertEquals(Duration.ofHours(16), NotificationRules.delayUntilNext(now, LocalTime.of(2, 0)))
        assertEquals(Duration.ofHours(24), NotificationRules.delayUntilNext(now, LocalTime.of(10, 0)))
    }

    // ---- achievements (F-ACH-5) ----

    private fun ach(id: String, kind: AchievementKind, on: LocalDate, title: String = id, value: Double? = null, unit: String? = null, ex: String? = null) =
        Achievement(id, kind, title, null, value, unit, ex, null, null, on, t0, "k-$id")

    private val today = LocalDate.of(2026, 11, 12)
    private fun ainput(
        achievements: List<Achievement> = emptyList(), unlocked: List<Achievement> = emptyList(), sent: List<LocalDate> = emptyList(),
        settings: Settings = Settings(), last: Map<NotifType, LocalDate?> = emptyMap(), day: LocalDate = today,
    ) = input(today = day, settings = settings, last = last).copy(achievements = achievements, unlocked = unlocked, achievementSentDates = sent)

    @Test fun anniversaryOneMonthSixMonthsOneYear() {
        for ((on, label) in listOf(LocalDate.of(2026, 10, 12) to "1 month", LocalDate.of(2026, 5, 12) to "6 months", LocalDate.of(2025, 11, 12) to "1 year")) {
            val n = NotificationRules.achievementMemory(ainput(listOf(ach("a", AchievementKind.PR_REPS, on, "12 pull-ups"))))!!
            assertEquals("$label ago today: 12 pull-ups", n.text); assertEquals(NotifType.ACHIEVEMENT_MEMORY, n.type); assertEquals("achievements", n.route)
        }
        assertNull(NotificationRules.achievementMemory(ainput(listOf(ach("a", AchievementKind.PR_REPS, LocalDate.of(2026, 10, 13))))))
        assertNull("2 months is not an anniversary", NotificationRules.achievementMemory(ainput(listOf(ach("a", AchievementKind.PR_REPS, LocalDate.of(2026, 9, 12))))))
    }

    @Test fun bigAnniversaryWinsAndSaysAchieved() {
        val on = LocalDate.of(2026, 10, 12)
        val n = NotificationRules.achievementMemory(ainput(listOf(
            ach("pr", AchievementKind.PR_REPS, on, "10 dips"), ach("goal", AchievementKind.GOAL_ACHIEVED, on, "Muscle Up"),
        )))!!
        assertTrue(n.text.startsWith("1 month ago today: Muscle Up achieved"))
    }

    @Test fun unlockedBeatsAnniversaryAndCarriesKey() {
        val u = ach("w", AchievementKind.PROTEIN_WEEK, today.minusDays(3), "Protein target hit all week")
        val n = NotificationRules.achievementMemory(ainput(listOf(ach("a", AchievementKind.PR_REPS, LocalDate.of(2026, 10, 12))), unlocked = listOf(u)))!!
        assertEquals("Achievement unlocked", n.title); assertEquals("Protein target hit all week", n.text); assertEquals("k-w", n.key)
    }

    @Test fun capOnePerDayAndTwoPerWeekAndToggle() {
        val a = listOf(ach("a", AchievementKind.PR_REPS, LocalDate.of(2026, 10, 12)))
        assertTrue(NotificationRules.evaluate(ainput(a)).any { it.type == NotifType.ACHIEVEMENT_MEMORY })
        assertTrue(NotificationRules.evaluate(ainput(a, last = mapOf(NotifType.ACHIEVEMENT_MEMORY to today))).none { it.type == NotifType.ACHIEVEMENT_MEMORY })
        assertNull(NotificationRules.achievementMemory(ainput(a, sent = listOf(today.minusDays(1), today.minusDays(3)))))
        assertNotNull(NotificationRules.achievementMemory(ainput(a, sent = listOf(today.minusDays(1)))))
        assertNotNull(NotificationRules.achievementMemory(ainput(a, sent = listOf(today.minusDays(8), today.minusDays(9)))))
        assertTrue(NotificationRules.evaluate(ainput(a, settings = Settings(notifyAchievements = false))).none { it.type == NotifType.ACHIEVEMENT_MEMORY })
    }

    @Test fun prMemoryHasProgressContextAndRespectsWeeklyGap() {
        val old = ach("old", AchievementKind.PR_HOLD, LocalDate.of(2026, 3, 12), "Front lever hold: 8 s", 8.0, "s", "fl")
        val best = ach("best", AchievementKind.PR_HOLD, LocalDate.of(2026, 5, 1), "Front lever hold: 15 s", 15.0, "s", "fl")
        val all = listOf(old, best)
        // deterministic pseudo-random gate: find a day on which it fires
        val fired = (0 until 49).mapNotNull { d ->
            val day = today.plusDays(d.toLong())
            NotificationRules.achievementMemory(ainput(all, day = day))?.let { day to it }
        }
        assertTrue("fires occasionally", fired.isNotEmpty() && fired.size < 49)
        val texts = fired.map { it.second.text }
        assertTrue(texts.toString(), texts.any { it.endsWith("Mar: Front lever hold: 8 s. Your best now: 15 s.") })
        assertTrue(fired.all { (day, _) -> NotificationRules.achievementMemory(ainput(all, sent = listOf(day.minusDays(2)), day = day)) == null })
        // recent PRs (<30 days) are never resurfaced
        assertNull(NotificationRules.achievementMemory(ainput(listOf(best.copy(achievedOn = today.minusDays(10))), day = today)))
    }
}
