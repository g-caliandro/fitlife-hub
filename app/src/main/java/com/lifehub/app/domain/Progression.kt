package com.lifehub.app.domain

import com.lifehub.app.data.model.PlanExercise
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType

/** A plan exercise whose target was raised after a session. */
data class RaisedTarget(val updated: PlanExercise, val label: String)

/**
 * "Remember what I managed": after a workout, a plan exercise's target goes up to what was actually done, so next time
 * the sets are prefilled with the new target (no manual plan edit). Targets are never lowered automatically.
 *
 * Per exercise, on DONE sets only:
 * - heavier than planned -> weight = heaviest weight used; reps/seconds target = best set at that weight
 *   (it can be lower than before: it is the new target at the new weight);
 * - same weight -> reps/seconds target = max(target, best set at or above the planned weight).
 * MAX exercises have no reps target: only the weight can go up.
 */
object Progression {
    private fun value(s: SessionSet) = if (s.targetType == TargetType.SECONDS) s.actualSeconds else s.actualReps

    fun raise(plan: List<PlanExercise>, sets: List<SessionSet>, exerciseName: (String) -> String? = { null }): List<RaisedTarget> {
        val blocks = sets.groupBy { it.exerciseOrder }
        return plan.sortedBy { it.orderIndex }.mapIndexedNotNull { order, pe ->
            // The session was built from the plan in order; fall back to the only block of that exercise (edits mid-workout).
            val block = blocks[order]?.takeIf { b -> b.first().exerciseId == pe.exerciseId }
                ?: blocks.values.filter { it.first().exerciseId == pe.exerciseId }.singleOrNull()
                ?: return@mapIndexedNotNull null
            val done = block.filter { it.status == SetStatus.DONE && it.targetType == pe.targetType && value(it) != null }
            if (done.isEmpty()) return@mapIndexedNotNull null
            val planW = pe.weightKg ?: 0.0
            val topW = done.maxOf { it.weightKg ?: 0.0 }
            val heavier = topW > planW
            val newW = if (heavier) topW else planW
            val bestAtW = done.filter { (it.weightKg ?: 0.0) >= newW }.maxOfOrNull { value(it)!! }
            val newV = when {
                pe.targetType == TargetType.MAX -> null
                heavier -> bestAtW?.takeIf { it > 0 } ?: pe.targetValue
                else -> maxOf(pe.targetValue ?: 0, bestAtW ?: 0).takeIf { it > 0 } ?: pe.targetValue
            }
            if (!heavier && newV == pe.targetValue) return@mapIndexedNotNull null
            val updated = pe.copy(weightKg = if (newW > 0) newW else pe.weightKg, targetValue = newV)
            if (updated == pe) return@mapIndexedNotNull null
            val name = exerciseName(pe.exerciseId) ?: block.first().exerciseName
            val what = buildList {
                if (newV != null) add(if (pe.targetType == TargetType.SECONDS) "$newV s" else "$newV reps")
                if (heavier) add("${AchievementEngine.num(newW)} kg")
            }.joinToString(" @ ")
            RaisedTarget(updated, "$name $what")
        }
    }
}
