package com.lifehub.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "goals", indices = [Index("state")])
data class Goal(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String?,
    val startDate: LocalDate,
    val deadline: LocalDate,
    val deadlineIsAuto: Boolean,
    val state: GoalState,
    val achievedAt: LocalDate?,
    val createdAt: Instant,
)

@Entity(tableName = "plans")
data class Plan(
    @PrimaryKey val id: String,
    val name: String,
    val weekdays: Set<Int>,          // ISO 1..7, stored "1,3,5" ("" = empty)
    val notes: String?,
    val isArchived: Boolean,
    val createdAt: Instant,
    val dayType: DayType? = null,
    /** Plan icon key (null = default dumbbell). */
    val iconKey: String? = null,
)

@Entity(
    tableName = "goal_plan_links",
    primaryKeys = ["goalId", "planId"],
    foreignKeys = [
        ForeignKey(entity = Goal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Plan::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("planId")],
)
data class GoalPlanLink(val goalId: String, val planId: String, val sessionsNeeded: Int?)

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,   // case-insensitive unique
    val category: ExerciseCategory,
    val defaultTargetType: TargetType,  // REPS or SECONDS
    val isCustom: Boolean,
    val isHidden: Boolean,
)

@Entity(
    tableName = "plan_exercises",
    foreignKeys = [
        ForeignKey(entity = Plan::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("planId"), Index("exerciseId")],
)
data class PlanExercise(
    @PrimaryKey val id: String,
    val planId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val sets: Int,                 // 1..20
    val targetType: TargetType,
    val targetValue: Int?,         // null iff MAX
    val weightKg: Double?,
    val restSeconds: Int,          // 0..600, default 90
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(entity = Plan::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("planId"), Index("date"), Index("status")],
)
data class WorkoutSession(
    @PrimaryKey val id: String,
    val planId: String?,
    val planName: String,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationSec: Int?,
    val status: SessionStatus,
    val rpe: Int?,
    val notes: String?,
    val isManual: Boolean,
    val currentSetIndex: Int,      // index into sets ordered by (exerciseOrder, setIndex)
    val restEndsAt: Instant?,
)

@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(entity = WorkoutSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionSet(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val setIndex: Int,
    val targetType: TargetType,
    val targetValue: Int?,
    val actualReps: Int?,
    val actualSeconds: Int?,
    val weightKg: Double?,
    val status: SetStatus,
    val completedAt: Instant?,
    val restSeconds: Int,          // snapshot of rest for this exercise (90 if ad-hoc). Exported.
)

@Entity(tableName = "macro_days")
data class MacroDay(
    @PrimaryKey val date: LocalDate,
    val proteinG: Int, val carbsG: Int, val fatG: Int, val kcal: Int,
    val kcalIsAuto: Boolean,
    val updatedAt: Instant,
)

@Entity(tableName = "body_entries")
data class BodyEntry(
    @PrimaryKey val date: LocalDate,
    val weightKg: Double,
    val muscleKg: Double?,
    val fatKg: Double?,
    val updatedAt: Instant,
)

@Entity(tableName = "achievements", indices = [Index(value = ["dedupeKey"], unique = true), Index("achievedOn")])
data class Achievement(
    @PrimaryKey val id: String,
    val kind: AchievementKind,
    val title: String,
    val detail: String?,
    val value: Double?,
    val unit: String?,
    val exerciseId: String?,
    val goalId: String?,
    val sessionId: String?,
    val achievedOn: LocalDate,
    val createdAt: Instant,
    val dedupeKey: String,
)

/** Custom foods + cached Open Food Facts products. Nutrition values are per 100 g. */
@Entity(tableName = "foods", indices = [Index("barcode"), Index("lastUsedAt")])
data class Food(
    @PrimaryKey val id: String,
    val source: FoodSource,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val servingSizeG: Double?,
    val favorite: Boolean,
    val lastUsedAt: Instant?,
    /** Open Food Facts product image (null for custom foods). */
    val imageUrl: String? = null,
)

/** One logged food of a day. Name/brand and macros are snapshots (survive food edits/deletion). */
@Entity(tableName = "food_entries", indices = [Index("date")])
data class FoodEntry(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val foodId: String?,
    val name: String,
    val brand: String?,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val createdAt: Instant,
    /** Snapshot of the food's image URL. */
    val imageUrl: String? = null,
)
