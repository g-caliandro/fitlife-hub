package com.lifehub.app.domain

import com.lifehub.app.data.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.IsoFields

/** A detected achievement before it gets an id / createdAt. [dedupeKey] makes detection idempotent. */
data class AchievementCandidate(
    val kind: AchievementKind, val title: String, val detail: String?, val value: Double?, val unit: String?,
    val exerciseId: String? = null, val goalId: String? = null, val sessionId: String? = null,
    val achievedOn: LocalDate, val dedupeKey: String,
) {
    fun toEntity(id: String, createdAt: Instant) = Achievement(
        id, kind, title, detail, value, unit, exerciseId, goalId, sessionId, achievedOn, createdAt, dedupeKey,
    )
}

/** Best values of one exercise so far. [reps] counts only unweighted sets, [weight] is the best added load (0 = none). */
data class Bests(val reps: Int? = null, val seconds: Int? = null, val weight: Double = 0.0, val hasPrior: Boolean = false) {
    fun plus(s: SessionSet): Bests {
        if (s.status != SetStatus.DONE) return this
        val w = s.weightKg ?: 0.0
        val r = if (s.targetType != TargetType.SECONDS) s.actualReps else null
        val sec = if (s.targetType == TargetType.SECONDS) s.actualSeconds else null
        return Bests(
            reps = if (r != null && r >= 1 && w <= 0.0) maxOf(reps ?: 0, r) else reps,
            seconds = if (sec != null && sec >= 1) maxOf(seconds ?: 0, sec) else seconds,
            weight = if (r != null && r >= 1 && w > 0.0) maxOf(weight, w) else weight,
            hasPrior = true,
        )
    }

    companion object {
        fun of(sets: Iterable<SessionSet>): Bests = sets.fold(Bests()) { b, s -> b.plus(s) }
    }
}

/** A personal record hit by a single set. */
data class PrHit(val kind: AchievementKind, val exerciseName: String, val value: Double) {
    val unit: String get() = when (kind) { AchievementKind.PR_HOLD -> "s"; AchievementKind.PR_WEIGHT -> "kg"; else -> "reps" }
    val bannerText: String get() = when (kind) {
        AchievementKind.PR_WEIGHT -> "New PR · $exerciseName +${AchievementEngine.num(value)} kg"
        AchievementKind.PR_HOLD -> "New PR · $exerciseName ${AchievementEngine.num(value)} s"
        else -> "New PR · ${AchievementEngine.num(value)} reps $exerciseName"
    }
}

data class AchievementHistory(
    val today: LocalDate,
    val sessions: List<WorkoutSession>,
    val sets: List<SessionSet>,
    val plans: List<Plan>,
    val macros: List<MacroDay>,
    val goals: List<Goal>,
    val proteinTargetG: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
)

object AchievementEngine {
    private val MILESTONES = setOf(10, 25, 50, 100)

    fun num(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    private fun isMilestone(n: Int) = n in MILESTONES || (n >= 200 && n % 100 == 0)

    // ---- PRs -------------------------------------------------------------------------------------

    /** PR kinds hit by [set] given everything done before it. First-ever set of an exercise is never a PR. */
    fun liveHits(set: SessionSet, prior: Bests): List<PrHit> {
        if (set.status != SetStatus.DONE || !prior.hasPrior) return emptyList()
        val after = prior.plus(set)
        return buildList {
            if (prior.reps != null && after.reps != null && after.reps > prior.reps) add(PrHit(AchievementKind.PR_REPS, set.exerciseName, after.reps.toDouble()))
            if (prior.seconds != null && after.seconds != null && after.seconds > prior.seconds) add(PrHit(AchievementKind.PR_HOLD, set.exerciseName, after.seconds.toDouble()))
            if (after.weight > prior.weight) add(PrHit(AchievementKind.PR_WEIGHT, set.exerciseName, after.weight))
        }
    }

    /**
     * One PR per kind per exercise for [session]: the best value of the session when it beats [priors]
     * (bests of every earlier completed session, by exercise id).
     */
    fun sessionPrs(session: WorkoutSession, sets: List<SessionSet>, priors: Map<String, Bests>): List<AchievementCandidate> =
        sets.filter { it.sessionId == session.id }
            .sortedWith(compareBy({ it.exerciseOrder }, { it.setIndex }))
            .groupBy { it.exerciseId }
            .flatMap { (exId, group) ->
                val prior = priors[exId] ?: Bests()
                val agg = Bests.of(group)
                val name = group.first().exerciseName
                val out = mutableListOf<AchievementCandidate>()
                if (prior.hasPrior) {
                    if (prior.reps != null && agg.reps != null && agg.reps > prior.reps) {
                        val v = agg.reps
                        out += AchievementCandidate(AchievementKind.PR_REPS, "$name: $v reps in one set", "Previous best ${prior.reps}",
                            v.toDouble(), "reps", exerciseId = exId, sessionId = session.id, achievedOn = session.date,
                            dedupeKey = "PR_REPS:ex=$exId:value=$v")
                    }
                    if (prior.seconds != null && agg.seconds != null && agg.seconds > prior.seconds) {
                        val v = agg.seconds
                        out += AchievementCandidate(AchievementKind.PR_HOLD, (if (name.endsWith("hold", true)) "$name: $v s" else "$name hold: $v s"), "Previous best ${prior.seconds} s",
                            v.toDouble(), "s", exerciseId = exId, sessionId = session.id, achievedOn = session.date,
                            dedupeKey = "PR_HOLD:ex=$exId:value=$v")
                    }
                    if (agg.weight > prior.weight) {
                        out += AchievementCandidate(AchievementKind.PR_WEIGHT, "$name: +${num(agg.weight)} kg",
                            if (prior.weight > 0) "Previous best +${num(prior.weight)} kg" else null,
                            agg.weight, "kg", exerciseId = exId, sessionId = session.id, achievedOn = session.date,
                            dedupeKey = "PR_WEIGHT:ex=$exId:value=${num(agg.weight)}")
                    }
                }
                out
            }

    private fun completedSorted(h: AchievementHistory) =
        h.sessions.filter { it.status == SessionStatus.COMPLETED }.sortedWith(compareBy({ it.date }, { it.startedAt }, { it.id }))

    fun prs(h: AchievementHistory): List<AchievementCandidate> {
        val bySession = h.sets.groupBy { it.sessionId }
        val priors = HashMap<String, Bests>()
        val out = mutableListOf<AchievementCandidate>()
        for (s in completedSorted(h)) {
            val sets = bySession[s.id].orEmpty()
            out += sessionPrs(s, sets, priors)
            sets.sortedWith(compareBy({ it.exerciseOrder }, { it.setIndex })).forEach { priors[it.exerciseId] = (priors[it.exerciseId] ?: Bests()).plus(it) }
        }
        return out
    }

    // ---- milestones ------------------------------------------------------------------------------

    fun milestones(h: AchievementHistory): List<AchievementCandidate> =
        completedSorted(h).mapIndexedNotNull { i, s ->
            val n = i + 1
            if (!isMilestone(n)) null
            else AchievementCandidate(AchievementKind.SESSION_MILESTONE, "$n sessions completed", null, n.toDouble(), "sessions",
                sessionId = s.id, achievedOn = s.date, dedupeKey = "SESSION_MILESTONE:$n")
        }

    // ---- periods (completed ones only) -----------------------------------------------------------

    private fun weekKey(monday: LocalDate) = String.format("%d-W%02d", monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    private fun createdDate(p: Plan, zone: ZoneId) = p.createdAt.atZone(zone).toLocalDate()

    /** Planned (plan, date) slots in [from]..[to] and how many were fulfilled by a completed session of that plan. */
    private fun slots(h: AchievementHistory, from: LocalDate, to: LocalDate): Pair<Int, Int> {
        val done = h.sessions.filter { it.status == SessionStatus.COMPLETED && it.planId != null }.map { it.planId to it.date }.toSet()
        var planned = 0; var hit = 0
        var d = from
        while (!d.isAfter(to)) {
            for (p in h.plans) {
                if (p.isArchived || d.dayOfWeek.value !in p.weekdays || d.isBefore(createdDate(p, h.zone))) continue
                planned++
                if ((p.id to d) in done) hit++
            }
            d = d.plusDays(1)
        }
        return planned to hit
    }

    fun perfectWeeks(h: AchievementHistory): List<AchievementCandidate> {
        val first = completedSorted(h).firstOrNull()?.date ?: return emptyList()
        val out = mutableListOf<AchievementCandidate>()
        var monday = Periods.weekStart(first)
        while (monday.plusDays(6).isBefore(h.today)) {
            val sunday = monday.plusDays(6)
            val (planned, hit) = slots(h, monday, sunday)
            if (planned >= 2 && planned == hit) {
                out += AchievementCandidate(AchievementKind.PERFECT_WEEK_TRAINING, "Perfect training week", "$hit/$planned planned sessions done",
                    planned.toDouble(), "sessions", achievedOn = sunday, dedupeKey = "PERFECT_WEEK:${weekKey(monday)}")
            }
            monday = monday.plusDays(7)
        }
        return out
    }

    fun perfectMonths(h: AchievementHistory): List<AchievementCandidate> {
        val first = completedSorted(h).firstOrNull()?.date ?: return emptyList()
        val out = mutableListOf<AchievementCandidate>()
        var m = YearMonth.from(first)
        while (m.atEndOfMonth().isBefore(h.today)) {
            val (planned, hit) = slots(h, m.atDay(1), m.atEndOfMonth())
            if (planned >= 4 && planned == hit) {
                out += AchievementCandidate(AchievementKind.PERFECT_MONTH_TRAINING, "Perfect training month", "$hit/$planned planned sessions done",
                    planned.toDouble(), "sessions", achievedOn = m.atEndOfMonth(), dedupeKey = "PERFECT_MONTH:$m")
            }
            m = m.plusMonths(1)
        }
        return out
    }

    fun proteinWeeks(h: AchievementHistory): List<AchievementCandidate> {
        val target = h.proteinTargetG
        if (target <= 0) return emptyList()
        val byDate = h.macros.associateBy { it.date }
        val first = h.macros.minOfOrNull { it.date } ?: return emptyList()
        val out = mutableListOf<AchievementCandidate>()
        var monday = Periods.weekStart(first)
        while (monday.plusDays(6).isBefore(h.today)) {
            val days = (0..6).map { byDate[monday.plusDays(it.toLong())] }
            if (days.all { it != null && it.proteinG >= target }) {
                out += AchievementCandidate(AchievementKind.PROTEIN_WEEK, "Protein target hit all week", "7/7 days at or above $target g",
                    target.toDouble(), "g", achievedOn = monday.plusDays(6), dedupeKey = "PROTEIN_WEEK:${weekKey(monday)}")
            }
            monday = monday.plusDays(7)
        }
        return out
    }

    fun periods(h: AchievementHistory): List<AchievementCandidate> = perfectWeeks(h) + perfectMonths(h) + proteinWeeks(h)

    // ---- goals -----------------------------------------------------------------------------------

    fun goalCandidate(g: Goal, today: LocalDate): AchievementCandidate = AchievementCandidate(
        AchievementKind.GOAL_ACHIEVED, g.name, "Goal achieved", null, null, goalId = g.id,
        achievedOn = g.achievedAt ?: today, dedupeKey = "GOAL:${g.id}",
    )

    fun goals(h: AchievementHistory): List<AchievementCandidate> =
        h.goals.filter { it.state == GoalState.ACHIEVED && it.achievedAt != null }.map { goalCandidate(it, h.today) }

    // ---- everything ------------------------------------------------------------------------------

    /**
     * Full recomputation from history. Pure + idempotent; results are de-duplicated by key.
     * Consistency (perfect weeks/months, protein weeks, session milestones) is no longer stored as achievements:
     * it is shown as the training streak instead (see [Streak]).
     */
    fun backfill(h: AchievementHistory): List<AchievementCandidate> =
        (prs(h) + goals(h)).distinctBy { it.dedupeKey }
}
