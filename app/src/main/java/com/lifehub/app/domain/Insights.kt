package com.lifehub.app.domain

import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.MacroDay
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SessionStatus
import com.lifehub.app.data.model.SessionWithSets
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.data.model.WorkoutSession
import java.time.LocalDate
import kotlin.math.roundToInt

/** Spec 4.2-4.5. Raw doubles; rounding only for display (except where spec says integer). */
data class Metric<T : Number>(val current: T?, val previous: T?) {
    val delta: Double? get() = if (current == null || previous == null) null else current.toDouble() - previous.toDouble()
}

/** 4.2: field of the entry with the greatest date in [r] whose field is non-null. */
fun lastBodyValue(entries: List<BodyEntry>, r: DateRange, field: (BodyEntry) -> Double?): Double? =
    entries.filter { it.date in r && field(it) != null }.maxByOrNull { it.date }?.let(field)

/** 4.3: COMPLETED sessions with date in [r]. */
fun sessionsCount(sessions: List<WorkoutSession>, r: DateRange): Int =
    sessions.count { it.status == SessionStatus.COMPLETED && it.date in r }

private fun completedIn(sessions: List<SessionWithSets>, r: DateRange) =
    sessions.filter { it.session.status == SessionStatus.COMPLETED && it.session.date in r }

private fun repSets(sessions: List<SessionWithSets>, r: DateRange): List<SessionSet> =
    completedIn(sessions, r).flatMap { it.sets }.filter {
        it.status == SetStatus.DONE && (it.targetType == TargetType.REPS || it.targetType == TargetType.MAX)
    }

fun avgRepsPerSet(sessions: List<SessionWithSets>, r: DateRange): Double? {
    val sets = repSets(sessions, r)
    if (sets.isEmpty()) return null
    return sets.sumOf { it.actualReps ?: 0 }.toDouble() / sets.size
}

fun volumeReps(sessions: List<SessionWithSets>, r: DateRange): Int =
    repSets(sessions, r).sumOf { it.actualReps ?: 0 }

fun holdSeconds(sessions: List<SessionWithSets>, r: DateRange): Int =
    completedIn(sessions, r).flatMap { it.sets }
        .filter { it.status == SetStatus.DONE && it.targetType == TargetType.SECONDS }
        .sumOf { it.actualSeconds ?: 0 }

data class Adherence(val done: Int, val planned: Int) {
    val percent: Int? get() = if (planned == 0) null else (done * 100.0 / planned).roundToInt()
}

/** 4.4. Sessions are expected to be COMPLETED only, but non-COMPLETED ones are ignored defensively. */
fun adherence(plans: List<Plan>, sessions: List<WorkoutSession>, r: DateRange, today: LocalDate): Adherence {
    val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
    val doneKeys = completed.mapNotNull { s -> s.planId?.let { it to s.date } }.toSet()
    var planned = 0
    var done = 0
    for (p in plans) {
        if (p.isArchived || p.weekdays.isEmpty()) continue
        for (d in r.days) {
            if (d.isAfter(today) || d.dayOfWeek.value !in p.weekdays) continue
            val isDone = (p.id to d) in doneKeys
            // Today is still open: only count it once the session is done.
            if (d == today && !isDone) continue
            planned++
            if (isDone) done++
        }
    }
    return Adherence(done, planned)
}

private fun logged(macros: List<MacroDay>, r: DateRange) = macros.filter { it.date in r }

fun avgProtein(macros: List<MacroDay>, r: DateRange): Int? =
    logged(macros, r).takeIf { it.isNotEmpty() }?.let { l -> (l.sumOf { it.proteinG }.toDouble() / l.size).roundToInt() }

fun avgKcal(macros: List<MacroDay>, r: DateRange): Int? =
    logged(macros, r).takeIf { it.isNotEmpty() }?.let { l -> (l.sumOf { it.kcal }.toDouble() / l.size).roundToInt() }

/** (daysOnTarget, loggedDays). */
fun daysOnTarget(macros: List<MacroDay>, r: DateRange, targetG: Int): Pair<Int, Int> {
    val l = logged(macros, r)
    return l.count { it.proteinG >= targetG } to l.size
}

data class InsightInput(
    val today: LocalDate, val current: DateRange, val previous: DateRange,
    val plans: List<Plan>, val sessions: List<SessionWithSets>,   // COMPLETED, covering previous.start..current.end
    val macros: List<MacroDay>, val body: List<BodyEntry>, val proteinTargetG: Int,
)

data class Insights(
    val weight: Metric<Double>, val muscle: Metric<Double>, val fat: Metric<Double>,
    val sessions: Metric<Int>, val adherence: Pair<Adherence, Adherence>,   // current, previous
    val adherenceDeltaPts: Int?,                                            // null if either percent null
    val avgRepsPerSet: Metric<Double>, val volume: Metric<Int>, val holdSeconds: Metric<Int>,
    val avgProtein: Metric<Int>, val avgKcal: Metric<Int>, val proteinOnTarget: Pair<Int, Int>,
    /** avgProtein(current) - target, grams; null if no logged days. */
    val proteinVsTarget: Int? = null,
    /** Fat percent (fat / weight) of the latest entry in the current period with fat; null if none. */
    val fatPercent: Double? = null,
)

fun computeInsights(input: InsightInput): Insights {
    val c = input.current
    val p = input.previous
    val plain = input.sessions.map { it.session }
    val curAd = adherence(input.plans, plain, c, input.today)
    val prevAd = adherence(input.plans, plain, p, input.today)
    val cp = curAd.percent
    val pp = prevAd.percent
    val avgP = avgProtein(input.macros, c)
    val latestFat = input.body.filter { it.date in c && it.fatKg != null }.maxByOrNull { it.date }
    return Insights(
        weight = Metric(lastBodyValue(input.body, c) { it.weightKg }, lastBodyValue(input.body, p) { it.weightKg }),
        muscle = Metric(lastBodyValue(input.body, c) { it.muscleKg }, lastBodyValue(input.body, p) { it.muscleKg }),
        fat = Metric(lastBodyValue(input.body, c) { it.fatKg }, lastBodyValue(input.body, p) { it.fatKg }),
        sessions = Metric(sessionsCount(plain, c), sessionsCount(plain, p)),
        adherence = curAd to prevAd,
        adherenceDeltaPts = if (cp == null || pp == null) null else cp - pp,
        avgRepsPerSet = Metric(avgRepsPerSet(input.sessions, c), avgRepsPerSet(input.sessions, p)),
        volume = Metric(volumeReps(input.sessions, c), volumeReps(input.sessions, p)),
        holdSeconds = Metric(holdSeconds(input.sessions, c), holdSeconds(input.sessions, p)),
        avgProtein = Metric(avgP, avgProtein(input.macros, p)),
        avgKcal = Metric(avgKcal(input.macros, c), avgKcal(input.macros, p)),
        proteinOnTarget = daysOnTarget(input.macros, c, input.proteinTargetG),
        proteinVsTarget = avgP?.let { it - input.proteinTargetG },
        fatPercent = latestFat?.let { fatPct(it) },
    )
}

/**
 * Logging streak (ONT "x-day streak"): consecutive days with food or quick-add macros logged, ending today — or
 * yesterday when today has nothing yet (the streak is still alive until the day ends). 0 when broken.
 */
fun loggingStreak(loggedDays: Set<LocalDate>, today: LocalDate): Int {
    var d = if (today in loggedDays) today else today.minusDays(1)
    var n = 0
    while (d in loggedDays) { n++; d = d.minusDays(1) }
    return n
}

/** Average carbs / fat / protein (g) over the logged days of [r]; null when nothing logged. */
data class MacroAverages(val carbsG: Int, val fatG: Int, val proteinG: Int, val kcal: Int, val days: Int)

fun macroAverages(macros: List<MacroDay>, r: DateRange): MacroAverages? {
    val l = logged(macros, r).takeIf { it.isNotEmpty() } ?: return null
    fun avg(f: (MacroDay) -> Int) = (l.sumOf(f).toDouble() / l.size).roundToInt()
    return MacroAverages(avg { it.carbsG }, avg { it.fatG }, avg { it.proteinG }, avg { it.kcal }, l.size)
}
