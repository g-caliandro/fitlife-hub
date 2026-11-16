package com.lifehub.app.feature.calendar

import com.lifehub.app.data.model.Goal
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.Plan
import com.lifehub.app.domain.DayNutrition
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Calendar flags of one finished day. A day without any food/quick-add logged gets no flag at all.
 * [kcalExceeded] means a cheat day: kcal well above target (see [DiaryFlags.CHEAT_DAY_KCAL_PERCENT]), not a slight overshoot.
 */
data class DayFlags(val proteinMissed: Boolean, val kcalExceeded: Boolean) {
    val any get() = proteinMissed || kcalExceeded
}

/** Counts for the Diary month header (days up to yesterday only). */
data class MonthSummary(val trainingMissed: Int, val proteinMissed: Int, val kcalExceeded: Int, val elapsedDays: Int) {
    val allZero get() = trainingMissed == 0 && proteinMissed == 0 && kcalExceeded == 0
}

object DiaryFlags {
    /** A day is a cheat day when kcal > this percent of the kcal target (e.g. 2600 -> over 2990). */
    const val CHEAT_DAY_KCAL_PERCENT = 115

    /** True when [kcal] is strictly above [CHEAT_DAY_KCAL_PERCENT] % of [target] (integer math: no float rounding). */
    fun isCheatDay(kcal: Int, target: Int?): Boolean = target != null && target > 0 && kcal.toLong() * 100 > target.toLong() * CHEAT_DAY_KCAL_PERCENT

    /**
     * Flags for logged days strictly before [today] (today is unfinished):
     * protein missed = protein < target (target > 0); kcal exceeded = cheat day, see [isCheatDay] (only with a kcal target).
     */
    fun dayFlags(days: List<DayNutrition>, today: LocalDate, proteinTarget: Int, kcalTarget: Int?): Map<LocalDate, DayFlags> =
        days.asSequence().filter { it.date.isBefore(today) }.mapNotNull { n ->
            val f = DayFlags(
                // Only a fully known protein total can "miss": with n/a quick adds the sum is a lower bound.
                proteinMissed = proteinTarget > 0 && n.totals.proteinComplete && n.totals.proteinInt < proteinTarget,
                kcalExceeded = isCheatDay(n.totals.kcalInt, kcalTarget),
            )
            if (f.any) n.date to f else null
        }.toMap()

    /**
     * Days before [today] (from the earliest plan creation) scheduled by an active plan's weekdays on which no
     * session was completed. A day counts once even if several plans are scheduled on it.
     */
    fun missedTrainingDays(plans: List<Plan>, sessionDates: Set<LocalDate>, today: LocalDate, zone: ZoneId): Set<LocalDate> {
        val active = plans.filter { !it.isArchived && it.weekdays.isNotEmpty() }
            .map { it.weekdays to it.createdAt.atZone(zone).toLocalDate() }
        val first = active.minOfOrNull { it.second } ?: return emptySet()
        val out = HashSet<LocalDate>()
        var d = first
        while (d.isBefore(today)) {
            val dow = d.dayOfWeek.value
            if (d !in sessionDates && active.any { (wd, created) -> dow in wd && !d.isBefore(created) }) out += d
            d = d.plusDays(1)
        }
        return out
    }

    fun monthSummary(month: YearMonth, today: LocalDate, flags: Map<LocalDate, DayFlags>, missedTraining: Set<LocalDate>): MonthSummary {
        fun inScope(d: LocalDate) = YearMonth.from(d) == month && d.isBefore(today)
        val elapsed = when {
            !month.atDay(1).isBefore(today) -> 0
            month.atEndOfMonth().isBefore(today) -> month.lengthOfMonth()
            else -> today.dayOfMonth - 1
        }
        return MonthSummary(
            missedTraining.count(::inScope),
            flags.count { inScope(it.key) && it.value.proteinMissed },
            flags.count { inScope(it.key) && it.value.kcalExceeded },
            elapsed,
        )
    }

    /**
     * Silver stars: active goals by a deadline not yet passed (today or later), each day's goals sorted by name.
     * Achieved/archived goals and overdue active ones are skipped.
     */
    fun goalsDue(goals: List<Goal>, today: LocalDate): Map<LocalDate, List<Goal>> =
        goals.filter { it.state == GoalState.ACTIVE && !it.deadline.isBefore(today) }.byDay { it.deadline }

    /** Gold stars: achieved goals by the day they were reached ([Goal.achievedAt]); archived goals are skipped. */
    fun goalsReached(goals: List<Goal>): Map<LocalDate, List<Goal>> =
        goals.filter { it.state == GoalState.ACHIEVED && it.achievedAt != null }.byDay { it.achievedAt!! }

    /** Goals of [byDay] falling in [month], by date then name (month header line). */
    fun goalsIn(month: YearMonth, byDay: Map<LocalDate, List<Goal>>): List<Goal> =
        byDay.filterKeys { YearMonth.from(it) == month }.toSortedMap().values.flatten()

    private fun List<Goal>.byDay(key: (Goal) -> LocalDate) = groupBy(key).mapValues { (_, g) -> g.sortedBy { it.name } }
}
