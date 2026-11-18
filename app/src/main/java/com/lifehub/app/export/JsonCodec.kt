package com.lifehub.app.export

import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.data.repo.DataSnapshot
import com.lifehub.app.data.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.lifehub.app.domain.fatPct

class ImportException(message: String) : Exception(message)

object JsonCodec {
    const val SCHEMA_VERSION = 9
    private const val MIN_SCHEMA_VERSION = 1

    val README = "Life Hub export. All personal fitness data of one user. `goals` are objectives with a time window " +
        "(startDate→deadline) and links to `plans` (planLinks.sessionsNeeded = estimated sessions to reach the goal). " +
        "`plans` are training templates: ordered `exercises`, each with `sets`, `targetType` (REPS = fixed reps, MAX = as many " +
        "reps as possible, SECONDS = timed hold), `targetValue`, optional `weightKg` (added load) and `restSeconds`; " +
        "`dayType` (PULL, PUSH, LEGS, CORE or null) tags the kind of training day; `exercises` are the user's own exercise library: `category` PULL, PUSH, CORE or LEGS, `defaultTargetType` REPS or SECONDS; `iconKey` is the plan's icon (null = default); `weekdays` are ISO weekdays (1=Mon..7=Sun) on which the plan is scheduled. `sessions` are performed workouts " +
        "(`status` COMPLETED or IN_PROGRESS) with `sets`: `actualReps` for REPS/MAX, `actualSeconds` for SECONDS, `status` " +
        "DONE/SKIPPED/PENDING, `weightKg` added load. `quickAdds` are manual calorie/macro entries (any number per date): " +
        "optional `name`, `kcal` (always set), proteinG/carbsG/fatG (null = unknown, NOT zero; daily sums only add the known " +
        "values), kcalIsAuto=true means kcal = 4·protein + 4·carbs + 9·fat. Files up to schemaVersion 6 had `macros` (one per date) instead. `bodyEntries` are scale readings per date: weightKg, " +
        "muscleKg, fatKg, fatPct (derived). `settings.proteinTargetG` is the daily protein goal. Ids are UUID strings and are " +
        "referentially consistent within this file. `achievements` is the achievements log: `kind` (GOAL_ACHIEVED = big, PR_REPS, " +
        "PR_HOLD, PR_WEIGHT, PERFECT_WEEK_TRAINING, PERFECT_MONTH_TRAINING, PROTEIN_WEEK, SESSION_MILESTONE), `achievedOn` date, " +
        "optional links to exercise/goal/session, `dedupeKey` unique. `foods` are custom foods and cached Open Food Facts products " +
        "(values per 100 g: kcal100, protein100, carbs100, fat100; servingSizeG optional; imageUrl = product photo URL or null); `foodEntries` are logged foods per date with " +
        "a name snapshot, `grams` and the computed kcal/proteinG/carbsG/fatG. Daily totals = sum of `foodEntries` + `quickAdds` of that date. " +
        "`settings.kcalTarget`/`carbsTargetG`/`fatTargetG` are optional daily targets. " +
        "Since schemaVersion 8: foods may have `state` (RAW/COOKED), `aliases` (synonyms separated by |), `baseFoodId` (copied " +
        "from the bundled USDA-based base table) and `incomplete` (Open Food Facts product with missing macros, stored as 0); " +
        "`foodPortions` are named portions of a food in grams (`kind` PACKAGE = the whole pack, SERVING, PIECE, USER; one " +
        "`isDefault`); `householdMeasures` are grams per spoon/glass/slice/piece, generic or for one food; foodEntries written " +
        "by the copilot carry provenance: `loggedAt` (meal date and time, epoch millis), `source` (HISTORY, BASE_TABLE, " +
        "OPEN_FOOD_FACTS, LLM_ESTIMATE), `servings` + `portionLabel` + `portionGrams` (portion or measure used). Since schemaVersion 9: " +
        "`userMemory` holds the copilot's short facts about the user (`key`, `value`, `updatedAt`). Copilot chats, captures, " +
        "plan drafts and API keys are not exported."

    val json = Json {
        prettyPrint = true; encodeDefaults = true; explicitNulls = true
        ignoreUnknownKeys = true; coerceInputValues = true
    }

    fun toDto(s: DataSnapshot, exportedAt: ZonedDateTime, appVersion: String): ExportV1 = ExportV1(
        schemaVersion = SCHEMA_VERSION,
        exportedAt = exportedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        app = AppInfoDto("Life Hub", appVersion),
        readme = README,
        settings = SettingsDto(
            s.settings.proteinTargetG, s.settings.notifyTrain, s.settings.notifyProtein, s.settings.notifyWeight, s.settings.notifyAchievements,
            String.format("%02d:%02d", s.settings.dailyCheckTime.hour, s.settings.dailyCheckTime.minute),
            s.settings.homePeriod, s.settings.timezone,
            s.settings.kcalTarget, s.settings.carbsTargetG, s.settings.fatTargetG, notifyStreak = s.settings.notifyStreak,
        ),
        goals = s.goals.map { g ->
            GoalDto(
                g.id, g.name, g.notes, g.startDate.toString(), g.deadline.toString(), g.deadlineIsAuto, g.state,
                g.achievedAt?.toString(), g.createdAt.toEpochMilli(),
                s.links.filter { it.goalId == g.id }.map { PlanLinkDto(it.planId, it.sessionsNeeded) },
            )
        },
        plans = s.plans.map { p ->
            PlanDto(
                p.id, p.name, p.weekdays.sorted(), p.notes, p.isArchived, p.createdAt.toEpochMilli(), p.dayType, p.iconKey,
                s.planExercises.filter { it.planId == p.id }.sortedBy { it.orderIndex }.map {
                    PlanExerciseDto(it.id, it.planId, it.exerciseId, it.orderIndex, it.sets, it.targetType, it.targetValue, it.weightKg, it.restSeconds)
                },
            )
        },
        exercises = s.exercises.map { ExerciseDto(it.id, it.name, it.category.name, it.defaultTargetType, it.isCustom, it.isHidden) },
        sessions = s.sessions.map { x ->
            SessionDto(
                x.id, x.planId, x.planName, x.date.toString(), x.startedAt.toEpochMilli(), x.endedAt?.toEpochMilli(),
                x.durationSec, x.status, x.rpe, x.notes, x.isManual, x.currentSetIndex, x.restEndsAt?.toEpochMilli(),
                s.sets.filter { it.sessionId == x.id }.sortedWith(compareBy({ it.exerciseOrder }, { it.setIndex })).map {
                    SetDto(
                        it.id, it.sessionId, it.exerciseId, it.exerciseName, it.exerciseOrder, it.setIndex, it.targetType,
                        it.targetValue, it.actualReps, it.actualSeconds, it.weightKg, it.status, it.completedAt?.toEpochMilli(), it.restSeconds,
                    )
                },
            )
        },
        quickAdds = s.quickAdds.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })).map {
            QuickAddDto(it.id, it.date.toString(), it.name, it.kcal, it.proteinG, it.carbsG, it.fatG, it.kcalIsAuto, it.createdAt.toEpochMilli())
        },
        bodyEntries = s.bodyEntries.sortedBy { it.date }.map {
            BodyDto(it.date.toString(), it.weightKg, it.muscleKg, it.fatKg, it.updatedAt.toEpochMilli(),
                fatPct(it))
        },
        achievements = s.achievements.sortedWith(compareBy({ it.achievedOn }, { it.dedupeKey })).map {
            AchievementDto(it.id, it.kind, it.title, it.detail, it.value, it.unit, it.exerciseId, it.goalId, it.sessionId,
                it.achievedOn.toString(), it.createdAt.toEpochMilli(), it.dedupeKey)
        },
        foods = s.foods.sortedBy { it.id }.map {
            FoodDto(it.id, it.source, it.barcode, it.name, it.brand, it.kcal100, it.protein100, it.carbs100, it.fat100,
                it.servingSizeG, it.favorite, it.lastUsedAt?.toEpochMilli(), it.imageUrl, it.state, it.aliases, it.baseFoodId, it.incomplete)
        },
        foodEntries = s.foodEntries.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })).map {
            FoodEntryDto(it.id, it.date.toString(), it.foodId, it.name, it.brand, it.grams, it.kcal, it.proteinG, it.carbsG, it.fatG, it.createdAt.toEpochMilli(), it.imageUrl,
                it.loggedAt?.toEpochMilli(), it.source, it.servings, it.portionLabel, it.portionGrams)
        },
        foodPortions = s.portions.sortedWith(compareBy({ it.foodId }, { it.id })).map {
            FoodPortionDto(it.id, it.foodId, it.label, it.grams, it.isDefault, it.kind, it.createdAt.toEpochMilli())
        },
        householdMeasures = s.measures?.sortedBy { it.id }?.map {
            HouseholdMeasureDto(it.id, it.label, it.grams, it.foodId, it.baseFoodId, it.updatedAt.toEpochMilli())
        },
        userMemory = s.memory?.sortedBy { it.key }?.map { UserMemoryDto(it.key, it.value, it.updatedAt.toEpochMilli()) },
    )

    fun encode(s: DataSnapshot, exportedAt: ZonedDateTime, appVersion: String): String =
        json.encodeToString(ExportV1.serializer(), toDto(s, exportedAt, appVersion))

    fun decode(text: String): DataSnapshot {
        val dto = try {
            val root = json.parseToJsonElement(text) as? JsonObject ?: throw ImportException("Not a Life Hub export")
            val v = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: throw ImportException("Missing schemaVersion")
            if (v !in MIN_SCHEMA_VERSION..SCHEMA_VERSION) throw ImportException("Unsupported schemaVersion $v")
            json.decodeFromJsonElement(ExportV1.serializer(), root)
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("Malformed file (${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName})")
        }
        return try { fromDto(dto) } catch (e: Exception) {
            throw ImportException("Invalid data (${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName})")
        }
    }

    private fun fromDto(d: ExportV1): DataSnapshot {
        val def = Settings()
        val st = d.settings
        val settings = Settings(
            proteinTargetG = st.proteinTargetG.coerceIn(0, 500),
            notifyTrain = st.notifyTrain, notifyProtein = st.notifyProtein, notifyWeight = st.notifyWeight, notifyAchievements = st.notifyAchievements,
            notifyStreak = st.notifyStreak,
            dailyCheckTime = runCatching { LocalTime.parse(st.dailyCheckTime) }.getOrDefault(def.dailyCheckTime),
            homePeriod = st.homePeriod, timezone = st.timezone ?: def.timezone,
            kcalTarget = st.kcalTarget, carbsTargetG = st.carbsTargetG, fatTargetG = st.fatTargetG,
        )
        val planIds = d.plans.map { it.id }.toSet()
        val goalIds = d.goals.map { it.id }.toSet()
        val sessionIds = d.sessions.map { it.id }.toSet()
        // Pre-v6 files carry the former built-in library: keep only what is referenced, as the user's own exercises.
        val usedExerciseIds = (d.plans.flatMap { p -> p.exercises.map { it.exerciseId } } +
            d.sessions.flatMap { x -> x.sets.map { it.exerciseId } } + d.achievements.mapNotNull { it.exerciseId }).toSet()
        val exercises = d.exercises.filter { it.isCustom || it.id in usedExerciseIds }
            .map { Exercise(it.id, it.name, ExerciseCategory.fromLegacy(it.category, it.name), it.defaultTargetType, isCustom = true, isHidden = it.isHidden) }
        val exerciseIds = exercises.map { it.id }.toSet()
        fun inst(l: Long) = Instant.ofEpochMilli(l)
        return DataSnapshot(
            settings = settings,
            goals = d.goals.map { Goal(it.id, it.name, it.notes, LocalDate.parse(it.startDate), LocalDate.parse(it.deadline), it.deadlineIsAuto, it.state, it.achievedAt?.let(LocalDate::parse), inst(it.createdAt)) },
            links = d.goals.flatMap { g -> g.planLinks.filter { it.planId in planIds }.map { GoalPlanLink(g.id, it.planId, it.sessionsNeeded) } }
                .filter { it.goalId in goalIds },
            plans = d.plans.map { Plan(it.id, it.name, it.weekdays.toSet(), it.notes, it.isArchived, inst(it.createdAt), it.dayType, it.iconKey) },
            planExercises = d.plans.flatMap { p -> p.exercises.map { PlanExercise(it.id, p.id, it.exerciseId, it.orderIndex, it.sets, it.targetType, it.targetValue, it.weightKg, it.restSeconds) } },
            exercises = exercises,
            sessions = d.sessions.map {
                WorkoutSession(it.id, it.planId?.takeIf { p -> p in planIds }, it.planName, LocalDate.parse(it.date), inst(it.startedAt),
                    it.endedAt?.let(::inst), it.durationSec, it.status, it.rpe, it.notes, it.isManual, it.currentSetIndex, it.restEndsAt?.let(::inst))
            },
            sets = d.sessions.flatMap { x -> x.sets.map {
                SessionSet(it.id, x.id, it.exerciseId, it.exerciseName, it.exerciseOrder, it.setIndex, it.targetType, it.targetValue,
                    it.actualReps, it.actualSeconds, it.weightKg, it.status, it.completedAt?.let(::inst), it.restSeconds)
            } }.filter { it.sessionId in sessionIds },
            // v7 quickAdds + legacy per-day macros (<= v6), converted like MIGRATION_6_7 (id "macro-<epochDay>", no name).
            quickAdds = d.quickAdds.map {
                QuickAddEntry(it.id, LocalDate.parse(it.date), it.name?.trim()?.takeIf { n -> n.isNotEmpty() }, it.kcal, it.proteinG, it.carbsG, it.fatG, it.kcalIsAuto, inst(it.createdAt))
            } + d.macros.map {
                val date = LocalDate.parse(it.date)
                QuickAddEntry("macro-${date.toEpochDay()}", date, null, it.kcal, it.proteinG, it.carbsG, it.fatG, it.kcalIsAuto, inst(it.updatedAt))
            },
            bodyEntries = d.bodyEntries.map { BodyEntry(LocalDate.parse(it.date), it.weightKg, it.muscleKg, it.fatKg, inst(it.updatedAt)) },
            foods = d.foods.map { Food(it.id, it.source, it.barcode, it.name, it.brand, it.kcal100, it.protein100, it.carbs100, it.fat100, it.servingSizeG, it.favorite, it.lastUsedAt?.let(::inst), it.imageUrl, it.state, it.aliases, it.baseFoodId, it.incomplete) },
            foodEntries = d.foodEntries.map {
                FoodEntry(it.id, LocalDate.parse(it.date), it.foodId, it.name, it.brand, it.grams, it.kcal, it.proteinG, it.carbsG, it.fatG, inst(it.createdAt), it.imageUrl,
                    it.loggedAt?.let(::inst), it.source, null, it.servings, it.portionLabel, it.portionGrams)
            },
            portions = d.foodPortions.map { FoodPortion(it.id, it.foodId, it.label, it.grams, it.isDefault, it.kind, inst(it.createdAt)) },
            measures = d.householdMeasures?.map { HouseholdMeasure(it.id, it.label, it.grams, it.foodId, it.baseFoodId, inst(it.updatedAt)) },
            memory = d.userMemory?.map { UserMemory(it.key, it.value, inst(it.updatedAt)) },
            achievements = d.achievements.distinctBy { it.dedupeKey }.map {
                Achievement(it.id, it.kind, it.title, it.detail, it.value, it.unit, it.exerciseId?.takeIf { e -> e in exerciseIds },
                    it.goalId?.takeIf { g -> g in goalIds }, it.sessionId?.takeIf { x -> x in sessionIds },
                    LocalDate.parse(it.achievedOn), inst(it.createdAt), it.dedupeKey)
            },
        )
    }
}
