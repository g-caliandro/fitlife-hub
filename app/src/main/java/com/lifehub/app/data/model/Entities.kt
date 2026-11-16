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

/**
 * Macro totals of one day (food entries + quick adds), shaped for protein/kcal consumers (insights, notifications,
 * achievements, charts). Not a table (the v1-v6 `macro_days` table became [QuickAddEntry] rows in v7).
 *
 * Grams are the sum of the KNOWN values. [proteinKnown] = at least one item of the day has a known protein value
 * (false: protein is n/a for the whole day, [proteinG] is 0). [proteinComplete] = every item's protein is known, so
 * [proteinG] is the real total (false: it is only a lower bound). Same idea for carbs/fat.
 */
data class MacroDay(
    val date: LocalDate,
    val proteinG: Int, val carbsG: Int, val fatG: Int, val kcal: Int,
    val kcalIsAuto: Boolean = true,
    val updatedAt: Instant = Instant.EPOCH,
    val proteinKnown: Boolean = true,
    val proteinComplete: Boolean = true,
    val carbsKnown: Boolean = true,
    val fatKnown: Boolean = true,
    val carbsComplete: Boolean = true,
    val fatComplete: Boolean = true,
) {
    /** Protein is the real total of the day (no n/a item): safe for averages and "missed" flags. */
    val proteinExact get() = proteinKnown && proteinComplete
    val carbsExact get() = carbsKnown && carbsComplete
    val fatExact get() = fatKnown && fatComplete
}

/**
 * One "Quick add" of a day (several per day): calories plus optional macros. A null macro means unknown ("n/a"),
 * which is NOT zero. [name] is optional ("Pizza out"); the UI falls back to "Quick add".
 * [kcalIsAuto] = kcal was computed as 4·P + 4·C + 9·F (only possible when all three macros are known).
 */
@Entity(tableName = "quick_add_entries", indices = [Index("date")])
data class QuickAddEntry(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val name: String?,
    val kcal: Int,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?,
    val kcalIsAuto: Boolean,
    val createdAt: Instant,
) {
    val displayName: String get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Quick add"
}

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
    /** v9: RAW / COOKED when it matters (copilot base table copies, user choice); null = not relevant. */
    val state: FoodState? = null,
    /** v9: user synonyms, `|`-separated (matched by the copilot's FoodResolver). */
    val aliases: String? = null,
    /** v9: id of the base_foods row this food was copied from (source BASE). */
    val baseFoodId: String? = null,
    /** v9: Open Food Facts product with missing macros (stored as 0, owner decision 11): shown as incomplete. */
    @ColumnInfo(defaultValue = "0") val incomplete: Boolean = false,
)

/**
 * One logged food of a day. Name/brand and macros are snapshots (survive food edits/deletion).
 * v9 provenance (copilot logs; null for entries added from the food screens): [loggedAt] = meal date AND time
 * (owner decision 2), [source], [captureId] = the capture that wrote it (Undo), [servings]/[portionLabel]/[portionGrams]
 * = the portion used ("0.5 x package (400 g)").
 */
@Entity(tableName = "food_entries", indices = [Index("date"), Index("captureId")])
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
    val loggedAt: Instant? = null,
    val source: EntrySource? = null,
    val captureId: String? = null,
    val servings: Double? = null,
    val portionLabel: String? = null,
    val portionGrams: Double? = null,
)
