# Fitness Copilot — Phase 0: repo map, gaps, migrations, plan

Date: 2026-11-16 · Input: `Fitness Copilot design doc e prompt per l'agent.md` (repo root, "the doc") · Companion: `docs/copilot/phase0-research.md` (external APIs: CREA, Open Food Facts fields, key encryption, LLM/STT providers, foreground-service rules — written in parallel, referenced below as **[R]**).

No code was changed in phase 0. Paths below are relative to `app/src/main/java/com/lifehub/app/` unless they start with `app/` or `docs/`.

---

## 1. Stack and conventions

| Topic | What the repo does |
|---|---|
| Language | Kotlin 2.1.0, JVM 17. Single module `:app`, package `com.lifehub.app`. |
| UI | Jetpack Compose (BOM 2024.12.01, Material3 used only as a base), custom shadcn-like design system in `core/designsystem` (`LhTheme`, `LhType`, `Lh*` components, see `docs/ux-v2-ds-notes.md`, `docs/08-v3-design.md`). UI text in English, strings in `res/values/strings_*.xml` (many screens still inline literals). |
| Architecture | Feature packages `feature/<name>/` with `XxxRoute` (wires container + nav) → stateless `XxxScreen(state, callbacks)`; `XxxViewModel` exposing `StateFlow` built with `combine/stateIn`. Pure logic in `domain/` (unit-tested, no Android). Data: `data/model` (entities), `data/db` (Room DB + DAOs), `data/repo` (interface + `Impl`), `data/prefs`, `data/off`. |
| DI | Manual service locator: `di/AppContainer.kt`, reached via `context.container` (`LifeHubApp.kt`). ViewModels: `companion fun factory(c: AppContainer) = viewModelFactory { initializer { ... createSavedStateHandle() } }`. No Hilt/Koin. |
| Navigation | Navigation-Compose 2.8.5 with string routes: patterns + builders in `navigation/Routes.kt`, graph in `navigation/LhNavHost.kt`, shell/bottom bar/centre "+" FAB in `navigation/AppScaffold.kt`, add sheet `navigation/AddActionSheet.kt`. Notification deep links: `navigation/DeepLinks.kt` (`EXTRA_ROUTE` extra on `MainActivity`, singleTop). Date args = epochDay `Long` (-1 default). |
| DB | Room 2.6.1 (KSP), `data/db/LifeHubDatabase.kt`, file `lifehub.db`, **version 7**, `exportSchema = true` → `app/schemas/com.lifehub.app.data.db.LifeHubDatabase/1..7.json`. Converters: `LocalDate`→epochDay, `Instant`→epoch millis, `Set<Int>` weekdays → "1,3,5"; enums stored by name (TEXT). IDs are UUID strings (except `body_entries` PK = date). |
| Migrations | Hand-written `val MIGRATION_n_m = object : Migration(n, m)` in the DB companion, raw SQL copied from the schema JSON, registered in `build()`. No destructive fallback. **Tests**: `app/src/test/.../data/db/Migration5To6Test.kt`, `Migration6To7Test.kt` (Robolectric): build a real vN DB from `schemas/.../N.json` with `FrameworkSQLiteOpenHelperFactory`, seed with SQL, open with Room + the migration (Room validates the new schema), assert data. `room-testing`'s `MigrationTestHelper` is not used. |
| Settings storage | `data/prefs/SettingsRepository.kt`: plain `SharedPreferences("lifehub_settings")` + `Settings` data class in a `StateFlow`. **No DataStore** (forbidden by `docs/02-architecture.md`). |
| Networking | Hand-written `HttpURLConnection` (`data/off/OpenFoodFacts.kt`, `OpenFoodFactsClient`), on `Dispatchers.IO`, sealed `OffResult` (Ok/Error), pure `OffMapper` unit-tested with JSON fixtures. No OkHttp/Retrofit/Ktor. |
| Serialization | kotlinx.serialization 1.7.3 (`Json { ignoreUnknownKeys }`, `JsonObject` tree parsing for OFF, `@Serializable` DTOs in `export/ExportModels.kt`). |
| Background | WorkManager 2.10.0: periodic `DailyCheckWorker`/`StreakCheckWorker` (`notifications/`), scheduled in `NotificationScheduler`. AlarmManager exact alarm for rest timer (`notifications/RestAlarm.kt`). No foreground services (manifest comment: "Lead override: no FOREGROUND_SERVICE permissions"). |
| Notifications | `notifications/Channels.kt` (7 channels), `Notifier.post()` (one id per `NotifType` ordinal, content intent only, no actions), rules in `NotificationRules.kt` (pure, tested). `POST_NOTIFICATIONS` declared; `settings.notificationPermissionAsked` flag exists. |
| Time | `core/time/TimeSource.kt` (`SystemTimeSource`, `FixedTimeSource` for tests, `todayFlow()`). |
| Barcode | CameraX 1.4.1 + ML Kit barcode-scanning 17.3.0 (`feature/food/ScanScreen.kt`). Images: Coil 2.7.0. |
| SDK | minSdk 26, target/compile 35. ABI splits (arm64 APK for the phone). Release = no minify, debug-signed. |
| Tests | JUnit4 + Robolectric 4.14.1 (`sdk=35`) for repositories/DB (in-memory Room, `runBlocking`, **no** coroutines-test), pure domain tests, Roborazzi 1.32.2 screenshots (`@GraphicsMode(NATIVE)`, `captureRoboImage("build/screens/<name>.png")`, light + dark), e2e click tour `e2e/AppTourTest.kt`. 60 test classes, 336 tests. |
| Logging | No `android.util.Log` calls anywhere (good for "no keys in release logs"). |
| Dependencies policy | `docs/02-architecture.md`: "do not add libraries ... (offline build)". Network is reachable from WSL now, but the habitual build is `--offline`, so any new dependency needs one online Gradle sync first. |

**Build commands verified in this environment (WSL2, 2026-11-16):**

- `./gradlew :app:testDebugUnitTest --offline -q` → **BUILD OK, 336 tests, 0 failures, 49 s** (warm Gradle caches; compile already up to date for most of it).
- Screenshots (from project notes, not re-run): `./gradlew assembleDebug :app:recordRoborazziDebug --offline -q` → PNGs in `app/build/screens/`.
- Install on phone: `./install.sh` (Windows `adb.exe`, user runs it).
- No emulator (no /dev/kvm): on-device behaviour (side key, lock screen, foreground service) can only be checked by the owner on the Samsung A55.

---

## 2. Data model map (doc §2.1)

### 2.1 Foods — table `foods` (`data/model/Entities.kt: Food`)
Fields: `id` (UUID), `source: FoodSource` (`CUSTOM | OFF`), `barcode?` (indexed), `name`, `brand?`, `kcal100`, `protein100`, `carbs100`, `fat100` (all non-null Double, per 100 g), `servingSizeG?` (single serving, from OFF `serving_size` or the custom form), `favorite: Boolean`, `lastUsedAt?` (indexed, = "recent"), `imageUrl?`.
- DAO `data/db/dao/FoodDao.kt` (`FoodDao`: recent, mine = favourite or custom, by id, by barcode, upsert, delete).
- Repo `data/repo/FoodRepository.kt` (`saveFood` dedupes OFF products by barcode, `setFavorite`).
- Math: `domain/DayNutrition.kt` (`Food.macrosFor(grams)`, `Totals`), `feature/food/FoodMath.kt` (`gramChips`, `parseGrams`, `filterFoods` = every word contained in name+brand, `CustomFoodForm`).
- Screens: `feature/food/AddFoodScreen.kt` + `AddFoodViewModel.kt` (tabs recent / mine / OFF search), `FoodDetailScreen.kt` + `FoodDetailViewModel.kt` (grams + chips, favourite star), `CustomFoodScreen.kt` (create/edit), `ScanScreen.kt`.
- Note: missing OFF macros are stored as **0**, not null (`OffMapper.toFood`), unlike quick adds which support "n/a".

### 2.2 Meal logs — table `food_entries` (`FoodEntry`) + `quick_add_entries` (`QuickAddEntry`)
`FoodEntry`: `id`, `date: LocalDate` (indexed), `foodId?` (no FK), `name`, `brand?`, `grams`, `kcal`, `proteinG`, `carbsG`, `fatG`, `createdAt: Instant`, `imageUrl?`. Name/brand/grams/kcal/macros are **snapshots** taken at log time. No meal type (single per-day list, owner decision), no time-of-day, no source, no capture link.
`QuickAddEntry` (v7): `id`, `date`, `name?`, `kcal`, `proteinG?/carbsG?/fatG?` (null = unknown), `kcalIsAuto`, `createdAt`.
- DAOs: `FoodEntryDao` (in `FoodDao.kt`), `QuickAddDao.kt`.
- Repos: `FoodRepository.addEntry(food, date, grams, now)` (caches food, bumps `lastUsedAt`, inserts entry — **not** in a transaction, single item), `updateEntryGrams`, `restoreEntry`, `deleteEntry`, `observeNutrition(from,to)` = merge of entries + quick adds (`domain/DayNutrition.kt: mergeNutrition`). `QuickAddRepository.kt`.
- Screens: `feature/calendar/CalendarScreen.kt` (Diary tab: day list of food + quick adds), `feature/home/HomeScreen.kt` ("Food today", kcal ring, macro cards), `feature/nutrition/MacroEditScreen.kt` (quick add form), `feature/trends/*`.

### 2.3 Nutrition targets — `SharedPreferences`, not DB
`Settings.proteinTargetG` (Int, default 150, always set), `kcalTarget?`, `carbsTargetG?`, `fatTargetG?` (`data/prefs/SettingsRepository.kt`). Screen `feature/settings/TargetsScreen.kt` (route `settings/targets`, from the You hub). Exported in JSON `settings`.

### 2.4 Goals — tables `goals`, `goal_plan_links`
`Goal`: `id`, `name`, `notes?`, `startDate`, `deadline`, `deadlineIsAuto`, `state: ACTIVE|ACHIEVED|ARCHIVED`, `achievedAt?`, `createdAt`. Goals are free text, no numeric target/metric. `GoalPlanLink(goalId, planId, sessionsNeeded?)` (FK cascade both sides).
- DAO `GoalDao.kt`, repo `GoalRepository.kt` (`setState` also writes/removes the GOAL achievement), domain `GoalProgress.kt`.
- Screens: `feature/goals/GoalsScreen.kt`, `GoalDetailScreen.kt`, `GoalEditScreen.kt`, Home goals card (`core/ui/GoalCard.kt`).

### 2.5 Workout plans — tables `plans`, `plan_exercises`, `exercises`
`Plan`: `id`, `name`, `weekdays: Set<Int>` (ISO 1..7), `notes?`, `isArchived`, `createdAt`, `dayType?` (PULL/PUSH/LEGS/CORE), `iconKey?`. A plan is a **single-session template repeated on its weekdays**, open-ended; there is no multi-week programme, no start/end date, and several plans can be active at once (all non-archived ones).
`PlanExercise`: `id`, `planId` (FK cascade), `exerciseId` (FK restrict), `orderIndex`, `sets` (1..20), `targetType: REPS|MAX|SECONDS`, `targetValue?`, `weightKg?`, `restSeconds`.
`Exercise`: `id`, `name` (unique, NOCASE), `category: PULL|PUSH|CORE|LEGS`, `defaultTargetType`, `isCustom`, `isHidden`. No built-in catalogue (owner decision): the user types their own.
- DAOs `PlanDao.kt`, `ExerciseDao.kt`; repos `PlanRepository.kt` (`save` in a transaction, `duplicate`, `setArchived`, `raiseTargets` = auto progression via `domain/Progression.kt`), `ExerciseRepository.kt`.
- Screens: `feature/plans/PlansScreen.kt` (Train tab), `PlanDetailScreen.kt`, `PlanEditScreen.kt`, `PlanSessionsCalendar.kt`, `ExercisesScreen.kt`, `ExerciseEditScreen.kt`; `feature/workout/WorkoutStartScreen.kt` (plan picker).

### 2.6 Planned workouts — **no table**, derived
"Planned" = a non-archived plan whose `weekdays` contains the date's ISO weekday. Implemented twice: `feature/home/HomeViewModel.kt: plansOn(date, plans, completed)` (internal, with done flag) and `domain/Streak.kt: Streak.planned(d)` (also requires plan `createdAt` ≤ date). Also used by `NotificationRules` (train/streak reminders), `feature/calendar/DiaryFlags.kt`. Done workouts: tables `sessions` (`WorkoutSession`: planId?, planName snapshot, date, status IN_PROGRESS|COMPLETED, rpe, …) and `session_sets` (`SessionDao.kt`, `SessionRepository.kt`).

### 2.7 Body weight — table `body_entries`
`BodyEntry`: **PK = `date`** (one row per day), `weightKg`, `muscleKg?`, `fatKg?`, `updatedAt`. DAO `BodyDao.kt` (upsert by date), repo `BodyRepository.kt`. Screens `feature/body/BodyListScreen.kt`, `BodyEditScreen.kt` (+ `BodyForm.kt`), Home weight chip, Trends.

### 2.8 Barcode scanner
`feature/food/ScanScreen.kt`: `ScanRoute(date, onNavigate, onBack)` handles CAMERA permission, CameraX preview + ML Kit analyzer, then: local `food.getByBarcode` → else `off.byBarcode` → `food.saveFood` (cache) → **navigates** to `foodDetail` (replace). Not-found → "create custom" with barcode. The lookup logic is inline in the composable and the screen has no "return result to caller" mode.

### 2.9 Open Food Facts client
`data/off/OpenFoodFacts.kt`: `OffApi` interface (`search(query)`, `byBarcode(code)`), `OpenFoodFactsClient` (base `https://world.openfoodfacts.org`, `cgi/search.pl` search, `api/v2/product/{code}.json`, 8/10 s timeouts, UA "LifeHub/0.3"), `OffMapper` (fields `code,product_name,brands,nutriments,serving_size,image_*`; parses `serving_size` grams). **Package weight (`product_quantity`/`quantity`) is not requested.** Instance in `AppContainer.off`. Test `app/src/test/.../data/off/OpenFoodFactsTest.kt`. Field/endpoint verification: **[R]**.

### 2.10 Other relevant infrastructure
- Backup/export: `data/repo/BackupRepository.kt` (`snapshot`/`replaceAll`/`clearAll` table by table), `export/JsonCodec.kt` + `ExportModels.kt` (JSON export schema, explicit `SettingsDto` field list), `export/CsvExporter.kt`, `feature/settings/DataScreen.kt`. Files via `FileProvider` (`res/xml/file_paths.xml`, cache `exports/`).
- Android auto-backup: `android:allowBackup="true"`, **no** `dataExtractionRules`/`fullBackupContent` → today the DB and all prefs go to Google backup.
- Settings hub: `feature/you/YouScreen.kt` (route `you`, avatar in Home header) → Targets, Notifications (`feature/settings/NotificationsScreen.kt`), Data, About. `feature/settings/SettingsScreen.kt` (route `settings`) still exists (older all-in-one screen).
- Undo pattern in UI: snackbar with action (`core/designsystem/SnackbarBus.kt`, `FormUndoTest`).

---

## 3. Gap analysis

### 3.1 Components (doc §4)

| Component | Exists | Missing / to adapt |
|---|---|---|
| `Transcriber` | Nothing (no audio recording code; `core/audio/SoftChime.kt` is playback only). | Interface + cloud impl (multipart upload over `HttpURLConnection`), provider details **[R]**. |
| `LlmClient` | Nothing. | Interface + OpenAI-compatible chat/tool-calling impl (`HttpURLConnection` + kotlinx.serialization), fake for tests. Endpoint/format **[R]**. |
| `AgentLoop` | Nothing. | New, pure-ish orchestrator over `LlmClient` + `ToolRegistry` with max iterations, timeout, suspend/resume from `Capture`. |
| `ToolRegistry` | Nothing. | New: JSON-schema definitions, argument validation (kotlinx `JsonObject`), classification READ / WRITE / DRAFT / INPUT. |
| `FoodResolver` | Pieces: `FoodDao.observeRecent/observeMine`, `filterFoods` (substring), `OffApi.search`. | Tolerant matching (accents, plurals, synonyms), history-first ranking by recency/frequency, base table lookup, raw/cooked, candidate list with source; base table data source **[R]**. |
| `QuantityNormalizer` | `parseGrams`, `gramChips` only. | New: grams / servings × portion / fraction of package / household measures → grams. Needs portions + measures tables. |
| `CaptureStore` | Nothing. | New table + repo + state machine. |
| `PendingInputStore` | Nothing. | New table + repo. |
| `CaptureWorker` | WorkManager present (periodic workers only). | One-time **expedited** unique work per capture, fallback to normal work, `getForegroundInfo` for API < 31 (rules **[R]**). |
| `NotificationCenter` | `Channels`, `Notifier` (single id per type, no actions). | New channels; per-capture notification ids; actions (Undo, choose 1–3, inline RemoteInput reply, Retry) via a `BroadcastReceiver`; `NotifType` ordinal scheme must not be reused for these. |
| `ChatStore` | Nothing. | Tables threads/messages + repo. |
| `UserMemoryStore` | Nothing. | Table + repo + settings screen. |
| Chat UI | Design system components (cards, chips, text field, bottom sheet, badges, list rows). | Chat screen, composer (text, mic, barcode), native cards, threads list, inbox; entry point with badge. |
| Quick capture | Nothing (single `MainActivity`, no foreground service, no shortcuts). | `CaptureActivity` (show when locked), microphone foreground service, static shortcut + launcher alias, `RECORD_AUDIO` / `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE`; behaviour **[R]** + on-device check. |
| Copilot settings | Settings hub + prefs pattern. | "Copilot" screen (URLs, models, masked keys, language, notifications, household measures, test connection), encrypted secret store **[R]**. |

### 3.2 Tools (doc §5)

| Tool | Backing today | Gap |
|---|---|---|
| `get_daily_summary(date)` | `FoodRepository.observeNutrition(d,d)` + `Settings` targets (Home computes "left"). | Formatting for the model; quick adds with unknown macros make "remaining protein" partial → return a `complete` flag. |
| `get_nutrition_targets()` | `SettingsRepository.current()`. | Only protein is always set; others nullable. |
| `get_goals()` | `GoalRepository.observeGoals().first()`. | Goals have no numeric target; return name/notes/deadline/state/linked plans. |
| `resolve_food(name, state)` | — | New `FoodResolver`. |
| `get_product_by_barcode(barcode)` | Inline logic in `ScanRoute` (local → OFF → cache). | Extract to a reusable `FoodRepository`/use-case function; include portions. |
| `get_planned_workouts(from,to)` | `plansOn` (internal in HomeViewModel), `Streak.planned`. | Move `plansOn` to `domain/` and expose; there are no dated planned workouts, only weekday templates. |
| `get_workout_plan()` | `PlanRepository.observePlans()` (non-archived = active, possibly several). | "Active plan" is a set; drafts are new. |
| `get_body_weight(from,to)` | `BodyRepository.observeBetween`. | Fine. |
| `log_meal(items, meal_type, datetime)` | `FoodRepository.addEntry` (single, non-transactional, no capture link). | New transactional batch write with capture id, idempotency, portion/servings snapshot, source; **no meal_type in the app** (see Q1). Only `date` is stored, no time. |
| `log_body_weight(weight_kg, date)` | `BodyRepository.upsert`. | PK is date → overwrites an existing day; undo must restore the previous row (Q5). |
| `save_user_memory(key, value)` | — | New. |
| `propose_workout_plan(plan)` | — | New draft storage; plan model is weekday templates, not weeks (Q3). |
| `request_user_input(kind, prompt, options)` | — | New pending inputs. |

### 3.3 Specific questions from the brief

- **Portions/servings on foods**: only one optional `servingSizeG` per food (label implicitly "serving"); no label, no list, no default flag, no package weight. → new `food_portions` table.
- **Favourites**: yes, `foods.favorite` (star in Food detail; "Mine" tab shows favourites + custom).
- **Raw/cooked**: not modelled at all.
- **Snapshot at log time**: yes, `food_entries` stores name, brand, grams, kcal, P/C/F, image. Missing: source, servings/portion label, capture id.
- **Synonyms / aliases**: none. Frequency: only `lastUsedAt` (no use count; can be computed from `food_entries.foodId`).
- **Draft vs active plans**: none. Only `isArchived`. Many places read "non-archived = active" (`HomeViewModel`, `Streak`, `NotificationRules`, `Insights`, `AchievementEngine`, `DiaryFlags`, `TrendsViewModel`, `WorkoutStartScreen`, `PlansScreen`, `PlanDetailScreen`, `GoalDetailScreen`, export) → a draft flag on `plans` would have to be filtered in ~15 places; a separate table avoids that (see §4).
- **Notifications / WorkManager**: WorkManager used for 2 periodic workers; notifications without actions; no receivers except `RestAlarmReceiver`.
- **Settings screen & storage**: You hub + sub-screens; SharedPreferences (not DataStore). No secret storage.
- **Backup / export rules**: `allowBackup=true`, no exclusion rules; JSON export lists settings fields explicitly (`SettingsDto`), so keys stay out of export as long as they never enter `Settings`. `clearAll()` clears `lifehub_settings` only. Needed: backup rules excluding the secret store and the capture audio directory; decide what copilot data enters the export (Q7).
- **Meal split**: none by design (owner said no breakfast/lunch/dinner) — conflicts with `meal_type` (Q1).

---

## 4. Proposed DB migrations

Principles: additive only (new tables, nullable or defaulted columns), every migration hand-written in `LifeHubDatabase` + schema JSON committed + a `MigrationNToMTest` in the existing style seeding representative rows of every pre-existing table and asserting them unchanged. Export `schemaVersion` bumped when exported shapes change; old files keep importing.

### Phase 1 — no migration (v7)
Settings in prefs; keys in a separate encrypted store **[R]**.

### Phase 2 — v8: chat
```
chat_threads(id TEXT PK, title TEXT NOT NULL, origin TEXT NOT NULL  -- CHAT|QUICK
             , createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)            idx(updatedAt)
chat_messages(id TEXT PK, threadId TEXT NOT NULL FK→chat_threads CASCADE,
             seq INTEGER NOT NULL, role TEXT NOT NULL  -- USER|ASSISTANT|TOOL
             , text TEXT, toolCallsJson TEXT, toolCallId TEXT, toolName TEXT,
             cardType TEXT, cardJson TEXT, captureId TEXT, createdAt INTEGER NOT NULL)
             idx(threadId, seq), idx(captureId)
```

### Phase 3 — v9: captures, pending inputs, portions, measures, entry provenance
```
captures(id TEXT PK, threadId TEXT, origin TEXT NOT NULL  -- CHAT|QUICK
        , status TEXT NOT NULL  -- RECORDED|PROCESSING|AWAITING_INPUT|DONE|FAILED|UNDONE
        , audioPath TEXT, transcript TEXT, inputText TEXT, attachedBarcode TEXT, attachedFoodId TEXT,
        zoneId TEXT NOT NULL, stepsJson TEXT  -- completed tool calls+results, for resume/retry
        , summary TEXT, errorCode TEXT, errorMessage TEXT, attempts INTEGER NOT NULL DEFAULT 0,
        createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)                    idx(status)
capture_writes(id TEXT PK, captureId TEXT NOT NULL FK→captures CASCADE,
        idemKey TEXT NOT NULL, tableName TEXT NOT NULL, recordId TEXT NOT NULL,
        op TEXT NOT NULL  -- INSERT|UPDATE
        , prevJson TEXT  -- previous row for UPDATE (body overwrite, foods.lastUsedAt)
        , createdAt INTEGER NOT NULL)                       UNIQUE(captureId, idemKey, tableName, recordId)
pending_inputs(id TEXT PK, captureId TEXT NOT NULL FK→captures CASCADE,
        kind TEXT NOT NULL  -- CHOOSE_FOOD|QUANTITY|PACKAGE_WEIGHT|USE_ESTIMATE
        , prompt TEXT NOT NULL, optionsJson TEXT, itemJson TEXT NOT NULL, entryDate INTEGER NOT NULL,
        status TEXT NOT NULL  -- OPEN|ANSWERED|DISCARDED
        , answerJson TEXT, notified INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, resolvedAt INTEGER)
        idx(status), idx(captureId)
food_portions(id TEXT PK, foodId TEXT NOT NULL FK→foods CASCADE, label TEXT NOT NULL, grams REAL NOT NULL,
        isDefault INTEGER NOT NULL, kind TEXT NOT NULL  -- PACKAGE|SERVING|PIECE|USER
        , createdAt INTEGER NOT NULL)                                              idx(foodId)
household_measures(id TEXT PK, label TEXT NOT NULL, foodId TEXT  -- null = generic
        , grams REAL NOT NULL, updatedAt INTEGER NOT NULL)            UNIQUE(label, foodId)
base_foods(id TEXT PK, name TEXT NOT NULL, aliases TEXT, state TEXT  -- RAW|COOKED|null
        , kcal100 REAL NOT NULL, protein100 REAL NOT NULL, carbs100 REAL NOT NULL, fat100 REAL NOT NULL,
        sourceRef TEXT)                       -- read-only reference data, seeded from an asset   **[R]**
ALTER TABLE foods ADD COLUMN state TEXT            -- RAW|COOKED|null
ALTER TABLE foods ADD COLUMN aliases TEXT          -- comma list, user synonyms
ALTER TABLE foods ADD COLUMN baseFoodId TEXT       -- when copied from base_foods
ALTER TABLE food_entries ADD COLUMN captureId TEXT  (+ index)
ALTER TABLE food_entries ADD COLUMN source TEXT     -- history|base_table|open_food_facts|llm_estimate|null(manual)
ALTER TABLE food_entries ADD COLUMN servings REAL
ALTER TABLE food_entries ADD COLUMN portionLabel TEXT
ALTER TABLE food_entries ADD COLUMN portionGrams REAL
```
Data step in the same migration: for every `foods` row with `servingSizeG > 0`, insert one `food_portions` row (`label='serving'`, `kind='SERVING'`, `isDefault=1`, deterministic id `'srv-'||id`). `servingSizeG` stays (read by the existing UI until it moves to portions; no column drops). `FoodSource` enum gains `BASE` and `ESTIMATE` (TEXT, old rows unaffected). Seeding `base_foods` and default `household_measures` happens in the migration *and* `onCreate` callback from bundled assets, after the owner validates the values (**[R]**, doc §6). Rationale for a separate `base_foods` table instead of rows in `foods`: keeps thousands of reference rows out of "Mine", export and backup snapshot; a base food is copied into `foods` (source `BASE`) on first log, exactly like OFF products are cached today.

### Phase 4 — no migration expected
`captures` already has audio/transcript/attempts columns. (If research shows extra state is needed, v10 here and shift the next one.)

### Phase 5 — no migration
Body-weight undo uses `capture_writes.prevJson`; retro dates use existing `date` columns.

### Phase 6 — v10: drafts and memory
```
plan_drafts(id TEXT PK, captureId TEXT, threadId TEXT, title TEXT NOT NULL, goalId TEXT, goalName TEXT,
        weeks INTEGER, payloadJson TEXT NOT NULL  -- plans, weekdays, exercises (by name+category), sets, targets
        , status TEXT NOT NULL  -- DRAFT|APPROVED|REJECTED|SUPERSEDED
        , createdAt INTEGER NOT NULL, decidedAt INTEGER, approvedPlanIds TEXT)   idx(status)
user_memory(key TEXT PK, value TEXT NOT NULL, updatedAt INTEGER NOT NULL)
```
Drafts never touch `plans`, so no existing query can treat a draft as active; Approve materialises it (in one transaction: create missing exercises by NOCASE name, create plans + plan_exercises + goal links via `PlanRepository.save`). This makes the cross-cutting "never active without Approve" test structural.

---

## 5. Proposed package layout and dependencies

Follows existing `data/` – `domain/` – `feature/` split and the `AppContainer` locator.

```
copilot/                         (new top-level, mirrors data/off as a self-contained integration)
  llm/        LlmClient.kt (interface + OpenAiCompatibleClient, HttpURLConnection), LlmModels.kt (@Serializable), FakeLlmClient in test
  stt/        Transcriber.kt (interface + CloudTranscriber, multipart), AudioRecorder.kt (MediaRecorder wrapper)
  agent/      AgentLoop.kt, AgentContext.kt (system prompt, memory, date/zone), ToolRegistry.kt, ToolSchemas.kt, ToolResult.kt
  tools/      ReadTools.kt, LogMealTool.kt, LogBodyWeightTool.kt, MemoryTool.kt, PlanDraftTool.kt, RequestInputTool.kt
  capture/    CaptureWorker.kt, CaptureProcessor.kt (testable logic, worker is thin), CaptureScheduler.kt,
              CaptureActivity.kt, CaptureRecordingService.kt
  notify/     CopilotNotifier.kt (NotificationCenter), CopilotActionReceiver.kt
  secrets/    SecretStore.kt (encrypted key storage)  [R]
domain/food/  FoodResolver.kt, QuantityNormalizer.kt, TextMatch.kt (normalise accents/plurals)   — pure, unit-tested
data/model/   CopilotEntities.kt (Capture, CaptureWrite, PendingInput, ChatThread, ChatMessage, FoodPortion,
              HouseholdMeasure, BaseFood, PlanDraft, UserMemory) + enums in Enums.kt
data/db/dao/  CaptureDao.kt, PendingInputDao.kt, ChatDao.kt, PortionDao.kt, BaseFoodDao.kt, PlanDraftDao.kt, MemoryDao.kt
data/repo/    CaptureRepository.kt (CaptureStore), PendingInputRepository.kt, ChatRepository.kt (ChatStore),
              UserMemoryRepository.kt, PlanDraftRepository.kt, FoodLogWriter.kt (transactional log + undo)
data/prefs/   CopilotSettingsRepository.kt (non-secret copilot prefs, separate prefs file)
feature/copilot/  ChatScreen.kt, ChatViewModel.kt, Composer.kt, cards/*.kt, ThreadsScreen.kt, InboxScreen.kt
feature/settings/ CopilotSettingsScreen.kt, MemoryScreen.kt, HouseholdMeasuresScreen.kt
assets/copilot/   base_foods.csv|json, household_measures.json   [R]
res/xml/          backup_rules.xml, data_extraction_rules.xml, shortcuts.xml
```
Routes added to `Routes.kt`: `copilot?threadId=`, `copilot/threads`, `copilot/inbox`, `settings/copilot`, `settings/copilot/memory`, `settings/copilot/measures`; `calendar` gains an optional `date` arg for "Open in diary".

**New dependencies: none required.**
- HTTP: keep `HttpURLConnection` (style of `OpenFoodFactsClient`); request/response sizes are small, no streaming needed. (OkHttp 4.12.0 is in the Gradle cache transitively and would be the fallback if multipart/timeouts get painful — only with owner approval.)
- JSON: kotlinx.serialization (present).
- Audio: platform `MediaRecorder` (AAC/m4a) — no lib.
- Background: WorkManager (present). Tests drive `CaptureProcessor` directly; `androidx.work:work-testing` only if worker wiring itself must be tested (optional, one-line justification: `TestListenableWorkerBuilder`).
- Encryption: platform Android Keystore + `javax.crypto` AES-GCM is dependency-free; final choice **[R]** (Tink 1.7.0 is also cached transitively).
- Notifications with actions/RemoteInput: `androidx.core` (present).

---

## 6. Per-phase implementation plan

Each phase: compiles, `./gradlew :app:testDebugUnitTest --offline -q` green, screenshots of new screens (`recordRoborazziDebug`), app usable with no key configured.

### Phase 1 — Settings, keys, LlmClient, connection test
1. `copilot/secrets/SecretStore.kt` (interface + Keystore impl per **[R]**, in-memory fake for tests); stored in its own prefs file `copilot_secrets`.
2. `data/prefs/CopilotSettingsRepository.kt`: llmBaseUrl, llmModel, sttBaseUrl, sttModel, sttLanguage (default `it`), notifications toggle, "same key for STT" flag; separate prefs file `copilot_settings`; not part of `Settings`/export.
3. `res/xml/data_extraction_rules.xml` + `backup_rules.xml` excluding `copilot_secrets.xml` (and later `files/captures/`); manifest attributes.
4. `copilot/llm/LlmModels.kt`, `LlmClient.kt` (`chat(messages, tools): LlmResult`, typed errors: NoKey, Network, Http(code), ToolsUnsupported, Timeout); no logging of bodies.
5. `feature/settings/CopilotSettingsScreen.kt` (+ViewModel): fields, masked key fields, "Test connection" (minimal call with one dummy tool to detect tool-calling support); row "Copilot" in `YouScreen`; route.
6. `AppContainer`: `copilotSettings`, `secrets`, `llm`.
- Tests: `LlmClientTest` (request JSON built correctly, parse tool calls / errors from fixtures, via a local fake `HttpURLConnection` seam or by testing pure encode/decode functions), `SecretStoreTest` (round trip on Robolectric with fake), `ExportImportTest` addition: export JSON contains no key string; backup rules XML contains the exclusion; screenshot of the settings screen.
- Accept: test connection succeeds on the phone; `git grep` of the key finds nothing; key absent from export and from backup rules scope.

### Phase 2 — Text chat, AgentLoop, read tools, summary card
1. v8 migration + `Migration7To8Test`; `ChatDao`, `ChatRepository`.
2. `ToolRegistry` + schemas + validation (readable errors back to the model).
3. Read tools: `get_daily_summary`, `get_nutrition_targets`, `get_goals`, `get_planned_workouts` (move `plansOn` to `domain/`), `get_workout_plan`, `get_body_weight`.
4. `AgentLoop` (max iterations, timeout, history window constant, older tool results trimmed), `AgentContext` (short system prompt, date/time/zone).
5. `feature/copilot/ChatScreen` + `ChatViewModel` + `Composer` (text only; mic/barcode buttons hidden or disabled), day summary card, states (thinking, network error, missing key → link to settings); entry point (Q6).
- Tests: `ToolRegistryTest` (validation), `ReadToolsTest` (in-memory DB, numbers equal to `observeNutrition`), `AgentLoopTest` with `FakeLlmClient` (scripted tool calls, iteration cap, error path), migration test, screenshots of chat with summary card.
- Accept: "Quante proteine mi mancano oggi?" answers with the Diary numbers.

### Phase 3 — FoodResolver, QuantityNormalizer, portions, direct meal log + Undo
1. v9 migration + `Migration8To9Test` (servingSizeG → portion; all old rows intact); DAOs/repos for captures, writes, pending inputs, portions, measures, base foods; seed assets after owner validates values (**[R]**).
2. `domain/food/TextMatch`, `FoodResolver` (history → base → OFF search → none; candidates with source; raw default), `QuantityNormalizer` (grams, servings × portion/default, fractions, measures).
3. `FoodLogWriter.log(captureId, items, date)`: one transaction, macros computed by the app from per-100 g, snapshot + provenance columns, `capture_writes` rows (insert entries, new foods/portions, previous `lastUsedAt`), idempotent by `idemKey`; `undo(captureId)`: reverse in one transaction, skip missing rows, status UNDONE.
4. Tools `resolve_food`, `log_meal` (partial write + `request_user_input` for the rest), `request_user_input`; pending input cards in chat (choose / answer / discard); answering resumes the capture and appends to the same day + capture.
5. Meal-logged card (items, grams, kcal, macros, source badge, portion used, day remaining) with Undo + Open in diary; Food detail/custom food screen: list/edit portions (minimal) so "box" grams can be set by hand.
- Tests: `FoodResolverTest`, `QuantityNormalizerTest`, `FoodLogWriterTest` (**undo restores DB identical**: compare `BackupRepository.snapshot()` before/after, excluding copilot tables), idempotency (same capture twice → one set of rows), snapshot invariance (edit food after log → entry unchanged), pending state machine tests, AgentLoop scripted scenarios: rice/chicken/oil; "180, no, 120"; "mezzo merluzzo Findus, il solito" (seeded history food with PACKAGE portion → 0.5×grams); unknown food → pending with estimate option, other items written.
- Accept: doc §12 phase 3 row.

### Phase 4 — Voice, quick capture, worker, notifications, inbox
1. `AudioRecorder` (m4a in `filesDir/captures/`), `Transcriber` + cloud impl (**[R]**).
2. `CaptureActivity` (showWhenLocked/turnScreenOn, record indicator, Done, silence auto-stop) + `CaptureRecordingService` (foreground, type microphone) started while the activity is in the foreground; mic permission on first use; manifest permissions; `shortcuts.xml` static shortcut + launcher `activity-alias` "Talk to copilot".
3. `CaptureProcessor` (RECORDED → transcribe if needed → AgentLoop → DONE/AWAITING_INPUT/FAILED; skips completed steps; quick capture disables `save_user_memory`; creates a new thread per quick capture) + thin `CaptureWorker` (unique work per capture id, expedited with non-expedited fallback; re-enqueue RECORDED/PROCESSING captures on app start/boot).
4. `CopilotNotifier`: channels (result, needs answer, error), result with Undo, question with up to 3 choices + RemoteInput, error with Retry; `CopilotActionReceiver`; one notification per capture, never re-posted.
5. Inbox screen (open pending inputs + queued/failed captures) and badge on the chat entry; mic button in composer (tap start/stop).
- Tests: capture/pending state-machine tests, `CaptureProcessorTest` with fake Transcriber/LLM (no network → FAILED + kept audio; Retry → DONE without duplicate rows), receiver handling (undo, choose, reply) with Robolectric, notification content tests (`ShadowNotificationManager`), screenshots of capture activity + inbox.
- Accept: on-device run by the owner (side key → speak → lock → notification with Undo; unknown product → request survives dismissal and app kill). README section with Samsung steps.

### Phase 5 — Barcode in chat, body weight log, retroactive log
1. Extract barcode lookup from `ScanRoute` into `FoodRepository.lookupBarcode(code)`; add a "pick" mode to the scanner returning the food id to the chat (saved-state result) instead of navigating to detail.
2. Composer attachment chip (product card); attached product goes into the turn context; `get_product_by_barcode` tool; OFF mapper requests package quantity fields (**[R]**) → `PACKAGE` portion; if missing, `PACKAGE_WEIGHT` pending input saved once as portion.
3. `log_body_weight` (keeps muscle/fat of an existing day, records `prevJson`), weight card with Undo.
4. Context includes local date, time and zone; date validation (no future dates beyond today unless owner wants).
- Tests: body undo restores previous row / deletes new row; retro scenario "ieri sera…" with fake LLM → entry on yesterday; barcode attachment → item uses that food; OFF mapper fixture with package quantity.
- Accept: doc §12 phase 5 row.

### Phase 6 — Plan drafts, meal suggestion, memory, thread list
1. v10 migration + `Migration9To10Test`; `PlanDraftRepository` (`propose`, `approve` transactional materialisation, `reject`), `UserMemoryRepository`.
2. `propose_workout_plan` tool + draft card (Approve / Reject / Open); "Drafts" section on `PlansScreen` + draft detail screen with Approve.
3. Meal suggestion card ("Log as meal" → `FoodLogWriter` with a new capture, Undo); suggestion only from known foods so macros are computed by the app.
4. `save_user_memory` (chat only) + `MemoryScreen` in settings; memory injected in context (size cap).
5. Threads list (open, delete) with pending badge.
- Tests: **draft never active without Approve** (after propose: `plans` table unchanged, Home/Streak/Notification inputs unchanged; after approve: plans exist), reject/supersede, memory injection size cap, screenshots (draft card, drafts section, memory, threads).
- Accept: doc §12 phase 6 row.

---

## 7. Ambiguities and contradictions — owner decisions needed

1. **Meal type.** Doc has `log_meal(items, meal_type, …)` and "same meal, same date and type"; the app deliberately has no meal split. → *Recommend*: no meal type anywhere; a "meal" is the set of entries of one capture (`food_entries.captureId`) on one date; resolved pending items join that capture.
2. **Time of a log.** Entries store only `date`. "Ieri sera" only needs the date. → *Recommend*: keep date only; no time column.
3. **What is a "workout plan" draft?** Doc: "4 weeks toward the muscle up, weeks/sessions/exercises". App: weekday templates (one session each), several active at once, auto-progression, no weeks. → *Recommend*: a draft = optional goal (new or existing, deadline = start + N weeks) + 1..n weekday plans with exercises/sets/targets; "weeks" only sets the goal deadline; progression stays the existing auto-raise. No per-week variation in v1.
4. **Approve vs existing active plans.** "Does not replace the active plan" — but after Approve, should overlapping weekday plans be archived? → *Recommend*: Approve only adds plans; the approval screen lists active plans on the same weekdays with an opt-in "archive these" checkbox (default off).
5. **Body weight on a day that already has an entry** (PK = date). → *Recommend*: overwrite weight only, keep muscle/fat, store the previous row; Undo restores it exactly.
6. **Chat entry point and badge.** Bottom bar is full (Home · Diary · + · Trends · Train). → *Recommend*: icon with badge in the Home header next to the profile avatar, plus "Ask copilot" in the "+" sheet; no new tab.
7. **Copilot data in export/backup.** → *Recommend*: export includes food portions, household measures, user memory and the new `food_entries` provenance columns; excludes chats, captures, audio, pending inputs and all keys. Android auto-backup excludes secrets and audio; DB still backed up as today.
8. **Strictness of "Undo restores the DB identical".** Logging also caches OFF/base foods, creates portions and bumps `lastUsedAt`. → *Recommend*: strict — undo also deletes foods/portions created by that capture (if not used by other entries) and restores `lastUsedAt`; the identity test excludes only copilot bookkeeping tables (captures, chat, pending).
9. **UI language.** App chrome is English; doc wants model replies in Italian. → *Recommend*: chat chrome, cards and notifications titles in English (consistent with the app); model text in Italian (system prompt); STT language default `it`.
10. **Missing OFF macros.** OFF products with missing macros are stored as 0 today. → *Recommend*: keep as is for v1 (no schema change), but `resolve_food` flags such products as `incomplete` so the card shows it.
11. **"Il solito" with several matching history foods.** Doc: most recent wins. → *Recommend*: most recently used wins only if the name match is strong; otherwise CHOOSE_FOOD pending input (top 3).
12. **Default dependencies policy.** Doc allows motivated new libraries; `docs/02-architecture.md` forbids them (offline build). → *Recommend*: zero new runtime dependencies (plan above needs none); revisit only for encryption if **[R]** says the platform API is insufficient.
13. **Manifest "no foreground service" override** (`AndroidManifest.xml` comment from the rest-timer work). → *Recommend*: lift it only for the microphone capture service (doc §11 requires it); rest timer unchanged.
14. **Future dates.** Can the copilot log meals/weight on future dates? → *Recommend*: reject dates after today with a readable tool error.

Values still to validate with the owner (from doc §6/§13, details in **[R]**): base food table source/licence and initial household measures; LLM/STT default providers and models; key-encryption mechanism; side-key behaviour on the A55.
