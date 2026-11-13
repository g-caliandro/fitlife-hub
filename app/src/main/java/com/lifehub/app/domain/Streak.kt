package com.lifehub.app.domain

import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.SessionStatus
import com.lifehub.app.data.model.WorkoutSession
import java.time.LocalDate
import java.time.ZoneId

/** How a single day relates to the training streak. */
enum class StreakDay {
    /** A workout was completed that day. Kept. */
    TRAINED,
    /** Nothing was planned that day. Kept. */
    REST,
    /** A planned workout was skipped, but a workout was completed the next day (make-up). Kept. */
    FORGIVEN,
    /**
     * A planned workout without a session whose grace period is still open: today, or yesterday while today has no
     * session yet. Not counted and does not break the streak (yet).
     */
    PENDING,
    /** A planned workout was skipped and the next day had no workout either: breaks the streak. */
    MISSED,
    /** Before the first ever completed workout (or in the future): outside any streak. */
    NONE,
}

/**
 * Training streak (Duolingo-style), shown only on Home. It replaces the old consistency achievements
 * (perfect weeks / months, session milestones).
 *
 * Rule, per day `d` from the first ever completed workout up to [today]:
 * - **TRAINED**: a workout was completed on `d` -> kept.
 * - **REST**: no active plan has `d`'s weekday (or the plan didn't exist yet) -> kept.
 * - **FORGIVEN** (grace rule): `d` was planned, had no workout, but `d + 1` has a completed workout -> kept.
 *   The make-up day itself is TRAINED, so both days count (+2). One session on `d + 1` is enough even when `d + 1` is
 *   also a planned day: it forgives `d` and fulfils `d + 1`.
 * - **PENDING**: `d` was planned without a workout and its grace is still open (`d` is today, or `d` is yesterday
 *   and today has no workout yet) -> neither counted nor breaking.
 * - **MISSED**: `d` was planned and neither `d` nor `d + 1` has a workout -> the streak breaks.
 *
 * [current] counts the kept days walking back from today, skipping PENDING days (only today and yesterday can be
 * pending) and stopping at the first MISSED or NONE day. Two planned days skipped in a row: the first is MISSED (the
 * second had no workout), the second can still be FORGIVEN, so the streak restarts from it.
 *
 * A day is planned when an active (not archived) plan has that ISO weekday and already existed then. The streak can't
 * start before the first completed workout, so a fresh install (or no history) is 0, not "infinite rest days".
 */
class Streak(sessions: List<WorkoutSession>, private val plans: List<Plan>, private val today: LocalDate, private val zone: ZoneId) {
    private val trained: Set<LocalDate> = sessions.filter { it.status == SessionStatus.COMPLETED }.map { it.date }.toSet()
    private val first: LocalDate? = trained.minOrNull()

    /** True when an active plan schedules a workout on [d]. */
    fun planned(d: LocalDate) = plans.any {
        !it.isArchived && d.dayOfWeek.value in it.weekdays && !d.isBefore(it.createdAt.atZone(zone).toLocalDate())
    }

    fun day(d: LocalDate): StreakDay {
        val next = d.plusDays(1)
        return when {
            d in trained -> StreakDay.TRAINED
            first == null || d.isBefore(first) || d.isAfter(today) -> StreakDay.NONE
            !planned(d) -> StreakDay.REST
            next in trained -> StreakDay.FORGIVEN
            d == today || next == today -> StreakDay.PENDING
            else -> StreakDay.MISSED
        }
    }

    /** Streak length: kept days ending today, pending days (today / yesterday) skipped. 0 when broken. */
    val current: Int by lazy {
        var d = today
        var n = 0
        while (true) {
            when (day(d)) {
                StreakDay.TRAINED, StreakDay.REST, StreakDay.FORGIVEN -> n++
                StreakDay.PENDING -> {}
                StreakDay.MISSED, StreakDay.NONE -> break
            }
            d = d.minusDays(1)
        }
        n
    }

    /** Today is a planned day with no workout yet: train today (or, if yesterday isn't pending too, tomorrow). */
    val atRiskToday: Boolean get() = day(today) == StreakDay.PENDING

    /** Yesterday was a skipped planned day: only a workout today can still forgive it. */
    val yesterdayPending: Boolean get() = day(today.minusDays(1)) == StreakDay.PENDING

    /** True when today's workout is what extended the streak (drives the celebration). */
    val extendedToday: Boolean get() = today in trained && current > 0

    /** Day states for [from]..[to] (celebration week strip). */
    fun days(from: LocalDate, to: LocalDate): Map<LocalDate, StreakDay> = buildMap {
        var d = from
        while (!d.isAfter(to)) { put(d, day(d)); d = d.plusDays(1) }
    }
}
