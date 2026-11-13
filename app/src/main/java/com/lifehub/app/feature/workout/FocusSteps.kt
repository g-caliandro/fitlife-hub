package com.lifehub.app.feature.workout

import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType

enum class StepKind { SET, HOLD_RUNNING, REST, REST_DONE, FINISHED }

/** The single full-screen step of the focus mode. [setIndex] is the current (or up-next) set in the ordered list. */
data class FocusStep(val kind: StepKind, val setIndex: Int?)

/** Segment of the progress bar. */
data class Seg(val setId: String, val status: SetStatus, val current: Boolean)

/**
 * Pure derivation of the focus step from persisted session state, so process death / resume lands on the right
 * step: no pending set -> FINISHED; a persisted rest end (restEndsAt) -> REST until it passes, then REST_DONE until
 * acknowledged (acknowledging / skipping clears restEndsAt); a running hold -> HOLD_RUNNING; otherwise SET.
 */
object FocusSteps {
    /** The set the focus mode is about: the focused set if still pending, else the next pending one. */
    fun currentIndex(sets: List<SessionSet>, focusedIndex: Int): Int? {
        if (sets.getOrNull(focusedIndex)?.status == SetStatus.PENDING) return focusedIndex
        return WorkoutOps.nextPendingIndex(sets, focusedIndex)
    }

    fun derive(sets: List<SessionSet>, focusedIndex: Int, restEndsAtMillis: Long?, holdStartMillis: Long?, nowMillis: Long): FocusStep {
        val idx = currentIndex(sets, focusedIndex) ?: return FocusStep(StepKind.FINISHED, null)
        val set = sets[idx]
        return when {
            restEndsAtMillis != null && nowMillis < restEndsAtMillis -> FocusStep(StepKind.REST, idx)
            restEndsAtMillis != null -> FocusStep(StepKind.REST_DONE, idx)
            holdStartMillis != null && set.targetType == TargetType.SECONDS -> FocusStep(StepKind.HOLD_RUNNING, idx)
            else -> FocusStep(StepKind.SET, idx)
        }
    }

    /** Progress segments grouped by exercise (in exercise order). */
    fun segments(sets: List<SessionSet>, currentIndex: Int?): List<List<Seg>> =
        sets.withIndex().groupBy { it.value.exerciseOrder }.toSortedMap().values
            .map { g -> g.map { Seg(it.value.id, it.value.status, it.index == currentIndex) } }

    /** "Exercise 2/6 · Set 3/4" numbers: (exerciseNo, exerciseCount, setNo, setCount) for [index]. */
    data class Position(val exercise: Int, val exercises: Int, val set: Int, val sets: Int)

    fun position(sets: List<SessionSet>, index: Int?): Position? {
        val s = sets.getOrNull(index ?: return null) ?: return null
        val orders = sets.map { it.exerciseOrder }.distinct().sorted()
        val block = sets.filter { it.exerciseOrder == s.exerciseOrder }
        return Position(orders.indexOf(s.exerciseOrder) + 1, orders.size, s.setIndex + 1, block.size)
    }
}
