package com.lifehub.app.domain

import com.lifehub.app.data.model.Goal
import com.lifehub.app.data.model.GoalWithLinks
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.WorkoutSession
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TimeProgress(val totalDays: Int, val elapsedDays: Int, val fraction: Float, val daysLeft: Int)

/** Spec 4.6. */
fun timeProgress(start: LocalDate, deadline: LocalDate, today: LocalDate): TimeProgress {
    val total = ChronoUnit.DAYS.between(start, deadline).toInt().coerceAtLeast(1)
    val elapsed = ChronoUnit.DAYS.between(start, today).toInt().coerceIn(0, total)
    return TimeProgress(total, elapsed, elapsed.toFloat() / total, ChronoUnit.DAYS.between(today, deadline).toInt())
}

/** Spec 4.7: COMPLETED sessions of plan with goal.startDate <= date <= (achievedAt ?: today). */
fun sessionsDone(goal: Goal, planId: String, completed: List<WorkoutSession>, today: LocalDate): Int {
    val end = goal.achievedAt ?: today
    return completed.count {
        it.planId == planId && !it.date.isBefore(goal.startDate) && !it.date.isAfter(end)
    }
}

data class PlanProgress(val planId: String, val planName: String, val done: Int, val needed: Int?) {
    val fraction: Float? get() = needed?.let { if (it <= 0) 1f else (done.toFloat() / it).coerceAtMost(1f) }
}

fun planProgress(goal: GoalWithLinks, plans: List<Plan>, completed: List<WorkoutSession>, today: LocalDate): List<PlanProgress> {
    val byId = plans.associateBy { it.id }
    return goal.links.mapNotNull { l ->
        val plan = byId[l.planId] ?: return@mapNotNull null
        PlanProgress(plan.id, plan.name, sessionsDone(goal.goal, plan.id, completed, today), l.sessionsNeeded)
    }.sortedBy { it.planName.lowercase() }
}
