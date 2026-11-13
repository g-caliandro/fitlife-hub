package com.lifehub.app.notifications

import com.lifehub.app.data.model.*
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.navigation.Routes
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import com.lifehub.app.domain.Streak
import kotlin.random.Random
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.domain.AchievementEngine
import kotlin.math.roundToInt

data class RuleInput(
    val today: LocalDate, val settings: Settings, val lastNotified: Map<NotifType, LocalDate?>,
    val plans: List<Plan>, val completed: List<WorkoutSession>, val hasInProgressToday: Boolean,
    val macros: List<MacroDay>,
    val latestBodyDate: LocalDate?, val earliestDataDate: LocalDate?,
    /** All stored achievements. */
    val achievements: List<Achievement> = emptyList(),
    /** Period achievements detected but not yet announced (caller filters by notified keys / recency). */
    val unlocked: List<Achievement> = emptyList(),
    /** Days on which an ACHIEVEMENT_MEMORY notification was sent recently. */
    val achievementSentDates: List<LocalDate> = emptyList(),
    /** Zone used to turn plan creation instants into dates (streak rule). */
    val zone: ZoneId = ZoneOffset.UTC,
)

data class PendingNotification(val type: NotifType, val title: String, val text: String, val route: String, val key: String? = null)

object NotificationRules {
    private const val TITLE = "Life Hub"

    fun evaluate(i: RuleInput): List<PendingNotification> = buildList {
        fun capped(t: NotifType) = i.lastNotified[t] == i.today
        if (i.settings.notifyTrain && !capped(NotifType.TRAIN)) train(i)?.let(::add)
        if (i.settings.notifyProtein && !capped(NotifType.PROTEIN)) protein(i)?.let(::add)
        if (i.settings.notifyWeight && !capped(NotifType.WEIGHT)) weight(i)?.let(::add)
        if (i.settings.notifyAchievements && !capped(NotifType.ACHIEVEMENT_MEMORY)) achievementMemory(i)?.let(::add)
    }

    /**
     * Evening streak check (separate worker at [NotificationScheduler.STREAK_TIME], not part of [evaluate] so it never
     * fires at the daily check time). At most one per day.
     */
    fun evaluateStreak(i: RuleInput): PendingNotification? =
        if (i.settings.notifyStreak && i.lastNotified[NotifType.STREAK] != i.today) streak(i) else null

    /**
     * Fires when there is a streak to lose (> 0), no workout completed (or in progress) today, and either
     * - today is a planned day: thanks to the grace rule (see [Streak]) tomorrow still saves it, unless yesterday was
     *   skipped too: then only today; or
     * - yesterday was a skipped planned day: today is the last day to make it up (also on an unplanned day).
     */
    fun streak(i: RuleInput): PendingNotification? {
        if (i.hasInProgressToday) return null
        val st = Streak(i.completed, i.plans, i.today, i.zone)
        if (!st.atRiskToday && !st.yesterdayPending) return null
        val n = st.current.takeIf { it > 0 } ?: return null
        // Preselect today's plan; on a make-up day without one, the plan that was skipped yesterday.
        val day = if (st.atRiskToday) i.today else i.today.minusDays(1)
        val matching = i.plans.filter { !it.isArchived && day.dayOfWeek.value in it.weekdays }
        val route = if (matching.size == 1) Routes.workoutStart(matching[0].id) else Routes.workoutStart()
        val text = if (st.yesterdayPending) "Train today to keep your $n-day streak." else "Train today or tomorrow to keep your $n-day streak."
        return PendingNotification(NotifType.STREAK, TITLE, text, route)
    }

    fun train(i: RuleInput): PendingNotification? {
        val trainedToday = i.hasInProgressToday || i.completed.any { it.date == i.today }
        val matching = i.plans.filter { !it.isArchived && i.today.dayOfWeek.value in it.weekdays }
        if (matching.isNotEmpty() && !trainedToday) {
            val route = if (matching.size == 1) Routes.workoutStart(matching[0].id) else Routes.workoutStart()
            return PendingNotification(NotifType.TRAIN, TITLE,
                "Training day — ${matching.minBy { it.name.lowercase() }.name}. You haven't trained yet today.", route)
        }
        val recent = (0..2).map { i.today.minusDays(it.toLong()) }.toSet()
        if (i.completed.isNotEmpty() && !trainedToday && i.completed.none { it.date in recent }) {
            return PendingNotification(NotifType.TRAIN, TITLE, "3 days without training. Time to move?", Routes.workoutStart())
        }
        return null
    }

    private fun avg(macros: Map<LocalDate, MacroDay>, from: LocalDate, to: LocalDate): Pair<Int, Double?> {
        val vals = macros.filterKeys { it >= from && it <= to }.values.map { it.proteinG }
        return vals.size to if (vals.isEmpty()) null else vals.average()
    }

    fun protein(i: RuleInput): PendingNotification? {
        if (i.today.dayOfWeek != DayOfWeek.MONDAY) return null
        val byDate = i.macros.associateBy { it.date }
        val (days, w) = avg(byDate, i.today.minusDays(7), i.today.minusDays(1))
        if (days < 3 || w == null) return null
        val prev = avg(byDate, i.today.minusDays(14), i.today.minusDays(8)).second
        val target = i.settings.proteinTargetG
        val belowTarget = w < target
        val dropped = prev != null && w < 0.9 * prev
        if (!belowTarget && !dropped) return null
        val text = if (belowTarget) "Last week avg protein was ${w.roundToInt()} g/day (target $target g)."
        else "Last week avg protein was ${w.roundToInt()} g/day, down from ${prev!!.roundToInt()} g/day the week before."
        return PendingNotification(NotifType.PROTEIN, TITLE, text, Routes.trends(HomePeriod.ONE_WEEK, 1))
    }

    fun weight(i: RuleInput): PendingNotification? {
        val ref = i.latestBodyDate ?: i.earliestDataDate ?: return null
        val days = i.today.toEpochDay() - ref.toEpochDay()
        if (!(days == 7L || days == 14L || (days >= 14 && days % 7 == 0L))) return null
        val text = if (i.latestBodyDate == null) "You haven't logged your weight yet — weigh yourself."
        else {
            val w = days / 7
            "You haven't logged your weight for $w ${if (w == 1L) "week" else "weeks"} — weigh yourself."
        }
        return PendingNotification(NotifType.WEIGHT, TITLE, text, Routes.bodyEdit(i.today))
    }

    private val ANNIVERSARY_MONTHS = listOf(1, 3, 6) + (1..10).map { it * 12 }

    private fun ago(months: Int) = when {
        months < 12 -> if (months == 1) "1 month" else "$months months"
        months == 12 -> "1 year"
        else -> "${months / 12} years"
    }

    private fun fmtVal(v: Double, unit: String?) = AchievementEngine.num(v) + (unit?.let { " $it" } ?: "")

    /**
     * F-ACH-5. At most one per day (shared cap handled by [evaluate]); anniversaries / PR memories at most ~2 per week.
     * Priority: unlocked period achievement > anniversary (big first) > occasional PR memory.
     */
    fun achievementMemory(i: RuleInput): PendingNotification? {
        if (!i.settings.notifyAchievements || i.today in i.achievementSentDates) return null
        val route = Routes.ACHIEVEMENTS
        i.unlocked.maxWithOrNull(compareBy<Achievement> { it.achievedOn }.thenBy { it.dedupeKey })?.let { a ->
            return PendingNotification(NotifType.ACHIEVEMENT_MEMORY, "Achievement unlocked", a.title, route, key = a.dedupeKey)
        }
        val lastWeek = i.achievementSentDates.count { it > i.today.minusDays(7) && it <= i.today }
        if (lastWeek >= 2) return null
        val hits = i.achievements.mapNotNull { a ->
            ANNIVERSARY_MONTHS.lastOrNull { m -> a.achievedOn.plusMonths(m.toLong()) == i.today }?.let { a to it }
        }
        hits.sortedWith(compareByDescending<Pair<Achievement, Int>> { it.first.kind.isBig }.thenByDescending { it.second }
            .thenByDescending { it.first.achievedOn }.thenBy { it.first.dedupeKey }).firstOrNull()?.let { (a, m) ->
            val text = if (a.kind.isBig) "${ago(m)} ago today: ${a.title} achieved \uD83C\uDF89" else "${ago(m)} ago today: ${a.title}"
            return PendingNotification(NotifType.ACHIEVEMENT_MEMORY, TITLE, text, route)
        }
        if (lastWeek > 0 || i.achievementSentDates.any { it > i.today.minusDays(7) }) return null
        val old = i.achievements.filter { it.kind.isPr && it.value != null && !it.achievedOn.isAfter(i.today.minusDays(30)) }
            .sortedWith(compareBy({ it.achievedOn }, { it.dedupeKey }))
        if (old.isEmpty()) return null
        val rnd = Random(i.today.toEpochDay())
        if (rnd.nextInt(3) != 0) return null
        val pick = old[rnd.nextInt(old.size)]
        val best = i.achievements.filter { it.kind == pick.kind && it.exerciseId == pick.exerciseId && it.value != null }.maxByOrNull { it.value!! }
        val base = "On ${fmtDate(pick.achievedOn, i.today.year)}: ${pick.title}."
        val text = if (best != null && best.value!! > pick.value!!) "$base Your best now: ${fmtVal(best.value, best.unit)}."
        else "$base Still your personal best."
        return PendingNotification(NotifType.ACHIEVEMENT_MEMORY, TITLE, text, route)
    }

    /** Delay from [now] until the next occurrence of [time] (today if still ahead, else tomorrow). */
    fun delayUntilNext(now: ZonedDateTime, time: LocalTime): Duration {
        var next = now.with(time).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
