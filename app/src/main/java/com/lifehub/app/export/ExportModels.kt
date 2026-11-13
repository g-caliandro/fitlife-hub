package com.lifehub.app.export

import com.lifehub.app.data.model.*
import kotlinx.serialization.Serializable

/** Export schema (spec 6.1). v6: exercise `category` is PULL|PUSH|CORE|LEGS (older ISOMETRIC/ACCESSORY are remapped on import), no built-in exercises. v5 adds imageUrl/iconKey; v4 adds foods, foodEntries and kcal/carbs/fat targets; v3 adds plans.dayType; v2 adds `achievements` and settings.notifyAchievements; v1 files still import. Property order = JSON key order. */
@Serializable
data class ExportV1(
    val schemaVersion: Int = 4,
    val exportedAt: String,
    val app: AppInfoDto,
    val readme: String,
    val units: UnitsDto = UnitsDto(),
    val settings: SettingsDto = SettingsDto(),
    val goals: List<GoalDto> = emptyList(),
    val plans: List<PlanDto> = emptyList(),
    val exercises: List<ExerciseDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val macros: List<MacroDto> = emptyList(),
    val bodyEntries: List<BodyDto> = emptyList(),
    val achievements: List<AchievementDto> = emptyList(),
    val foods: List<FoodDto> = emptyList(),
    val foodEntries: List<FoodEntryDto> = emptyList(),
)

@Serializable data class AppInfoDto(val name: String = "Life Hub", val version: String = "")

@Serializable
data class UnitsDto(
    val weight: String = "kg", val macros: String = "g", val energy: String = "kcal",
    val duration: String = "seconds", val dates: String = "ISO-8601 local date",
    val timestamps: String = "epoch millis UTC",
)

@Serializable
data class SettingsDto(
    val proteinTargetG: Int = 150,
    val notifyTrain: Boolean = true,
    val notifyProtein: Boolean = true,
    val notifyWeight: Boolean = true,
    val notifyAchievements: Boolean = true,
    val dailyCheckTime: String = "18:00",
    val homePeriod: HomePeriod = HomePeriod.ONE_WEEK,
    val timezone: String? = null,
    val kcalTarget: Int? = null,
    val carbsTargetG: Int? = null,
    val fatTargetG: Int? = null,
    val notifyStreak: Boolean = true,
)

@Serializable data class PlanLinkDto(val planId: String, val sessionsNeeded: Int? = null)

@Serializable
data class GoalDto(
    val id: String, val name: String, val notes: String? = null,
    val startDate: String, val deadline: String, val deadlineIsAuto: Boolean = false,
    val state: GoalState = GoalState.ACTIVE, val achievedAt: String? = null, val createdAt: Long,
    val planLinks: List<PlanLinkDto> = emptyList(),
)

@Serializable
data class PlanExerciseDto(
    val id: String, val planId: String, val exerciseId: String, val orderIndex: Int, val sets: Int,
    val targetType: TargetType, val targetValue: Int? = null, val weightKg: Double? = null, val restSeconds: Int = 90,
)

@Serializable
data class PlanDto(
    val id: String, val name: String, val weekdays: List<Int> = emptyList(), val notes: String? = null,
    val isArchived: Boolean = false, val createdAt: Long,
    val dayType: DayType? = null,
    val iconKey: String? = null,
    val exercises: List<PlanExerciseDto> = emptyList(),
)

@Serializable
data class ExerciseDto(
    val id: String, val name: String, val category: String, val defaultTargetType: TargetType,
    val isCustom: Boolean = true, val isHidden: Boolean = false,
)

@Serializable
data class SetDto(
    val id: String, val sessionId: String, val exerciseId: String, val exerciseName: String,
    val exerciseOrder: Int, val setIndex: Int, val targetType: TargetType, val targetValue: Int? = null,
    val actualReps: Int? = null, val actualSeconds: Int? = null, val weightKg: Double? = null,
    val status: SetStatus = SetStatus.PENDING, val completedAt: Long? = null, val restSeconds: Int = 90,
)

@Serializable
data class SessionDto(
    val id: String, val planId: String? = null, val planName: String, val date: String,
    val startedAt: Long, val endedAt: Long? = null, val durationSec: Int? = null,
    val status: SessionStatus = SessionStatus.COMPLETED, val rpe: Int? = null, val notes: String? = null,
    val isManual: Boolean = false, val currentSetIndex: Int = 0, val restEndsAt: Long? = null,
    val sets: List<SetDto> = emptyList(),
)

@Serializable
data class MacroDto(
    val date: String, val proteinG: Int, val carbsG: Int, val fatG: Int, val kcal: Int,
    val kcalIsAuto: Boolean = true, val updatedAt: Long,
)

@Serializable
data class BodyDto(
    val date: String, val weightKg: Double, val muscleKg: Double? = null, val fatKg: Double? = null,
    val updatedAt: Long, val fatPct: Double? = null,
)

@Serializable
data class AchievementDto(
    val id: String, val kind: AchievementKind, val title: String, val detail: String? = null,
    val value: Double? = null, val unit: String? = null,
    val exerciseId: String? = null, val goalId: String? = null, val sessionId: String? = null,
    val achievedOn: String, val createdAt: Long, val dedupeKey: String,
)

@Serializable
data class FoodDto(
    val id: String, val source: FoodSource = FoodSource.CUSTOM, val barcode: String? = null, val name: String, val brand: String? = null,
    val kcal100: Double, val protein100: Double, val carbs100: Double, val fat100: Double,
    val servingSizeG: Double? = null, val favorite: Boolean = false, val lastUsedAt: Long? = null,
    val imageUrl: String? = null,
)

@Serializable
data class FoodEntryDto(
    val id: String, val date: String, val foodId: String? = null, val name: String, val brand: String? = null,
    val grams: Double, val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double, val createdAt: Long,
    val imageUrl: String? = null,
)
