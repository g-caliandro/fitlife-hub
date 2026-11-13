# Life Hub — Architecture

Version 1.0 · Implements `docs/01-spec.md` · Visual/UX rules: `docs/03-design.md`

Implementers: follow this literally. If something is unspecified, choose the simplest option and leave a `// DECISION:` comment. Do **not** add libraries or change versions in `gradle/libs.versions.toml` (offline build). Available: Compose BOM 2024.12.01 (Material3 1.3.x, icons-extended), Navigation 2.8.5, Room 2.6.1 (KSP, room-ktx), WorkManager 2.10.0, kotlinx.serialization 1.7.3, coroutines 1.9.0, JUnit4, Robolectric 4.14.1, Roborazzi 1.32.2. **No** `kotlinx-coroutines-test`, no DataStore, no Hilt, no zip/CSV/chart libs (use `java.util.zip`, hand-written CSV, Compose Canvas).

---

## 0. Conventions

- Root package `com.lifehub.app`; source root `app/src/main/java/com/lifehub/app/` (written `…/` below). Tests: `app/src/test/java/com/lifehub/app/`.
- IDs: `String` UUID (`UUID.randomUUID().toString()`); built-in exercises use fixed ids (§3.4).
- `LocalDate` stored as `Long` epochDay; `Instant` as `Long` epoch millis; `LocalTime` as `Int` minute-of-day (prefs). Converters in `data/db/Converters.kt`.
- Enums stored by name (Room default for enums is fine; we still declare converters only for java.time and `Set<Int>`).
- All "now"/"today" go through `TimeSource` (never call `LocalDate.now()` directly) so tests are deterministic.
- UI pattern per screen: `XxxRoute(...)` (stateful: gets ViewModel, collects state, handles navigation) + `XxxScreen(state, callbacks)` (stateless, previewable, screenshot-tested).
- User-facing strings: each WP owns its own `app/src/main/res/values/strings_<wp>.xml` (e.g. `strings_goals.xml`). Never edit another WP's strings file. `strings.xml` (WP0) holds only `app_name` and shared words (`save`, `cancel`, `delete`, `undo`, `edit`, `dash` = "—").
- Formatting helpers live in `core/designsystem/Format.kt` (WP0) — use them, don't re-invent.

---

## 1. Package layout (final state; owner WP in brackets)

```
…/LifeHubApp.kt                         [WP0] Application, owns AppContainer, creates channels, schedules worker
…/MainActivity.kt                       [WP0] setContent, deep-link intent → route, permission launcher host
…/di/AppContainer.kt                    [WP0]
…/core/time/TimeSource.kt               [WP0]
…/core/designsystem/Theme.kt            [WP0] LhTheme, LhColors, LocalLhColors
…/core/designsystem/Tokens.kt           [WP0] LhSpacing, LhRadius, LhType
…/core/designsystem/Format.kt           [WP0] number/date formatters
…/core/designsystem/components/*.kt     [WP0] one file per component (see 03-design §2)
…/core/ui/GoalCard.kt                   [WP0] shared by Home (WP4) and Goals list (WP1)
…/core/ui/ExercisePickerSheet.kt        [WP0] shared by Plan editor (WP1) and Live workout (WP2)
…/core/ui/WeekdayChips.kt               [WP0] shared by Plans (WP1), Goal detail (WP1), Workout start (WP2)
…/core/ui/charts/BarChart.kt            [WP4]
…/core/ui/charts/LineChart.kt           [WP4]
…/core/ui/charts/ChartScaffold.kt       [WP4] axes, gridlines, "No data"
…/data/model/Enums.kt                   [WP0]
…/data/model/Entities.kt                [WP0] all @Entity classes
…/data/model/Relations.kt               [WP0] PlanWithExercises, SessionWithSets, etc.
…/data/db/Converters.kt                 [WP0]
…/data/db/LifeHubDatabase.kt            [WP0]
…/data/db/dao/{GoalDao,PlanDao,ExerciseDao,SessionDao,MacroDao,BodyDao}.kt   [WP0]
…/data/prefs/SettingsRepository.kt      [WP0]
…/data/repo/{GoalRepository,PlanRepository,ExerciseRepository,SessionRepository,MacroRepository,BodyRepository,BackupRepository}.kt [WP0] interface + impl in same file
…/data/repo/DataSnapshot.kt             [WP0]
…/domain/Period.kt                      [WP0] §4.1
…/domain/GoalProgress.kt                [WP0] §4.6, §4.7
…/domain/Nutrition.kt                   [WP0] autoKcal, fatPct
…/domain/Insights.kt                    [WP4] §4.2–4.5
…/navigation/Routes.kt                  [WP0]
…/navigation/LhNavHost.kt               [WP0]
…/navigation/AppScaffold.kt             [WP0] bottom bar + active-workout bar + snackbar host
…/navigation/DeepLinks.kt               [WP0] EXTRA_ROUTE + helpers
…/notifications/Channels.kt             [WP0] channel ids + creation
…/notifications/NotificationScheduler.kt[WP5] (stub by WP0)
…/notifications/DailyCheckWorker.kt     [WP5]
…/notifications/NotificationRules.kt    [WP5] pure
…/notifications/Notifier.kt             [WP5]
…/export/ExportModels.kt                [WP5] @Serializable DTOs schemaVersion 1
…/export/JsonCodec.kt                   [WP5] snapshot ⇄ JSON
…/export/CsvExporter.kt                 [WP5]
…/export/ExportFiles.kt                 [WP5] write to cache/exports, share intent, copy to SAF uri
…/mock/MockDataGenerator.kt             [WP5] pure: (today) → DataSnapshot
…/feature/home/*                        [WP4] (placeholder by WP0)
…/feature/calendar/*                    [WP3] (placeholder by WP0)
…/feature/nutrition/*                   [WP3] (placeholder by WP0)
…/feature/body/*                        [WP3] (placeholder by WP0)
…/feature/goals/*                       [WP1] (placeholder by WP0)
…/feature/plans/*                       [WP1] (placeholder by WP0) — plans + exercise library
…/feature/workout/*                     [WP2] (placeholder by WP0) — start, live, session edit/manual log, RestTimerService
…/feature/settings/*                    [WP5] (placeholder by WP0)
```

---

## 2. Time

```kotlin
// core/time/TimeSource.kt
interface TimeSource {
    val zone: ZoneId
    fun now(): Instant
    fun today(): LocalDate = now().atZone(zone).toLocalDate()
}
class SystemTimeSource : TimeSource {
    override val zone: ZoneId get() = ZoneId.systemDefault()
    override fun now(): Instant = Instant.now()
}
class FixedTimeSource(var instant: Instant, override val zone: ZoneId = ZoneOffset.UTC) : TimeSource {
    override fun now() = instant
}
```

---

## 3. Data layer

### 3.1 Enums (`data/model/Enums.kt`)

```kotlin
enum class GoalState { ACTIVE, ACHIEVED, ARCHIVED }
enum class ExerciseCategory { PULL, PUSH, CORE, LEGS }   // DB v6: ISOMETRIC→CORE, ACCESSORY→PUSH (+ name rules, fromLegacy)
enum class TargetType { REPS, MAX, SECONDS }          // Exercise.defaultTargetType only REPS|SECONDS
enum class SessionStatus { IN_PROGRESS, COMPLETED }
enum class SetStatus { PENDING, DONE, SKIPPED }
enum class HomePeriod(val days: Int) { ONE_WEEK(7), TWO_WEEKS(14) }
enum class NotifType { TRAIN, PROTEIN, WEIGHT }
```

### 3.2 Entities (`data/model/Entities.kt`)

Entities **are** the app's model classes (no separate domain models/mappers). Names without `Entity` suffix.

```kotlin
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
    val restSeconds: Int,          // ADDENDUM to spec §3.7: snapshot of rest for this exercise (90 if ad-hoc). Exported.
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
```

### 3.3 Relations (`data/model/Relations.kt`) — plain data classes, assembled in repos (no `@Relation`)

```kotlin
data class PlanExerciseItem(val planExercise: PlanExercise, val exercise: Exercise)
data class PlanWithExercises(val plan: Plan, val exercises: List<PlanExerciseItem>)   // ordered by orderIndex
data class SessionWithSets(val session: WorkoutSession, val sets: List<SessionSet>)  // ordered by exerciseOrder, setIndex
data class GoalWithLinks(val goal: Goal, val links: List<GoalPlanLink>)
```

### 3.4 Converters & DB

```kotlin
// data/db/Converters.kt
class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun longToInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
    @TypeConverter fun weekdaysToString(s: Set<Int>): String = s.sorted().joinToString(",")
    @TypeConverter fun stringToWeekdays(v: String): Set<Int> = if (v.isBlank()) emptySet() else v.split(",").map(String::toInt).toSet()
}

// data/db/LifeHubDatabase.kt
@Database(
    entities = [Goal::class, Plan::class, GoalPlanLink::class, Exercise::class, PlanExercise::class,
                WorkoutSession::class, SessionSet::class, MacroDay::class, BodyEntry::class],
    version = 1, exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LifeHubDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun planDao(): PlanDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun macroDao(): MacroDao
    abstract fun bodyDao(): BodyDao
    companion object {
        fun build(context: Context): LifeHubDatabase =
            Room.databaseBuilder(context, LifeHubDatabase::class.java, "lifehub.db").build()
        fun inMemory(context: Context): LifeHubDatabase =
            Room.inMemoryDatabaseBuilder(context, LifeHubDatabase::class.java).allowMainThreadQueries().build()
    }
}
```

Room enables `PRAGMA foreign_keys` automatically because entities declare FKs. Query params of type `LocalDate` go through converters (pass `LocalDate`, not `Long`). Enum literals in SQL use the name, e.g. `'COMPLETED'`.

### 3.5 DAOs (`data/db/dao/*.kt`)

Every DAO also has `getAll(): List<T>`, `insertAll(items: List<T>)` (`OnConflictStrategy.ABORT`) and `deleteAll()` for backup/mock (WP5 relies on them).

```kotlin
@Dao interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY deadline ASC") fun observeAll(): Flow<List<Goal>>
    @Query("SELECT * FROM goals WHERE id = :id") fun observe(id: String): Flow<Goal?>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun get(id: String): Goal?
    @Upsert suspend fun upsert(goal: Goal)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM goal_plan_links") fun observeAllLinks(): Flow<List<GoalPlanLink>>
    @Query("SELECT * FROM goal_plan_links WHERE goalId = :goalId") fun observeLinksForGoal(goalId: String): Flow<List<GoalPlanLink>>
    @Query("SELECT * FROM goal_plan_links WHERE planId = :planId") suspend fun getLinksForPlan(planId: String): List<GoalPlanLink>
    @Upsert suspend fun upsertLink(link: GoalPlanLink)
    @Query("DELETE FROM goal_plan_links WHERE goalId = :goalId AND planId = :planId") suspend fun deleteLink(goalId: String, planId: String)
    @Query("DELETE FROM goal_plan_links WHERE planId = :planId") suspend fun deleteLinksForPlan(planId: String)

    @Query("SELECT * FROM goals") suspend fun getAll(): List<Goal>
    @Query("SELECT * FROM goal_plan_links") suspend fun getAllLinks(): List<GoalPlanLink>
    @Insert suspend fun insertAll(items: List<Goal>)
    @Insert suspend fun insertAllLinks(items: List<GoalPlanLink>)
    @Query("DELETE FROM goals") suspend fun deleteAll()
    @Query("DELETE FROM goal_plan_links") suspend fun deleteAllLinks()
}

@Dao interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY isArchived ASC, name COLLATE NOCASE ASC") fun observeAll(): Flow<List<Plan>>
    @Query("SELECT * FROM plans WHERE id = :id") fun observe(id: String): Flow<Plan?>
    @Query("SELECT * FROM plans WHERE id = :id") suspend fun get(id: String): Plan?
    @Upsert suspend fun upsert(plan: Plan)
    @Query("DELETE FROM plans WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM plan_exercises ORDER BY planId, orderIndex") fun observeAllExercises(): Flow<List<PlanExercise>>
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex") fun observeExercises(planId: String): Flow<List<PlanExercise>>
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex") suspend fun getExercises(planId: String): List<PlanExercise>
    @Query("DELETE FROM plan_exercises WHERE planId = :planId") suspend fun deleteExercises(planId: String)
    @Insert suspend fun insertExercises(items: List<PlanExercise>)

    @Query("SELECT * FROM plans") suspend fun getAll(): List<Plan>
    @Query("SELECT * FROM plan_exercises") suspend fun getAllExercises(): List<PlanExercise>
    @Insert suspend fun insertAll(items: List<Plan>)
    @Query("DELETE FROM plans") suspend fun deleteAll()
    @Query("DELETE FROM plan_exercises") suspend fun deleteAllExercises()
}

@Dao interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY category, name") fun observeAll(): Flow<List<Exercise>>
    @Query("SELECT * FROM exercises WHERE id = :id") suspend fun get(id: String): Exercise?
    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1") suspend fun findByName(name: String): Exercise?   // NOCASE via column collation
    @Upsert suspend fun upsert(exercise: Exercise)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(items: List<Exercise>)
    @Query("DELETE FROM exercises WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT (SELECT COUNT(*) FROM plan_exercises WHERE exerciseId = :id) + (SELECT COUNT(*) FROM session_sets WHERE exerciseId = :id)")
    suspend fun referenceCount(id: String): Int
    @Query("SELECT COUNT(*) FROM exercises") suspend fun count(): Int
    @Query("SELECT * FROM exercises") suspend fun getAll(): List<Exercise>
    @Insert suspend fun insertAll(items: List<Exercise>)
    @Query("DELETE FROM exercises") suspend fun deleteAll()
}

@Dao interface SessionDao {
    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' LIMIT 1") fun observeInProgress(): Flow<WorkoutSession?>
    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' LIMIT 1") suspend fun getInProgress(): WorkoutSession?
    @Query("SELECT * FROM sessions WHERE id = :id") fun observe(id: String): Flow<WorkoutSession?>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun get(id: String): WorkoutSession?
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' ORDER BY date DESC, startedAt DESC") fun observeCompleted(): Flow<List<WorkoutSession>>
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND date BETWEEN :from AND :to ORDER BY date, startedAt")
    fun observeCompletedBetween(from: LocalDate, to: LocalDate): Flow<List<WorkoutSession>>
    @Upsert suspend fun upsert(session: WorkoutSession)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseOrder, setIndex") fun observeSets(sessionId: String): Flow<List<SessionSet>>
    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseOrder, setIndex") suspend fun getSets(sessionId: String): List<SessionSet>
    @Query("""SELECT ss.* FROM session_sets ss JOIN sessions s ON s.id = ss.sessionId
              WHERE s.status = 'COMPLETED' AND s.date BETWEEN :from AND :to ORDER BY s.date, ss.exerciseOrder, ss.setIndex""")
    fun observeCompletedSetsBetween(from: LocalDate, to: LocalDate): Flow<List<SessionSet>>
    @Upsert suspend fun upsertSet(set: SessionSet)
    @Upsert suspend fun upsertSets(sets: List<SessionSet>)
    @Query("DELETE FROM session_sets WHERE id = :id") suspend fun deleteSet(id: String)
    @Query("DELETE FROM session_sets WHERE sessionId = :sessionId") suspend fun deleteSets(sessionId: String)

    @Query("SELECT * FROM sessions") suspend fun getAll(): List<WorkoutSession>
    @Query("SELECT * FROM session_sets") suspend fun getAllSets(): List<SessionSet>
    @Insert suspend fun insertAll(items: List<WorkoutSession>)
    @Insert suspend fun insertAllSets(items: List<SessionSet>)
    @Query("DELETE FROM sessions") suspend fun deleteAll()
    @Query("DELETE FROM session_sets") suspend fun deleteAllSets()
}

@Dao interface MacroDao {
    @Query("SELECT * FROM macro_days WHERE date BETWEEN :from AND :to ORDER BY date") fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<MacroDay>>
    @Query("SELECT * FROM macro_days WHERE date = :date") fun observe(date: LocalDate): Flow<MacroDay?>
    @Query("SELECT * FROM macro_days WHERE date = :date") suspend fun get(date: LocalDate): MacroDay?
    @Upsert suspend fun upsert(day: MacroDay)
    @Query("DELETE FROM macro_days WHERE date = :date") suspend fun delete(date: LocalDate)
    @Query("SELECT * FROM macro_days ORDER BY date") suspend fun getAll(): List<MacroDay>
    @Insert suspend fun insertAll(items: List<MacroDay>)
    @Query("DELETE FROM macro_days") suspend fun deleteAll()
}

@Dao interface BodyDao {
    @Query("SELECT * FROM body_entries ORDER BY date DESC") fun observeAll(): Flow<List<BodyEntry>>
    @Query("SELECT * FROM body_entries WHERE date BETWEEN :from AND :to ORDER BY date") fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<BodyEntry>>
    @Query("SELECT * FROM body_entries WHERE date = :date") fun observe(date: LocalDate): Flow<BodyEntry?>
    @Query("SELECT * FROM body_entries WHERE date = :date") suspend fun get(date: LocalDate): BodyEntry?
    @Query("SELECT MAX(date) FROM body_entries") suspend fun latestDate(): LocalDate?
    @Upsert suspend fun upsert(entry: BodyEntry)
    @Query("DELETE FROM body_entries WHERE date = :date") suspend fun delete(date: LocalDate)
    @Query("SELECT * FROM body_entries ORDER BY date") suspend fun getAll(): List<BodyEntry>
    @Insert suspend fun insertAll(items: List<BodyEntry>)
    @Query("DELETE FROM body_entries") suspend fun deleteAll()
}
```

### 3.6 Exercise library (no seed since DB v6)

Superseded: there is no built-in library. A fresh install starts with no exercises; the user creates them all (library, or inline from the exercise picker). `MIGRATION_5_6` deletes unreferenced former built-ins, turns referenced ones into custom exercises and remaps ISOMETRIC/ACCESSORY; JSON import (export v6) applies the same rules.

### 3.7 Settings (`data/prefs/SettingsRepository.kt`)

SharedPreferences file `"lifehub_settings"`. No Room row.

```kotlin
data class Settings(
    val proteinTargetG: Int = 150,
    val notifyTrain: Boolean = true,
    val notifyProtein: Boolean = true,
    val notifyWeight: Boolean = true,
    val dailyCheckTime: LocalTime = LocalTime.of(18, 0),   // pref key "dailyCheckMinute": Int minute-of-day
    val homePeriod: HomePeriod = HomePeriod.ONE_WEEK,
    val timezone: String = ZoneId.systemDefault().id,        // informational; written on every app start
)

class SettingsRepository(context: Context) {
    val settings: StateFlow<Settings>                      // MutableStateFlow, reloaded from prefs after each write
    fun current(): Settings = settings.value
    fun setProteinTarget(g: Int)
    fun setNotify(type: NotifType, enabled: Boolean)
    fun setDailyCheckTime(time: LocalTime)
    fun setHomePeriod(p: HomePeriod)
    fun replace(s: Settings)                               // import / reset
    fun lastNotified(type: NotifType): LocalDate?          // key "lastNotified_<TYPE>" epochDay
    fun setLastNotified(type: NotifType, date: LocalDate)
    var notificationPermissionAsked: Boolean
    fun clear()                                            // reset to defaults (keeps nothing)
}
```

Writes use `apply()`. Only the UI process writes Settings fields; the worker reads `current()` and writes only `lastNotified_*`.

### 3.8 Repositories (`data/repo/*.kt`, interface + `XxxRepositoryImpl` in the same file)

All suspend writes run on Room's executors (Room suspend DAOs are main-safe). Multi-table writes use `db.withTransaction {}`.

```kotlin
interface GoalRepository {
    fun observeGoals(): Flow<List<GoalWithLinks>>
    fun observeGoal(id: String): Flow<GoalWithLinks?>
    suspend fun get(id: String): Goal?
    suspend fun save(goal: Goal)                                         // upsert
    suspend fun setState(id: String, state: GoalState, today: LocalDate) // ACHIEVED sets achievedAt=today; ACTIVE clears it
    suspend fun delete(id: String)                                       // links cascade; plans untouched
    suspend fun upsertLink(link: GoalPlanLink)
    suspend fun removeLink(goalId: String, planId: String)
}

interface PlanRepository {
    fun observePlans(): Flow<List<PlanWithExercises>>                     // includes archived; UI filters
    fun observePlan(id: String): Flow<PlanWithExercises?>
    suspend fun get(id: String): PlanWithExercises?
    suspend fun getLinks(planId: String): List<GoalPlanLink>
    /** Upserts plan, replaces its PlanExercise rows (orderIndex re-numbered 0..n-1 from list order) and its goal links. */
    suspend fun save(plan: Plan, exercises: List<PlanExercise>, links: List<GoalPlanLink>)
    suspend fun duplicate(id: String, now: Instant): String               // returns new id; "<name> (copy)", no links
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun delete(id: String)                                       // sessions keep planName, planId → NULL
}

sealed interface ExerciseSaveResult { data object Ok : ExerciseSaveResult; data object DuplicateName : ExerciseSaveResult }
sealed interface ExerciseDeleteResult { data object Ok : ExerciseDeleteResult; data object InUse : ExerciseDeleteResult; data object BuiltIn : ExerciseDeleteResult }

interface ExerciseRepository {
    fun observeAll(): Flow<List<Exercise>>                               // includes hidden; pickers filter !isHidden
    suspend fun get(id: String): Exercise?
    suspend fun save(exercise: Exercise): ExerciseSaveResult             // name trimmed; unique NOCASE excluding self
    suspend fun setHidden(id: String, hidden: Boolean)
    suspend fun delete(id: String): ExerciseDeleteResult
}

class ActiveSessionExistsException : IllegalStateException()

interface SessionRepository {
    fun observeInProgress(): Flow<SessionWithSets?>
    fun observeSession(id: String): Flow<SessionWithSets?>
    fun observeCompleted(): Flow<List<WorkoutSession>>                    // all, no sets (calendar markers, goal progress, rules)
    fun observeCompletedWithSets(from: LocalDate, to: LocalDate): Flow<List<SessionWithSets>>
    /** F-WO-1. Throws ActiveSessionExistsException if one is IN_PROGRESS. Creates one PENDING SessionSet per planned set. */
    suspend fun startFromPlan(planId: String, now: Instant, today: LocalDate): String
    suspend fun discard(sessionId: String)
    suspend fun updateSession(session: WorkoutSession)                    // currentSetIndex, restEndsAt, notes, rpe, date…
    suspend fun upsertSet(set: SessionSet)
    suspend fun upsertSets(sets: List<SessionSet>)
    suspend fun deleteSet(setId: String)
    /** F-WO-10: PENDING → SKIPPED, status COMPLETED, endedAt=now, durationSec, restEndsAt=null. */
    suspend fun finish(sessionId: String, notes: String?, rpe: Int?, now: Instant)
    /** F-WO-8 / F-WO-13: writes session + replaces all its sets in one transaction. */
    suspend fun saveFull(session: WorkoutSession, sets: List<SessionSet>)
    suspend fun delete(sessionId: String)
}

interface MacroRepository {
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<MacroDay>>
    suspend fun get(date: LocalDate): MacroDay?
    /** F-NUT-1/2. kcal null ⇒ autoKcal + kcalIsAuto=true. */
    suspend fun upsert(date: LocalDate, proteinG: Int, carbsG: Int, fatG: Int, kcal: Int?, now: Instant)
    suspend fun delete(date: LocalDate)
}

interface BodyRepository {
    fun observeAll(): Flow<List<BodyEntry>>
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<BodyEntry>>
    suspend fun get(date: LocalDate): BodyEntry?
    suspend fun upsert(entry: BodyEntry)
    suspend fun delete(date: LocalDate)
    suspend fun latestDate(): LocalDate?
}

// data/repo/DataSnapshot.kt — the unit of export/import/mock/clear
data class DataSnapshot(
    val settings: Settings,
    val goals: List<Goal>, val links: List<GoalPlanLink>,
    val plans: List<Plan>, val planExercises: List<PlanExercise>,
    val exercises: List<Exercise>,
    val sessions: List<WorkoutSession>, val sets: List<SessionSet>,
    val macros: List<MacroDay>, val bodyEntries: List<BodyEntry>,
)

interface BackupRepository {
    suspend fun snapshot(): DataSnapshot
    /** Single transaction: deleteAll children→parents, insertAll parents→children
        (exercises, plans, planExercises, goals, links, sessions, sets, macros, body), then settings.replace(). */
    suspend fun replaceAll(snapshot: DataSnapshot)
    /** F-SET-6: wipe all tables + settings.clear(). */
    suspend fun clearAll()
    suspend fun isEmpty(): Boolean      // no goals, plans, sessions, macros, body entries
}
```

`observeCompletedWithSets` = `combine(dao.observeCompletedBetween, dao.observeCompletedSetsBetween)` grouped by sessionId.

---

## 4. Domain (pure Kotlin, no Android imports; unit-tested)

```kotlin
// domain/Period.kt  [WP0]  spec §4.1
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(d: LocalDate): Boolean = !d.isBefore(start) && !d.isAfter(end)
    val days: List<LocalDate>   // start..end inclusive
}
object Periods {
    fun weekStart(d: LocalDate): LocalDate                                  // Monday ≤ d
    fun period(today: LocalDate, p: HomePeriod, offset: Int): DateRange     // offset 0 = current
    fun previous(r: DateRange): DateRange                                   // same length, immediately before
    fun monthGrid(month: YearMonth): List<LocalDate>                        // Monday-aligned, 35 or 42 days, for calendar
}

// domain/GoalProgress.kt  [WP0]  spec §4.6, §4.7
data class TimeProgress(val totalDays: Int, val elapsedDays: Int, val fraction: Float, val daysLeft: Int)
fun timeProgress(start: LocalDate, deadline: LocalDate, today: LocalDate): TimeProgress
fun sessionsDone(goal: Goal, planId: String, completed: List<WorkoutSession>, today: LocalDate): Int
data class PlanProgress(val planId: String, val planName: String, val done: Int, val needed: Int?) {
    val fraction: Float? get() = needed?.let { (done.toFloat() / it).coerceAtMost(1f) }
}
fun planProgress(goal: GoalWithLinks, plans: List<Plan>, completed: List<WorkoutSession>, today: LocalDate): List<PlanProgress>

// domain/Nutrition.kt  [WP0]
fun autoKcal(proteinG: Int, carbsG: Int, fatG: Int): Int = 4 * proteinG + 4 * carbsG + 9 * fatG
fun fatPct(e: BodyEntry): Double? = e.fatKg?.let { it / e.weightKg * 100 }

// domain/Insights.kt  [WP4]  spec §4.2–4.5. Raw doubles; rounding only for display (except where spec says integer).
data class Metric<T : Number>(val current: T?, val previous: T?) {
    val delta: Double? get() = if (current == null || previous == null) null else current.toDouble() - previous.toDouble()
}
fun lastBodyValue(entries: List<BodyEntry>, r: DateRange, field: (BodyEntry) -> Double?): Double?
fun sessionsCount(sessions: List<WorkoutSession>, r: DateRange): Int                    // COMPLETED only
fun avgRepsPerSet(sessions: List<SessionWithSets>, r: DateRange): Double?
fun volumeReps(sessions: List<SessionWithSets>, r: DateRange): Int
fun holdSeconds(sessions: List<SessionWithSets>, r: DateRange): Int
data class Adherence(val done: Int, val planned: Int) { val percent: Int? get() = if (planned == 0) null else (done * 100.0 / planned).roundToInt() }
fun adherence(plans: List<Plan>, sessions: List<WorkoutSession>, r: DateRange, today: LocalDate): Adherence
fun avgProtein(macros: List<MacroDay>, r: DateRange): Int?                              // roundToInt
fun avgKcal(macros: List<MacroDay>, r: DateRange): Int?
fun daysOnTarget(macros: List<MacroDay>, r: DateRange, targetG: Int): Pair<Int, Int>    // (onTarget, logged)

data class InsightInput(
    val today: LocalDate, val current: DateRange, val previous: DateRange,
    val plans: List<Plan>, val sessions: List<SessionWithSets>,   // COMPLETED, covering previous.start..current.end
    val macros: List<MacroDay>, val body: List<BodyEntry>, val proteinTargetG: Int,
)
data class Insights(
    val weight: Metric<Double>, val muscle: Metric<Double>, val fat: Metric<Double>,
    val sessions: Metric<Int>, val adherence: Pair<Adherence, Adherence>,   // current, previous
    val adherenceDeltaPts: Int?,                                            // null if either percent null
    val avgRepsPerSet: Metric<Double>, val volume: Metric<Int>, val holdSeconds: Metric<Int>,
    val avgProtein: Metric<Int>, val avgKcal: Metric<Int>, val proteinOnTarget: Pair<Int, Int>,
)
fun computeInsights(input: InsightInput): Insights
```

Body Δ follows §4.2 exactly (`lastBodyValue` uses entries with non-null field). Note `Metric.delta` handles "current defined, previous undefined ⇒ Δ null".

---

## 5. DI — manual container

```kotlin
// di/AppContainer.kt
class AppContainer(context: Context, val time: TimeSource = SystemTimeSource()) {
    val appContext: Context = context.applicationContext
    val db: LifeHubDatabase = LifeHubDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val goals: GoalRepository = GoalRepositoryImpl(db)
    val plans: PlanRepository = PlanRepositoryImpl(db)
    val exercises: ExerciseRepository = ExerciseRepositoryImpl(db)
    val sessions: SessionRepository = SessionRepositoryImpl(db)
    val macros: MacroRepository = MacroRepositoryImpl(db)
    val body: BodyRepository = BodyRepositoryImpl(db)
    val backup: BackupRepository = BackupRepositoryImpl(db, settings, exercises)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

// LifeHubApp.kt
class LifeHubApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.ensure(this)
        NotificationScheduler.schedule(this, container.settings.current().dailyCheckTime, replace = false)
    }
}
val Context.container: AppContainer get() = (applicationContext as LifeHubApp).container
```

Compose access: `val container = LocalContext.current.container` inside `XxxRoute` only. `XxxScreen` never touches the container.

### ViewModel pattern

```kotlin
class GoalDetailViewModel(
    private val goals: GoalRepository, private val plans: PlanRepository,
    private val sessions: SessionRepository, private val time: TimeSource,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val goalId: String = checkNotNull(savedState["goalId"])
    val state: StateFlow<GoalDetailUiState> = combine(...) { ... }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailUiState.Loading)
    fun markAchieved() = viewModelScope.launch { goals.setState(goalId, GoalState.ACHIEVED, time.today()) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { GoalDetailViewModel(c.goals, c.plans, c.sessions, c.time, createSavedStateHandle()) }
        }
    }
}

@Composable
fun GoalDetailRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: GoalDetailViewModel = viewModel(factory = GoalDetailViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    GoalDetailScreen(state, onBack = onBack, onOpenPlan = { onNavigate(Routes.plan(it)) }, ...)
}
```

- `UiState` is a data class (or sealed `Loading`/`Loaded`) defined in the ViewModel file.
- Form screens hold editable fields in the ViewModel as `MutableStateFlow<FormState>`; validation is a pure function `fun FormState.errors(): Map<Field, String>` — unit-testable.
- One-shot events (navigate back after save, snackbar) via `Channel<Event>(Channel.BUFFERED)` exposed as `Flow`, collected in Route with `LaunchedEffect(Unit)`.
- Tests: no coroutines-test available → test pure functions and repositories (`runBlocking` + in-memory Room under Robolectric). Don't unit-test ViewModels.

---

## 6. Navigation

### 6.1 Routes (`navigation/Routes.kt`)

String routes; all optional args are query params with defaults. Dates passed as epochDay `Long`.

| Route pattern | Builder | Owner | Notes |
|---|---|---|---|
| `home?period={period}&offset={offset}` | `Routes.home(period: HomePeriod? = null, offset: Int = 0)` | WP4 | tab. `period` String nullable; if non-null VM writes it to settings |
| `calendar` | `Routes.CALENDAR` | WP3 | tab |
| `goals` | `Routes.GOALS` | WP1 | tab |
| `goal/{goalId}` | `Routes.goal(id)` | WP1 | |
| `goal/edit?goalId={goalId}` | `Routes.goalEdit(id: String? = null)` | WP1 | null = new |
| `plans` | `Routes.PLANS` | WP1 | tab ("Train") |
| `plan/{planId}` | `Routes.plan(id)` | WP1 | detail |
| `plan/edit?planId={planId}&goalId={goalId}` | `Routes.planEdit(planId: String? = null, goalId: String? = null)` | WP1 | goalId pre-links new plan |
| `exercises` | `Routes.EXERCISES` | WP1 | library management |
| `exercise/edit?exerciseId={exerciseId}` | `Routes.exerciseEdit(id: String? = null)` | WP1 | |
| `workout/start?planId={planId}` | `Routes.workoutStart(planId: String? = null)` | WP2 | plan picker; planId = preselect |
| `workout/live` | `Routes.WORKOUT_LIVE` | WP2 | the IN_PROGRESS session |
| `workout/log?date={date}` | `Routes.workoutLog(date: LocalDate)` | WP2 | manual log F-WO-8 |
| `session/{sessionId}` | `Routes.session(id)` | WP2 | view/edit F-WO-13 |
| `macros/edit?date={date}` | `Routes.macroEdit(date: LocalDate)` | WP3 | |
| `body` | `Routes.BODY` | WP3 | history F-BODY-3 |
| `body/edit?date={date}` | `Routes.bodyEdit(date: LocalDate)` | WP3 | |
| `settings` | `Routes.SETTINGS` | WP5 | |

Tab roots: `HOME_ROOT = "home?period={period}&offset={offset}"`, `calendar`, `goals`, `plans`. Bottom-bar navigation: `navigate(route) { popUpTo(graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }`.

### 6.2 Route composable signatures (fixed by WP0, implemented by owner WP)

Every route composable has exactly this shape (args are read from `SavedStateHandle` in the VM, not passed):

```kotlin
@Composable fun HomeRoute(onNavigate: (String) -> Unit)
@Composable fun CalendarRoute(onNavigate: (String) -> Unit)
@Composable fun GoalsRoute(onNavigate: (String) -> Unit)
@Composable fun GoalDetailRoute(onNavigate: (String) -> Unit, onBack: () -> Unit)
@Composable fun GoalEditRoute(onBack: () -> Unit)
@Composable fun PlansRoute(onNavigate: (String) -> Unit)
@Composable fun PlanDetailRoute(onNavigate: (String) -> Unit, onBack: () -> Unit)
@Composable fun PlanEditRoute(onBack: () -> Unit)
@Composable fun ExercisesRoute(onNavigate: (String) -> Unit, onBack: () -> Unit)
@Composable fun ExerciseEditRoute(onBack: () -> Unit)
@Composable fun WorkoutStartRoute(onNavigate: (String) -> Unit, onBack: () -> Unit)
@Composable fun LiveWorkoutRoute(onNavigate: (String) -> Unit, onBack: () -> Unit)
@Composable fun WorkoutLogRoute(onBack: () -> Unit)
@Composable fun SessionRoute(onBack: () -> Unit)
@Composable fun MacroEditRoute(onBack: () -> Unit)
@Composable fun BodyListRoute(onNavigate: (String) -> Unit, onBack: () -> Unit)
@Composable fun BodyEditRoute(onBack: () -> Unit)
@Composable fun SettingsRoute(onBack: () -> Unit)
```

File = `feature/<pkg>/<Name>Screen.kt` containing `<Name>Route` + `<Name>Screen`; VM in `<Name>ViewModel.kt`. WP0 creates each Route as a placeholder (`LhTopBar(title) + LhEmptyState("Coming soon")`). Owner WP rewrites the file.

Navigating "replace" semantics: after `workout/start` starts a session → `onNavigate(Routes.WORKOUT_LIVE)` with the start screen popped. Because Routes only get `onNavigate(String)`, use a prefix convention handled in `LhNavHost`: `onNavigate("replace:" + route)` = navigate with `popUpTo(currentRoute) { inclusive = true }`. `onNavigate("back")` = popBackStack. Keep these two only.

### 6.3 Shell (`navigation/AppScaffold.kt`, `LhNavHost.kt`)

- `Scaffold(bottomBar = { if (isTabRoute) Column { ActiveWorkoutBar(); LhBottomBar() } }, snackbarHost = …)`.
- `ActiveWorkoutBar`: collects `container.sessions.observeInProgress()`; when non-null shows "Workout in progress · <plan> · mm:ss  [Resume]" → `Routes.WORKOUT_LIVE`. Hidden on non-tab routes.
- A global `LocalSnackbarHostState` CompositionLocal (WP0) for undo snackbars.

### 6.4 Deep links (`navigation/DeepLinks.kt`)

Notifications don't use URIs; they put an explicit route in an intent extra.

```kotlin
object DeepLinks {
    const val EXTRA_ROUTE = "com.lifehub.app.ROUTE"
    fun intent(context: Context, route: String): Intent =
        Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    fun pendingIntent(context: Context, requestCode: Int, route: String): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent(context, route), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
```

`MainActivity`: holds `private val pendingRoute = MutableStateFlow<String?>(null)`; sets it from `intent` in `onCreate` (only if `savedInstanceState == null`) and in `onNewIntent`. `LhNavHost` collects it: `LaunchedEffect(route) { route?.let { navController.navigate(it); pendingRoute.value = null } }`.

For `home…` routes navigate with `popUpTo(graph.id) { inclusive = true }` so a fresh Home entry reads the new args (singleTop would reuse the old SavedStateHandle).

Spec mapping: `lifehub://workout/start` → `Routes.workoutStart(planId?)` (WorkoutStart screen itself redirects to `workout/live` with replace if a session is IN_PROGRESS); `lifehub://home?period=1W&offset=1` → `Routes.home(HomePeriod.ONE_WEEK, 1)`; `lifehub://body/new?date=today` → `Routes.bodyEdit(today)`.

---

## 7. In-progress workout persistence (F-WO-1, -2, -7, -9, -11)

- Exactly one `WorkoutSession(status = IN_PROGRESS)`; `startFromPlan` checks `getInProgress()` inside the transaction.
- Every user action writes immediately: set completion → `upsertSet` + `updateSession(currentSetIndex, restEndsAt)`. No in-memory-only state except text field drafts (written on Done or on field blur).
- `currentSetIndex` = index into `sets` sorted by `(exerciseOrder, setIndex)`. After Done/Skip it moves to the next PENDING set (search forward, then wrap from 0); if none, stays and UI shows "All sets done — Finish".
- Session ops are pure functions in `feature/workout/WorkoutOps.kt` (WP2) operating on `List<SessionSet>` and returning the rows to upsert/delete — unit-tested:
  ```kotlin
  object WorkoutOps {
      fun ordered(sets: List<SessionSet>): List<SessionSet>
      fun nextPendingIndex(sets: List<SessionSet>, from: Int): Int?
      fun complete(set: SessionSet, reps: Int?, seconds: Int?, weightKg: Double?, now: Instant): SessionSet   // validates invariant §3.7
      fun skip(set: SessionSet): SessionSet
      fun addSet(sets: List<SessionSet>, exerciseOrder: Int): SessionSet          // copies last set of that exercise, PENDING, actuals null
      fun removeSet(sets: List<SessionSet>, setId: String): List<SessionSet>     // returns renumbered remaining sets of that exercise
      fun addExercise(sessionId: String, sets: List<SessionSet>, ex: Exercise, count: Int, type: TargetType, value: Int?, weightKg: Double?, restSeconds: Int = 90): List<SessionSet>  // exerciseOrder = max+1
      fun swapExercise(sets: List<SessionSet>, exerciseOrder: Int, ex: Exercise): List<SessionSet>        // only PENDING rows
      fun applyWeightToRemaining(sets: List<SessionSet>, fromSetId: String, weightKg: Double?): List<SessionSet>  // PENDING rows of same exercise after it
  }
  ```
- Rest timer: on Done (not on the last remaining pending set) → `restEndsAt = now + set.restSeconds`; UI ticks from `restEndsAt − now` (recomputed, never decremented) so process death is harmless. ±15 s and Skip update `restEndsAt` (Skip ⇒ null). Clamp: never below now.
- `RestTimerService` (WP2, `feature/workout/RestTimerService.kt`): foreground service so the alert fires with screen off/app backgrounded.
  - Manifest (written by WP0): `<service android:name=".feature.workout.RestTimerService" android:exported="false" android:foregroundServiceType="specialUse"><property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Workout rest timer"/></service>`; permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `VIBRATE`.
  - API: `RestTimerService.start(context, endAtMillis: Long, exerciseName: String)` / `RestTimerService.stop(context)`. Started only from UI actions (app in foreground — allowed on 12+).
  - Shows ongoing notification on channel `rest_timer` (IMPORTANCE_LOW) with `setUsesChronometer(true)`, `setChronometerCountDown(true)`, `setWhen(endAt)`, action "Skip". At endAt (Handler.postDelayed): vibrate 2×200 ms, post "Rest over — next: <exercise>" on channel `rest_done` (IMPORTANCE_HIGH, default sound, auto-cancel, content intent → `workout/live`), `stopSelf()`. Skip action: `sessions.updateSession(restEndsAt = null)` then stop.
  - Only the service vibrates/sounds at rest end (UI does not, avoids double alert). If POST_NOTIFICATIONS is denied the FGS still runs (notification hidden) — fine.
- Isometric stopwatch (SECONDS sets): UI-only state (`startedAtMillis` in VM `SavedStateHandle`); at target seconds: vibrate 300 ms + `ToneGenerator(STREAM_NOTIFICATION, 80).startTone(TONE_PROP_BEEP, 150)`. On Stop → actual seconds = elapsed, user taps Done to commit.

---

## 8. Notifications (WP5)

- Channels (`notifications/Channels.kt`, WP0): `reminders_train`, `reminders_protein`, `reminders_weight` (IMPORTANCE_DEFAULT), `rest_timer` (LOW), `rest_done` (HIGH). `Channels.ensure(context)` is idempotent, API 26+.
- `NotificationScheduler.schedule(context, time: LocalTime, replace: Boolean)`: `PeriodicWorkRequestBuilder<DailyCheckWorker>(24, HOURS, 15, MINUTES).setInitialDelay(delayUntilNext(time))`; unique name `"daily-check"`; policy `KEEP` on app start, `CANCEL_AND_REENQUEUE` when time changes. WorkManager persists across reboot itself — no BOOT_COMPLETED receiver. `fun delayUntilNext(now: ZonedDateTime, time: LocalTime): Duration` is pure (tested).
- `DailyCheckWorker : CoroutineWorker`: gets `applicationContext.container`; loads data (`plans.observePlans().first()`, `sessions.observeCompleted().first()`, `sessionDao.getInProgress()`, macros of last 14 days, `body.latestDate()`, earliest data date), calls `NotificationRules.evaluate(...)`, posts via `Notifier`, writes `setLastNotified`. Returns `Result.success()` always.
- `NotificationRules` (pure, `notifications/NotificationRules.kt`):
  ```kotlin
  data class RuleInput(
      val today: LocalDate, val settings: Settings, val lastNotified: Map<NotifType, LocalDate?>,
      val plans: List<Plan>, val completed: List<WorkoutSession>, val hasInProgressToday: Boolean,
      val macros: List<MacroDay>,                 // at least today-14..today-1
      val latestBodyDate: LocalDate?, val earliestDataDate: LocalDate?,   // earliest session/macro date
  )
  data class PendingNotification(val type: NotifType, val title: String, val text: String, val route: String)
  fun evaluate(input: RuleInput): List<PendingNotification>   // toggles + once-per-day cap + §5 rules
  ```
  Decisions on spec gaps: N-TRAIN(a) with several matching plans names the first by name and passes no planId; with exactly one it passes `planId`. (a) takes precedence over (b). N-WEIGHT with no BodyEntry ever: `daysSince = today − earliestDataDate` and text "You haven't logged your weight yet — weigh yourself."; no data at all ⇒ never fires. Title for all: "Life Hub".
- `Notifier.post(context, n)`: skip if API ≥ 33 and permission not granted; `NotificationCompat` with `R.drawable.ic_launcher` small icon (monochrome vector), `setAutoCancel(true)`, content intent `DeepLinks.pendingIntent(context, type.ordinal, n.route)`. Notification id = `100 + type.ordinal`.
- POST_NOTIFICATIONS: requested from Settings (WP5) via `rememberLauncherForActivityResult(RequestPermission())` when a toggle is switched on or on first Settings open (`settings.notificationPermissionAsked`). Also requested once when the user starts the first workout (WP2) so rest alerts can show. Denied ⇒ toggles show hint "Notifications are blocked in system settings" + "Open settings" (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).

---

## 9. Export / import (WP5)

- `export/ExportModels.kt`: `@Serializable` DTOs matching spec §6.1 key order exactly (`ExportV1(schemaVersion, exportedAt, app, readme, units, settings, goals, plans, exercises, sessions, macros, bodyEntries)`); nested `planLinks`, plan `exercises`, session `sets` (+ `restSeconds`), body `fatPct`. Dates as ISO strings, timestamps as epoch millis `Long`, `dailyCheckTime` as `"HH:mm"`.
- `JsonCodec`: `val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true; coerceInputValues = true }`. `fun encode(s: DataSnapshot, exportedAt: ZonedDateTime, appVersion: String): String`, `fun decode(text: String): DataSnapshot` (throws `ImportException(message)` on bad `schemaVersion != 1` or malformed). Optional DTO fields have defaults so missing fields import. Round-trip test: `decode(encode(s)) == s` on the mock snapshot (F-EXP-3).
- `CsvExporter.writeZip(s: DataSnapshot, exportedAt: ZonedDateTime, out: OutputStream)`: files/columns exactly spec §6.2 via `ZipOutputStream`; RFC-4180 quoting helper `csv(vararg cells: Any?)`; timestamps `Instant.atZone(zone).toOffsetDateTime().toString()`.
- `ExportFiles`:
  - `fun writeToCache(context, name: String, write: (OutputStream) -> Unit): File` → `cacheDir/exports/<name>` (dir matches `res/xml/file_paths.xml`; delete older files in that dir first).
  - `fun shareIntent(context, file: File, mime: String): Intent` → `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`, `ACTION_SEND`, `FLAG_GRANT_READ_URI_PERMISSION`, wrapped in `Intent.createChooser`.
  - Save to…: `rememberLauncherForActivityResult(CreateDocument("application/json"|"application/zip"))` → `contentResolver.openOutputStream(uri)` → write bytes.
  - Names: `lifehub-export-YYYYMMDD-HHmm.json|.zip`.
- Import: `OpenDocument()` with `arrayOf("application/json", "text/plain", "application/octet-stream")` → read text on `Dispatchers.IO` → `decode` → show counts dialog "Replace all data?" → `backup.replaceAll(snapshot)` (single transaction, all-or-nothing) → reschedule worker with imported time.

## 10. Mock data (WP5)

`object MockDataGenerator { fun generate(today: LocalDate, zone: ZoneId): DataSnapshot }` — pure, `kotlin.random.Random(42)`, follows spec §7 exactly, defines its own sample exercises (`MockDataGenerator.exercises`), `Settings()` defaults. Applied via `backup.replaceAll`. Test: run `computeInsights` for 1W/2W, k=0 and k=1 → every `Metric` has non-null current & delta (spec §7 expected check).

---

## 11. Manifest (WP0 writes the final version)

Permissions: `POST_NOTIFICATIONS`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`. No INTERNET. Components: `MainActivity` (existing), `FileProvider` (existing), `RestTimerService` (§7). WP0 creates a compiling stub `RestTimerService` (`Service` with empty `onBind`, `start`/`stop` companion no-ops) and stub `NotificationScheduler.schedule()` (no-op) — owners replace bodies, keep signatures.

---

## 12. Work packages

Rules for all WPs: touch only files you own (list below) + your `strings_<wp>.xml` + your tests. If you need a change in a WP0 file (DAO query, component param), add the **minimum additive** change (new function/param with default) and note it in your report — never change existing signatures. Every WP finishes with `./gradlew --offline :app:assembleDebug :app:testDebugUnitTest` green and adds ≥ 1 Roborazzi screenshot test per screen in `app/src/test/java/com/lifehub/app/screenshots/<Wp>ScreenshotTest.kt` (light + dark, stateless `XxxScreen` with fake state, output `build/screens/<name>.png`).

### WP0 — Foundation (sequential, first)
Files: everything marked [WP0] in §1, all placeholder `feature/*/*Screen.kt` Routes, `AndroidManifest.xml`, `res/values/strings.xml`, `res/values/themes.xml` (keep), stub `feature/workout/RestTimerService.kt`, stub `notifications/NotificationScheduler.kt`.
Tests: `domain/PeriodTest`, `domain/GoalProgressTest`, `domain/NutritionTest`, `data/repo/RepositoriesTest` (Robolectric, in-memory DB: fresh DB has no exercises; FK cascade goal→links; plan delete sets session planId null; exercise delete InUse; startFromPlan creates N sets and rejects second; replaceAll/clearAll), `screenshots/DesignSystemScreenshotTest` (catalog of all components light/dark).
Spec: P-2..P-5, §3, §4.1, §4.6, §4.7, F-NUT-2 (calc), F-BODY-2 (calc), F-EX-1 (seed), F-GOAL-8, F-NUT-4.
Acceptance: app launches to Home placeholder; 4-tab bottom bar navigates with state restore; every route in §6.1 reachable from a debug-free code path without crash; dark mode follows system; deep-link extra navigates.

### WP1 — Goals, Plans, Exercise library
Files: `feature/goals/*`, `feature/plans/*` (Goals, GoalDetail, GoalEdit, Plans, PlanDetail, PlanEdit, Exercises, ExerciseEdit + VMs), `res/values/strings_goals.xml`, `strings_plans.xml`.
Spec: F-GOAL-1..7, F-PLAN-1..5, F-EX-1 (hide), F-EX-2, F-EX-3 (uses `ExercisePickerSheet`).
Acceptance: goal form deadline auto-recompute until manually edited (pure `GoalForm` reducer test); deadline ≤ start blocked; states/tabs work; delete goal keeps plans; plan editor reorder (up/down buttons), REPS/MAX/SECONDS validation (pure validator test), links with sessionsNeeded editable from plan and goal detail; duplicate; archive hides from start list; custom exercise duplicate name error; delete in-use exercise shows message.

### WP2 — Live workout & session logging
Files: `feature/workout/*` (WorkoutStart, LiveWorkout, SetRow, RestTimerBar, FinishSheet, AddExerciseSheet, WorkoutLog, Session screens + VMs, `WorkoutOps.kt`, `RestTimerService.kt`), `strings_workout.xml`.
Spec: F-WO-1..13.
Acceptance: `WorkoutOpsTest` covers every op + invariant; start/resume/discard dialog; REPS one-tap Done prefilled; MAX Done disabled until input; SECONDS stopwatch beeps at target; weight per set + apply-to-remaining; add/skip/remove set, add/swap exercise never touch plan; rest timer survives process death (restEndsAt) and alerts via service when backgrounded; Finish → COMPLETED with duration, pending→SKIPPED; manual log saves `isManual=true`, date ≤ today; session detail edit/delete.

### WP3 — Calendar, Nutrition, Body
Files: `feature/calendar/*`, `feature/nutrition/*`, `feature/body/*`, `strings_calendar.xml`, `strings_nutrition.xml`, `strings_body.xml`.
Spec: F-CAL-1..4, F-NUT-1..3 (form), F-BODY-1..3.
Acceptance: Monday-first grid, ±month, Today button; 3 markers + legend; day sheet content per F-CAL-3 and buttons per F-CAL-4; macro form live kcal hint, at-least-one-macro validation, upsert existing date; body form ranges (pure validator tests), fat % displayed, list with delete confirmation.

### WP4 — Home dashboard, insights, charts
Files: `domain/Insights.kt`, `core/ui/charts/*`, `feature/home/*`, `strings_home.xml`.
Spec: F-HOME-1..7, §4.2–4.5.
Acceptance: `InsightsTest` covers each formula incl. "—" edge cases (§4.8) and 2W period; period nav forward disabled at k=0; header range format; charts render with 0 points ("No data"); protein target dashed line; quick actions (Start/Resume, Log macros, Log weight) visible without scrolling; goals overview uses `GoalCard`.

### WP5 — Settings, Export/Import, Mock data, Notifications
Files: `feature/settings/*`, `export/*`, `mock/*`, `notifications/{NotificationScheduler,DailyCheckWorker,NotificationRules,Notifier}.kt`, `strings_settings.xml`.
Spec: F-SET-1..7, F-EXP-1..3, F-MOCK-1, F-NOTIF-1..5, §5, §6, §7.
Acceptance: `NotificationRulesTest` (each rule, toggles, cap, Monday-only protein, weight 7/14/21 days), `DelayUntilNextTest`, `JsonCodecTest` round-trip + schemaVersion rejection, `CsvExporterTest` (headers, quoting), `MockDataTest` (counts within spec ranges, determinism, insights non-null); share sheet + Save to… work; import replace-all confirmation; clear-all requires typing DELETE and re-seeds 42 exercises.

Parallelism: WP0 first; then WP1–WP5 fully in parallel (no shared files). Integration order for merge conflicts (should be none): WP1, WP3, WP2, WP4, WP5.
