package com.lifehub.app.feature.workout

import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import java.time.Instant
import java.util.UUID

/** Text-field state for the focused set (weight kg, and reps or seconds depending on target type). */
data class SetInput(val weight: String, val value: String)

/** Pure session operations (architecture 7). They return rows to upsert; callers persist. */
object WorkoutOps {
    const val DEFAULT_REST = 90

    fun ordered(sets: List<SessionSet>): List<SessionSet> = sets.sortedWith(compareBy({ it.exerciseOrder }, { it.setIndex }))

    /** Index (into the ordered list) of the next PENDING set after [from]; wraps to the start; null if none pending. */
    fun nextPendingIndex(sets: List<SessionSet>, from: Int): Int? {
        val o = ordered(sets)
        for (i in (from + 1) until o.size) if (o[i].status == SetStatus.PENDING) return i
        for (i in 0 until minOf(from + 1, o.size)) if (o[i].status == SetStatus.PENDING) return i
        return null
    }

    /** Validates invariant 3.7: DONE needs reps (REPS/MAX) or seconds (SECONDS). */
    fun complete(set: SessionSet, reps: Int?, seconds: Int?, weightKg: Double?, now: Instant): SessionSet {
        when (set.targetType) {
            TargetType.SECONDS -> require(seconds != null && seconds >= 0) { "seconds required" }
            else -> require(reps != null && reps >= 0) { "reps required" }
        }
        return if (set.targetType == TargetType.SECONDS) {
            set.copy(status = SetStatus.DONE, actualSeconds = seconds, actualReps = null, weightKg = weightKg, completedAt = now)
        } else {
            set.copy(status = SetStatus.DONE, actualReps = reps, actualSeconds = null, weightKg = weightKg, completedAt = now)
        }
    }

    fun skip(set: SessionSet): SessionSet =
        set.copy(status = SetStatus.SKIPPED, actualReps = null, actualSeconds = null, completedAt = null)

    fun unskip(set: SessionSet): SessionSet = set.copy(status = SetStatus.PENDING)

    /** Copies the last set of that exercise as a new PENDING set with null actuals. */
    fun addSet(sets: List<SessionSet>, exerciseOrder: Int, id: String = UUID.randomUUID().toString()): SessionSet {
        val last = ordered(sets).last { it.exerciseOrder == exerciseOrder }
        return last.copy(
            id = id, setIndex = last.setIndex + 1, actualReps = null, actualSeconds = null,
            status = SetStatus.PENDING, completedAt = null,
        )
    }

    /** Remaining sets of the removed set's exercise, renumbered 0..n-1 (the removed row is NOT included). */
    fun removeSet(sets: List<SessionSet>, setId: String): List<SessionSet> {
        val target = sets.first { it.id == setId }
        return ordered(sets).filter { it.exerciseOrder == target.exerciseOrder && it.id != setId }
            .mapIndexed { i, s -> s.copy(setIndex = i) }
    }

    fun addExercise(
        sessionId: String, sets: List<SessionSet>, ex: Exercise, count: Int, type: TargetType, value: Int?,
        weightKg: Double?, restSeconds: Int = DEFAULT_REST,
    ): List<SessionSet> {
        val order = (sets.maxOfOrNull { it.exerciseOrder } ?: -1) + 1
        return (0 until count).map { i ->
            SessionSet(
                id = UUID.randomUUID().toString(), sessionId = sessionId, exerciseId = ex.id, exerciseName = ex.name,
                exerciseOrder = order, setIndex = i, targetType = type, targetValue = if (type == TargetType.MAX) null else value,
                actualReps = null, actualSeconds = null, weightKg = weightKg, status = SetStatus.PENDING,
                completedAt = null, restSeconds = restSeconds,
            )
        }
    }

    /** Replaces the exercise on PENDING rows only. */
    fun swapExercise(sets: List<SessionSet>, exerciseOrder: Int, ex: Exercise): List<SessionSet> =
        sets.filter { it.exerciseOrder == exerciseOrder && it.status == SetStatus.PENDING }
            .map { it.copy(exerciseId = ex.id, exerciseName = ex.name) }

    /** PENDING rows of the same exercise after [fromSetId] get [weightKg]. */
    fun applyWeightToRemaining(sets: List<SessionSet>, fromSetId: String, weightKg: Double?): List<SessionSet> {
        val from = sets.first { it.id == fromSetId }
        return sets.filter {
            it.exerciseOrder == from.exerciseOrder && it.setIndex > from.setIndex && it.status == SetStatus.PENDING
        }.map { it.copy(weightKg = weightKg) }
    }

    // ---- input handling for the dock ----

    private fun weightText(w: Double?): String = when {
        w == null -> ""
        w % 1.0 == 0.0 -> w.toLong().toString()
        else -> String.format(java.util.Locale.US, "%.1f", w)
    }

    /** F-WO-3/4/5: REPS prefilled with target, MAX empty, SECONDS prefilled with target; DONE rows show actuals. */
    fun prefill(set: SessionSet): SetInput {
        val w = weightText(set.weightKg)
        val v = when {
            set.status == SetStatus.DONE ->
                (if (set.targetType == TargetType.SECONDS) set.actualSeconds else set.actualReps)?.toString().orEmpty()
            set.targetType == TargetType.MAX -> ""
            else -> set.targetValue?.toString().orEmpty()
        }
        return SetInput(w, v)
    }

    /** Ruler weight -> dock text ("" = no weight / bodyweight). */
    fun weightInput(kg: Double): String = if (kg <= 0.0) "" else weightText(kg)

    /**
     * Ruler start value when the set has none (MAX sets): what was done on the same set last time, else on the
     * previous set of this exercise in this session, else 30 s for a hold, else 1 rep.
     */
    fun fallbackValue(set: SessionSet, sameSession: List<SessionSet>, last: SessionSet?): String {
        fun v(s: SessionSet?) = s?.takeIf { it.status == SetStatus.DONE }?.let { if (it.targetType == TargetType.SECONDS) it.actualSeconds else it.actualReps }
        return (v(last) ?: sameSession.filter { it.exerciseOrder == set.exerciseOrder && it.setIndex < set.setIndex }
            .sortedByDescending { it.setIndex }.firstNotNullOfOrNull { v(it) } ?: if (set.targetType == TargetType.SECONDS) 30 else 1).toString()
    }

    /**
     * After a set is done with a weight different from its planned one, the remaining PENDING sets of that exercise
     * take the same weight (no more "apply to remaining" button).
     */
    fun carryWeight(sets: List<SessionSet>, done: SessionSet, plannedWeight: Double?): List<SessionSet> =
        if (done.weightKg == plannedWeight) emptyList() else applyWeightToRemaining(sets, done.id, done.weightKg)

    fun canComplete(input: SetInput): Boolean = input.value.toIntOrNull()?.let { it in 0..999 } == true

    /** Parses the input and completes [set]. Returns null if the value is invalid. */
    fun completeFromInput(set: SessionSet, input: SetInput, now: Instant): SessionSet? {
        if (!canComplete(input)) return null
        val v = input.value.toInt()
        val w = input.weight.toDoubleOrNull()
        return if (set.targetType == TargetType.SECONDS) complete(set, null, v, w, now) else complete(set, v, null, w, now)
    }

    /** Whether the weight in the dock differs from the set's stored weight (shows "apply to remaining"). */
    fun weightChanged(set: SessionSet, input: SetInput): Boolean = input.weight.toDoubleOrNull() != set.weightKg

    // ---- session edit / manual log ----

    /** Manual log: every planned set prefilled DONE with actual = target; MAX rows empty. */
    fun manualSets(sessionId: String, plan: PlanWithExercises, now: Instant): List<SessionSet> =
        plan.exercises.flatMapIndexed { order, item ->
            val pe = item.planExercise
            (0 until pe.sets).map { i ->
                SessionSet(
                    id = UUID.randomUUID().toString(), sessionId = sessionId, exerciseId = item.exercise.id,
                    exerciseName = item.exercise.name, exerciseOrder = order, setIndex = i, targetType = pe.targetType,
                    targetValue = pe.targetValue,
                    actualReps = if (pe.targetType == TargetType.REPS) pe.targetValue else null,
                    actualSeconds = if (pe.targetType == TargetType.SECONDS) pe.targetValue else null,
                    weightKg = pe.weightKg,
                    status = if (pe.targetType == TargetType.MAX) SetStatus.SKIPPED else SetStatus.DONE,
                    completedAt = if (pe.targetType == TargetType.MAX) null else now, restSeconds = pe.restSeconds,
                )
            }
        }

    /**
     * Applies an inline edit row: checked + valid actual => DONE; checked + blank actual => SKIPPED ("empty = skipped");
     * unchecked => SKIPPED.
     */
    fun applyEdit(set: SessionSet, weight: String, actual: String, checked: Boolean, now: Instant): SessionSet {
        val w = weight.toDoubleOrNull()
        val v = actual.toIntOrNull()
        if (!checked || v == null) {
            return set.copy(status = SetStatus.SKIPPED, actualReps = null, actualSeconds = null, weightKg = w, completedAt = null)
        }
        val done = if (set.targetType == TargetType.SECONDS) complete(set, null, v, w, now) else complete(set, v, null, w, now)
        return done.copy(completedAt = set.completedAt ?: now)
    }

    // ---- previous column (item 13) ----

    /** "6", "8 · +10kg", "12 s" or "—" for a set of the last completed session. */
    fun previousLabel(prev: SessionSet?): String {
        if (prev == null || prev.status != SetStatus.DONE) return "\u2014"
        val main = if (prev.targetType == TargetType.SECONDS) prev.actualSeconds?.let { "$it s" } else prev.actualReps?.toString()
        main ?: return "\u2014"
        val w = prev.weightKg?.takeIf { it > 0 }
        return if (w != null) "$main · +${com.lifehub.app.core.designsystem.fmtWeight(w)}kg" else main
    }

    /**
     * Previous labels by set id. [history] maps exerciseId -> sets (ordered by exerciseOrder, setIndex) of that exercise in the
     * most recent completed session; the n-th set of an exercise block is compared with the n-th historical set.
     */
    fun previousLabels(sets: List<SessionSet>, history: Map<String, List<SessionSet>>): Map<String, String> =
        sets.groupBy { it.exerciseOrder }.values.flatMap { block ->
            val sorted = block.sortedBy { it.setIndex }
            val hist = history[sorted.first().exerciseId].orEmpty()
            sorted.mapIndexed { i, s -> s.id to previousLabel(hist.getOrNull(i)) }
        }.toMap()

    // ---- preview (item 17) ----

    const val AVG_SET_SECONDS = 40

    /** Estimated duration: sum over planned sets of (40 s + rest). */
    fun estimateSeconds(plan: PlanWithExercises): Int =
        plan.exercises.sumOf { it.planExercise.sets * (AVG_SET_SECONDS + it.planExercise.restSeconds) }

    // ---- finish summary (item 16) ----

    data class Summary(val done: Int, val skipped: Int, val pending: Int, val reps: Int, val holdSeconds: Int)

    fun summarize(sets: List<SessionSet>): Summary {
        val done = sets.filter { it.status == SetStatus.DONE }
        return Summary(
            done = done.size, skipped = sets.count { it.status == SetStatus.SKIPPED }, pending = sets.count { it.status == SetStatus.PENDING },
            reps = done.sumOf { if (it.targetType == TargetType.SECONDS) 0 else it.actualReps ?: 0 },
            holdSeconds = done.sumOf { if (it.targetType == TargetType.SECONDS) it.actualSeconds ?: 0 else 0 },
        )
    }
}
