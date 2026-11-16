package com.lifehub.app.data.model

enum class DayType { PULL, PUSH, LEGS, CORE }
enum class GoalState { ACTIVE, ACHIEVED, ARCHIVED }
enum class ExerciseCategory {
    PULL, PUSH, CORE, LEGS;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        /** Names of the former built-ins whose removed ISOMETRIC/ACCESSORY category maps to PULL or PUSH. */
        val LEGACY_PULL_NAMES = listOf("Tuck front lever", "Advanced tuck front lever", "Front lever", "Tuck back lever", "Back lever",
            "Dead hang", "Biceps curl", "Face pull")
        val LEGACY_PUSH_NAMES = listOf("Planche lean", "Tuck planche", "Support hold", "Triceps pushdown", "Lateral raise")

        /** Stored/imported category, mapping the removed ISOMETRIC and ACCESSORY (DB v6, export v6); same rules as MIGRATION_5_6. */
        fun fromLegacy(raw: String, exerciseName: String): ExerciseCategory {
            entries.firstOrNull { it.name == raw }?.let { return it }
            val n = exerciseName.trim()
            return when {
                LEGACY_PULL_NAMES.any { it.equals(n, ignoreCase = true) } -> PULL
                LEGACY_PUSH_NAMES.any { it.equals(n, ignoreCase = true) } -> PUSH
                raw == "ACCESSORY" -> PUSH
                else -> CORE
            }
        }
    }
}
enum class TargetType { REPS, MAX, SECONDS }          // Exercise.defaultTargetType only REPS|SECONDS
enum class SessionStatus { IN_PROGRESS, COMPLETED }
enum class SetStatus { PENDING, DONE, SKIPPED }
enum class HomePeriod(val days: Int) { ONE_WEEK(7), TWO_WEEKS(14) }
/** Order matters: the ordinal is the notification id / request code, so new types go at the end. */
enum class NotifType { TRAIN, PROTEIN, WEIGHT, ACHIEVEMENT_MEMORY, STREAK }

enum class AchievementKind {
    GOAL_ACHIEVED, PR_REPS, PR_HOLD, PR_WEIGHT, PERFECT_WEEK_TRAINING, PERFECT_MONTH_TRAINING, PROTEIN_WEEK, SESSION_MILESTONE;

    val isBig: Boolean get() = this == GOAL_ACHIEVED
    val isPr: Boolean get() = this == PR_REPS || this == PR_HOLD || this == PR_WEIGHT
    val isPeriod: Boolean get() = this == PERFECT_WEEK_TRAINING || this == PERFECT_MONTH_TRAINING || this == PROTEIN_WEEK
    val isConsistency: Boolean get() = isPeriod || this == SESSION_MILESTONE
}

/** CUSTOM / OFF (cached Open Food Facts product); v9: BASE = copied from the base food table, ESTIMATE = accepted copilot estimate. */
enum class FoodSource { CUSTOM, OFF, BASE, ESTIMATE }

/** Raw vs cooked, where it matters for the values per 100 g (copilot: raw is the default). */
enum class FoodState { RAW, COOKED }

/** Where the values of a copilot-logged entry came from (design doc §6 steps). */
enum class EntrySource(val wire: String) {
    HISTORY("history"), BASE_TABLE("base_table"), OPEN_FOOD_FACTS("open_food_facts"), LLM_ESTIMATE("llm_estimate");
}
