package com.lifehub.app.data.repo

import com.lifehub.app.data.model.*
import com.lifehub.app.data.prefs.Settings

/** The unit of export/import/mock/clear. */
data class DataSnapshot(
    val settings: Settings,
    val goals: List<Goal>, val links: List<GoalPlanLink>,
    val plans: List<Plan>, val planExercises: List<PlanExercise>,
    val exercises: List<Exercise>,
    val sessions: List<WorkoutSession>, val sets: List<SessionSet>,
    val macros: List<MacroDay>, val bodyEntries: List<BodyEntry>,
    val achievements: List<Achievement> = emptyList(),
    val foods: List<Food> = emptyList(), val foodEntries: List<FoodEntry> = emptyList(),
)
