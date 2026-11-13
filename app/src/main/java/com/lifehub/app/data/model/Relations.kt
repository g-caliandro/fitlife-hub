package com.lifehub.app.data.model

data class PlanExerciseItem(val planExercise: PlanExercise, val exercise: Exercise)
data class PlanWithExercises(val plan: Plan, val exercises: List<PlanExerciseItem>)   // ordered by orderIndex
data class SessionWithSets(val session: WorkoutSession, val sets: List<SessionSet>)  // ordered by exerciseOrder, setIndex
data class GoalWithLinks(val goal: Goal, val links: List<GoalPlanLink>)
