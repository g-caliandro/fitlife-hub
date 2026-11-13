package com.lifehub.app.domain

import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import kotlin.math.min
import kotlin.math.roundToInt

/** Plan compliance of a performed session. [percent] 0..100, exercises = fully completed / planned. */
data class Compliance(val percent: Int, val exercisesDone: Int, val exercisesTotal: Int)

object ComplianceCalc {
    /** 0..1 score of one planned set. Skipped / pending = 0. */
    fun setScore(set: SessionSet): Double {
        if (set.status != SetStatus.DONE) return 0.0
        return when (set.targetType) {
            TargetType.MAX -> 1.0
            TargetType.REPS -> {
                val t = set.targetValue ?: return 1.0
                if (t <= 0) 1.0 else min(set.actualReps ?: 0, t).toDouble() / t
            }
            TargetType.SECONDS -> {
                val t = set.targetValue ?: return 1.0
                if (t <= 0) 1.0 else min((set.actualSeconds ?: 0).toDouble() / t, 1.0)
            }
        }
    }

    /**
     * @param plannedSets optional exerciseId -> number of planned sets; when given, only the first N sets (by setIndex) of each
     * planned exercise count (extra sets and ad-hoc exercises are ignored). Null = every session set is planned.
     */
    fun compute(sets: List<SessionSet>, plannedSets: Map<String, Int>? = null): Compliance? {
        val groups = sets.groupBy { it.exerciseOrder }.values.mapNotNull { g ->
            val ordered = g.sortedBy { it.setIndex }
            val limit = plannedSets?.let { it[ordered.first().exerciseId] ?: return@mapNotNull null }
            if (limit != null) ordered.take(limit) else ordered
        }.filter { it.isNotEmpty() }
        if (groups.isEmpty()) return null
        val all = groups.flatten()
        val pct = (all.sumOf { setScore(it) } / all.size * 100).roundToInt()
        return Compliance(pct, groups.count { g -> g.all { it.status == SetStatus.DONE } }, groups.size)
    }
}
