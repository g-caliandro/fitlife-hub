package com.lifehub.app.domain

import com.lifehub.app.data.model.PlanWithExercises
import java.time.LocalDate

/**
 * Plans scheduled on [date]: non-archived plans whose ISO weekdays contain the date's weekday, in the given order.
 * There are no dated planned workouts in the data model; a plan is a weekday template (shared by Home and the copilot).
 */
fun plannedOn(date: LocalDate, plans: List<PlanWithExercises>): List<PlanWithExercises> =
    plans.filter { !it.plan.isArchived && date.dayOfWeek.value in it.plan.weekdays }
