# Life Hub — Product Specification

Version 1.0 · Source: `docs/00-requirements-brief.md` · Status: ready for review

Conventions: `MUST` = required for v1. IDs are stable; never renumber, only append. "AC" = acceptance criteria (each is testable). "—" denotes "no data" in UI.

---

## 1. Product principles

| ID | Principle | Testable consequence |
|---|---|---|
| P-1 | Simple | Every core action (start workout, log macros, log weight) reachable in ≤ 2 taps from Home. |
| P-2 | Native & fast | Cold start to Home < 1.5 s on a mid-range device; all list/calendar screens render from local DB only (no network). |
| P-3 | Dry, no bloat | No onboarding wizard, no social features, no ads, no accounts, no network permission. Exercise library ≤ 40 built-in entries. |
| P-4 | English UI | All strings in English (`values/strings.xml` only; no other locales in v1). |
| P-5 | shadcn-like visual language | Neutral palette, thin 1dp borders, rounded-md (8dp) cards, clear type scale, system light/dark. No gradients, no illustrations. |
| P-6 | Clean, exportable data model | Every persisted entity is exportable to JSON losslessly (export → import round-trip is identity). |
| P-7 | Mobile-UX best practices | Numeric fields open numeric keyboard; destructive actions confirm; forms save with one primary button; workout flow is one-hand usable. |

---

## 2. Features

### 2.1 Calendar (F-CAL)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-CAL-1 | As a user I can browse a month calendar, past and future. | Month grid, weeks start Monday. Swipe/arrows move ±1 month without limit. "Today" button returns to current month. Today's cell is visually highlighted. |
| F-CAL-2 | Each day shows what I logged. | A day cell shows up to 3 distinct markers: workout (≥ 1 COMPLETED session dated that day), macros (MacroDay exists), weight (BodyEntry exists). Markers are visually distinct (color/shape) and have a legend. |
| F-CAL-3 | Tapping a day opens its summary. | Day summary sheet/screen shows: each COMPLETED session that day (plan name, duration, per exercise: sets as `reps×weight` / `seconds`), MacroDay totals (P/C/F/kcal) or "—", BodyEntry (weight/muscle/fat/fat%) or "—". |
| F-CAL-4 | From the day summary I can add or edit data for that date. | Buttons: "Log macros" (opens F-NUT-1 prefilled with that date), "Log weight" (opens F-BODY-1 prefilled), "Log workout" (opens F-WO-8 manual log with that date). Editing existing MacroDay/BodyEntry opens the same form prefilled. |

### 2.2 Goals (F-GOAL)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-GOAL-1 | I can create a goal (e.g. "Muscle Up", "Dragon Flag (clean)"). | Form fields: name (required, 1–60 chars), notes (optional, multiline), startDate (default today, one-tap date picker), deadline (default `startDate + 84 days`, editable, must be > startDate). Saving creates Goal with state ACTIVE. |
| F-GOAL-2 | Changing the start date keeps the 12-week default. | If deadline was never manually edited, changing startDate re-computes deadline = startDate + 84 d. Once deadline is edited manually, it is no longer auto-recomputed. |
| F-GOAL-3 | I see how much of the goal's time window has elapsed. | Goal card and detail show a time progress bar: `elapsedFraction` per §4.6. Label shows "N days left" (N ≥ 0) or "Overdue by N days" when today > deadline. The bar never represents goal achievement. |
| F-GOAL-4 | I can mark a goal achieved or archive it. | Actions: "Mark achieved" (state → ACHIEVED, sets `achievedAt` = today), "Archive" (→ ARCHIVED), "Reactivate" (→ ACTIVE, clears achievedAt). Goal list has tabs/filter Active · Achieved · Archived. Delete requires confirmation; deleting a goal does NOT delete linked plans (link rows are removed). |
| F-GOAL-5 | Opening a goal shows its linked plans, their weekdays and exercises. | Goal detail lists each GoalPlanLink: plan name, weekday chips (Mon…Sun, scheduled ones highlighted), ordered exercises with set count and target, and session progress per F-GOAL-6. Tapping a plan opens plan detail. |
| F-GOAL-6 | I see session progress toward the goal per plan. | For each link with `sessionsNeeded` set: "done / needed" + bar, where done = per §4.7. If `sessionsNeeded` is null, show only "done" count. |
| F-GOAL-7 | Multiple goals can be active at once. | No limit on ACTIVE goals; Home and goal list show all of them. |
| F-GOAL-8 | Sub-goals are not supported. | No UI or entity for sub-goals (see §8). |

### 2.3 Training plans (F-PLAN)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-PLAN-1 | I can create a plan with an ordered exercise list. | Form: name (required), weekdays (multi-select Mon–Sun, may be empty), ordered list of PlanExercise. Reorder via drag handle or up/down. Delete plan requires confirmation; sessions already logged with it keep a snapshot (plan name stored on session). |
| F-PLAN-2 | Each plan exercise has N sets with a target, rest time and optional weight. | PlanExercise fields: exercise (from library), sets (1–20), target type REPS / MAX / SECONDS, target value (required for REPS and SECONDS, ≥ 1; hidden for MAX), default added weight kg (optional, ≥ 0, step 0.5), rest seconds (default 90, 0–600). Target type defaults from `Exercise.defaultTargetType`. |
| F-PLAN-3 | A plan may optionally be linked to one or more goals. | Plan form has "Linked goals" multi-select (ACTIVE goals). Each link has optional `sessionsNeeded` (integer 1–500). Links are editable from both plan and goal detail. Unlinked plans are fully usable. |
| F-PLAN-4 | Plan weekdays drive "which days I should train". | `weekdays` is a set of ISO weekdays (1=Mon…7=Sun). Used by F-HOME-5 (adherence) and F-NOTIF-1. Empty set = no planned days (adherence shows "—" for that plan). |
| F-PLAN-5 | I can duplicate a plan. | "Duplicate" creates a copy named "<name> (copy)" with same exercises, weekdays, no goal links. |

### 2.4 Exercise library (F-EX)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-EX-1 | The app ships a small curated calisthenics library. | Seeded on first launch, 30–40 exercises, categories: PULL, PUSH, CORE, ISOMETRIC, LEGS, ACCESSORY. Mandatory entries listed in §3.11. Built-in exercises have `isCustom=false` and cannot be deleted (can be hidden). |
| F-EX-2 | I can add custom exercises. | Form: name (required, unique case-insensitive), category, defaultTargetType (REPS or SECONDS). Custom exercises are editable/deletable; deleting one that is referenced by a plan or session is blocked with a message. |
| F-EX-3 | I can find exercises quickly when building a plan. | Picker with search-as-you-type on name and category filter chips. |

### 2.5 Live workout (F-WO)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-WO-1 | I start a session by picking a plan. | Home "Start workout" → plan list → "Start". Creates WorkoutSession(status=IN_PROGRESS, startedAt=now, date=today) and SessionSets from the plan (one per planned set, `targetType/targetValue/weightKg` copied). If an IN_PROGRESS session already exists, Home shows "Resume workout" instead and starting a new one asks to discard or resume. |
| F-WO-2 | I step through exercises and sets. | Screen shows current exercise, set i/N, target, and a list of all exercises with completion state. Next/previous navigation. Progress indicator "sets done / total". |
| F-WO-3 | Completing a REPS set defaults to the target. | For target REPS: actual reps field prefilled with target; one tap on "Done" confirms; field editable (0–999). |
| F-WO-4 | Completing a MAX set requires actual reps. | For target MAX: actual reps field empty; "Done" disabled until a value ≥ 0 is entered. |
| F-WO-5 | SECONDS sets have an integrated isometric timer. | For target SECONDS: Start/Stop stopwatch counting up; a beep/vibration at target seconds; on Stop the actual seconds field = elapsed (editable). Alternatively type seconds directly. |
| F-WO-6 | I can edit weight per set. | Weight kg field (step 0.5, ≥ 0) prefilled from plan; editing one set does not change others unless "apply to remaining sets" is tapped. |
| F-WO-7 | I can modify the session on the fly. | Actions: skip set (status SKIPPED), add set (copies previous set's target), remove set, add exercise (from library, with sets/target entered inline), swap exercise (replaces exercise on all not-done sets of that exercise). Changes affect only this session, never the plan. |
| F-WO-8 | I can log a past workout manually. | From calendar day or Home overflow: choose plan, date (≤ today), optional start time and duration; all sets prefilled with targets and marked DONE; user edits actuals and saves. Session saved as COMPLETED with `isManual=true`. |
| F-WO-9 | A rest timer runs automatically after each completed set. | After "Done": countdown = PlanExercise.restSeconds (or 90 if ad-hoc). Controls: skip, −15 s, +15 s. Vibration + sound at 0. Timer keeps running when screen is off (foreground service or alarm) and when app is backgrounded. No rest timer after the last set of the session. |
| F-WO-10 | I finish the session with notes and RPE. | "Finish" → sheet with optional notes (multiline) and RPE (1–10 slider/segmented, optional). Saving sets status=COMPLETED, `endedAt=now`, `durationSec = endedAt − startedAt`. Sets never marked are stored as SKIPPED. Session appears in calendar on its `date`. |
| F-WO-11 | The session survives backgrounding and process death. | All set actuals and status are written to Room on every change. Killing and reopening the app restores the exact in-progress state (current exercise/set index included). Rest timer end time is persisted so it resumes correctly. |
| F-WO-12 | I can discard a session. | "Discard" with confirmation deletes the IN_PROGRESS session and its sets. |
| F-WO-13 | I can view and edit a saved session. | Session detail shows all sets; actuals, weight, notes, RPE, date editable; delete with confirmation. |

### 2.6 Nutrition (F-NUT)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-NUT-1 | I log one macro total per day. | Form: date (default today), protein g, carbs g, fat g (integers ≥ 0; at least one > 0), kcal (optional integer ≥ 0). One MacroDay per date (upsert: logging an existing date edits it). |
| F-NUT-2 | kcal is auto-computed if left empty. | If kcal empty on save: `kcal = 4·protein + 4·carbs + 9·fat` (rounded to integer) and `kcalIsAuto=true`. If the user types kcal, it is kept as-is and `kcalIsAuto=false`. Form shows the computed value live as a hint while kcal is empty. |
| F-NUT-3 | Protein target is a single setting. | Settings.proteinTargetG (integer 0–500, default 150). No kcal target anywhere. |
| F-NUT-4 | No per-meal diary. | MacroDay is the only nutrition entity (see §8). |

### 2.7 Body weight (F-BODY)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-BODY-1 | I log a scale reading. | Form: date (default today), weightKg (required, 20–300, step 0.1), muscleKg (optional, 0–weightKg), fatKg (optional, 0–weightKg). One BodyEntry per date (upsert). Reachable from Home quick action, calendar day, and weight notification deep link. |
| F-BODY-2 | Body-fat % is derived, not stored. | Display `fatPct = fatKg / weightKg × 100` (1 decimal) when fatKg present, else "—". |
| F-BODY-3 | I can review/edit history. | Body list sorted by date desc; tap → edit; swipe/long-press → delete with confirmation. |

### 2.8 Home dashboard (F-HOME)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-HOME-1 | I choose a period of 1 or 2 weeks and navigate back/forward. | Segmented control "1W · 2W". Period definition per §4.1. Arrows move to previous/next period; forward disabled beyond current period. Header shows date range "Mon 3 Nov – Sun 9 Nov". Selection persisted (Settings.homePeriod). |
| F-HOME-2 | I see daily kcal and protein charts for the period. | Bar chart (Compose Canvas): one bar per day for kcal, one for protein (two charts or toggle). Protein chart draws a horizontal dashed line at proteinTargetG. Days without MacroDay have no bar. Y axis from 0. |
| F-HOME-3 | I see weight and muscle mass trend. | Line chart with two series (weight, muscle) over the period (points at BodyEntry dates; lines connect consecutive points). Weight on left axis; muscle may use same axis. If < 1 point, show "—". |
| F-HOME-4 | Insight cards show concrete numbers vs the previous period. | Cards (each: current value, Δ vs previous with sign/arrow, "—" when undefined per §4): Weight Δ, Muscle Δ, Fat Δ, Sessions done, Adherence, Avg reps/set, Volume, Avg protein, Avg kcal. Formulas §4.2–4.5. |
| F-HOME-5 | Adherence tells me planned vs done sessions. | Card shows "done / planned (xx %)" and "better/worse/same vs previous period" per §4.4. |
| F-HOME-6 | I see active goals at a glance. | List of ACTIVE goals: name, time bar + days left (F-GOAL-3), and per linked plan "done / needed" (F-GOAL-6). Tap → goal detail. Empty state: "No active goals" + "Add goal". |
| F-HOME-7 | Quick actions. | Three buttons always visible: Start workout (or Resume), Log macros, Log weight. |

### 2.9 Notifications (F-NOTIF)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-NOTIF-1 | Reminded to train. | Rule N-TRAIN per §5. Tap → Home (plan list to start). |
| F-NOTIF-2 | Reminded when last week's protein was low. | Rule N-PROTEIN per §5. Tap → Home with period 1W set to last week. |
| F-NOTIF-3 | Reminded to weigh myself. | Rule N-WEIGHT per §5. Tap → body entry form (F-BODY-1) with today's date. |
| F-NOTIF-4 | Each notification type can be toggled. | Settings has three switches (default on). Disabled types never fire. On Android 13+, POST_NOTIFICATIONS permission requested when the first toggle is turned on (or at first launch of settings). |
| F-NOTIF-5 | Daily check time is configurable. | Settings.dailyCheckTime (default 18:00 local). A WorkManager periodic job evaluates all rules once per day at that time (±15 min tolerance). Changing the time reschedules the job. |

### 2.10 Settings (F-SET)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-SET-1 | Protein target. | Integer field, default 150 g (F-NUT-3). |
| F-SET-2 | Notification toggles and check time. | Three switches + time picker (F-NOTIF-4/5). |
| F-SET-3 | Export data. | "Export JSON" and "Export CSV (zip)" per §6; delivered via Android share sheet AND "Save to…" (SAF). |
| F-SET-4 | Import JSON backup. | "Import JSON" opens file picker; validates `schemaVersion`; shows summary (counts) and asks "Replace all data?" before applying. Import is all-or-nothing (transaction). Unknown fields ignored; missing optional fields defaulted. |
| F-SET-5 | Generate mock data. | "Generate mock data" per §7, with confirmation if DB non-empty (option: replace). Runs < 3 s. |
| F-SET-6 | Clear all data. | "Clear all data" requires typed confirmation ("DELETE") and wipes every table, then re-seeds the built-in exercise library and default settings. |
| F-SET-7 | About. | App version and schemaVersion shown. |

### 2.11 Export (F-EXP) and mock data (F-MOCK)

| ID | User story | Acceptance criteria |
|---|---|---|
| F-EXP-1 | JSON export is complete and self-describing. | Single file `lifehub-export-YYYYMMDD-HHmm.json` matching schema §6.1; contains every row of every entity (incl. ARCHIVED goals, custom and built-in exercises, SKIPPED sets); `readme` field present. |
| F-EXP-2 | CSV export for spreadsheet/AI use. | Zip `lifehub-export-YYYYMMDD-HHmm.zip` with files in §6.2; UTF-8, comma separator, header row, ISO dates. |
| F-EXP-3 | Export → import round-trip is lossless. | Importing an export yields identical row counts and field values (ids preserved). |
| F-MOCK-1 | Mock data previews the whole app realistically. | Dataset per §7 populates calendar, goals, plans, sessions, macros, body entries so every Home card and chart shows non-"—" values for the current and previous period. |

---

## 3. Domain model

All dates are local calendar dates (`LocalDate`, ISO `YYYY-MM-DD`); timestamps are epoch millis UTC with the IANA timezone stored in Settings for interpretation. IDs are UUID strings. Units: kg, g, kcal, seconds.

### 3.1 Goal
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| name | String | 1–60 chars |
| notes | String? | |
| startDate | LocalDate | default today |
| deadline | LocalDate | default startDate + 84 d; invariant deadline > startDate |
| deadlineIsAuto | Boolean | true until user edits deadline (F-GOAL-2) |
| state | Enum ACTIVE / ACHIEVED / ARCHIVED | |
| achievedAt | LocalDate? | set when ACHIEVED |
| createdAt | Instant | |

### 3.2 Plan
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| name | String | |
| weekdays | Set<Int> | ISO 1..7; may be empty |
| notes | String? | |
| isArchived | Boolean | archived plans hidden from Start list, kept for history |
| createdAt | Instant | |

### 3.3 GoalPlanLink
| Field | Type | Notes |
|---|---|---|
| goalId | UUID → Goal | composite PK (goalId, planId) |
| planId | UUID → Plan | |
| sessionsNeeded | Int? | 1–500 |

### 3.4 Exercise
| Field | Type | Notes |
|---|---|---|
| id | UUID | built-ins have stable fixed ids (e.g. `ex-pullup`) |
| name | String | unique, case-insensitive |
| category | Enum PULL / PUSH / CORE / ISOMETRIC / LEGS / ACCESSORY | |
| defaultTargetType | Enum REPS / SECONDS | |
| isCustom | Boolean | |
| isHidden | Boolean | built-ins can be hidden |

### 3.5 PlanExercise
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| planId | UUID → Plan | cascade delete |
| exerciseId | UUID → Exercise | |
| orderIndex | Int | 0-based, unique per plan |
| sets | Int | 1–20 |
| targetType | Enum REPS / MAX / SECONDS | |
| targetValue | Int? | required for REPS/SECONDS (≥ 1), null for MAX |
| weightKg | Double? | ≥ 0, step 0.5 |
| restSeconds | Int | 0–600, default 90 |

Invariant: a plan's sets all share the PlanExercise target (no per-set targets in the plan; per-set variation happens in the session).

### 3.6 WorkoutSession
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| planId | UUID? → Plan | null if plan deleted |
| planName | String | snapshot at start |
| date | LocalDate | calendar day (startedAt local date, or manual) |
| startedAt | Instant | |
| endedAt | Instant? | null while IN_PROGRESS |
| durationSec | Int? | endedAt − startedAt; manual: user-entered or null |
| status | Enum IN_PROGRESS / COMPLETED | |
| rpe | Int? | 1–10 |
| notes | String? | |
| isManual | Boolean | |
| currentSetIndex | Int | for resume (F-WO-11) |
| restEndsAt | Instant? | for resume of rest timer |

Invariant: at most one session with status IN_PROGRESS.

### 3.7 SessionSet
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| sessionId | UUID → WorkoutSession | cascade delete |
| exerciseId | UUID → Exercise | |
| exerciseName | String | snapshot |
| exerciseOrder | Int | order of the exercise within the session |
| setIndex | Int | 0-based within exercise |
| targetType | Enum REPS / MAX / SECONDS | |
| targetValue | Int? | null for MAX |
| actualReps | Int? | REPS/MAX; null until done |
| actualSeconds | Int? | SECONDS; null until done |
| weightKg | Double? | |
| status | Enum PENDING / DONE / SKIPPED | |
| completedAt | Instant? | |

Invariant: DONE ⇒ (targetType ∈ {REPS, MAX} ⇒ actualReps ≠ null) ∧ (targetType = SECONDS ⇒ actualSeconds ≠ null).

### 3.8 MacroDay
| Field | Type | Notes |
|---|---|---|
| date | LocalDate | PK |
| proteinG | Int | ≥ 0 |
| carbsG | Int | ≥ 0 |
| fatG | Int | ≥ 0 |
| kcal | Int | ≥ 0 |
| kcalIsAuto | Boolean | true ⇒ kcal = 4P + 4C + 9F |
| updatedAt | Instant | |

### 3.9 BodyEntry
| Field | Type | Notes |
|---|---|---|
| date | LocalDate | PK |
| weightKg | Double | 20–300, 1 decimal |
| muscleKg | Double? | 0 ≤ muscleKg ≤ weightKg |
| fatKg | Double? | 0 ≤ fatKg ≤ weightKg |
| updatedAt | Instant | |

Derived: `fatPct = fatKg / weightKg × 100`.

### 3.10 Settings (single row)
| Field | Type | Default |
|---|---|---|
| proteinTargetG | Int | 150 |
| notifyTrain | Boolean | true |
| notifyProtein | Boolean | true |
| notifyWeight | Boolean | true |
| dailyCheckTime | LocalTime | 18:00 |
| homePeriod | Enum ONE_WEEK / TWO_WEEKS | ONE_WEEK |
| timezone | String | device zone id |
| lastNotified | Map<type, LocalDate> | empty (frequency cap state) |

### 3.11 Built-in exercise library (mandatory entries)

| Category | Exercises (defaultTargetType) |
|---|---|
| PULL | Pull-up (REPS), Chin-up (REPS), Wide pull-up (REPS), Archer pull-up (REPS), Negative pull-up (REPS), Explosive pull-up (REPS), Ring row (REPS), Ring pull-up (REPS), Muscle-up (REPS), Muscle-up negative (REPS), Straight-bar dip transition (REPS) |
| PUSH | Push-up (REPS), Diamond push-up (REPS), Pseudo-planche push-up (REPS), Pike push-up (REPS), Dip (REPS), Ring dip (REPS), Ring push-up (REPS) |
| CORE | Hanging leg raise (REPS), Hanging knee raise (REPS), Dragon flag negative (REPS), Dragon flag (REPS), Tuck dragon flag (REPS), Hollow body rock (REPS) |
| ISOMETRIC | L-sit (SECONDS), Hollow body hold (SECONDS), Tuck front lever (SECONDS), Advanced tuck front lever (SECONDS), Front lever (SECONDS), Tuck back lever (SECONDS), Back lever (SECONDS), Planche lean (SECONDS), Tuck planche (SECONDS), Dead hang (SECONDS), Support hold (SECONDS) |
| LEGS | Squat (REPS), Pistol squat (REPS), Nordic curl (REPS) |
| ACCESSORY | Triceps pushdown (REPS), Biceps curl (REPS), Face pull (REPS), Lateral raise (REPS) |

### 3.12 Relationships
- Goal ⟷ Plan: many-to-many via GoalPlanLink (sessionsNeeded on the link).
- Plan 1→N PlanExercise N→1 Exercise.
- WorkoutSession 1→N SessionSet; SessionSet N→1 Exercise; WorkoutSession N→0..1 Plan.
- MacroDay, BodyEntry keyed by date (one per day).

---

## 4. Metrics and insights

### 4.1 Period
- `len` ∈ {7, 14} days (Settings.homePeriod).
- Weeks are Monday-aligned. `currentWeekStart` = most recent Monday ≤ today.
- Current period: `start = currentWeekStart − (len − 7)`, `end = start + len − 1` (inclusive). For 2W the current period is the current week plus the previous week.
- Navigation offset k (0 = current, 1 = one period back…): `start_k = start − k·len`, `end_k = start_k + len − 1`.
- Previous period of period k = period k+1 (same length, immediately before, no gap).
- "In period" = `start ≤ date ≤ end` on the entity's `date`.
- Current period includes future days; those simply have no data.

### 4.2 Body deltas (weight, muscle, fat)
- `last(P, field)` = value of `field` of the BodyEntry with the greatest date in P where `field` is non-null; undefined if none.
- `Δ = last(current) − last(previous)`; shown with 1 decimal and sign ("−0.6 kg").
- Card main value = `last(current)`. If `last(current)` undefined → card shows "—". If only `last(previous)` undefined → value shown, Δ "—".
- Rationale: last-vs-last is chosen (not first-to-last) because weekly weigh-ins would otherwise often leave a single point per period.

### 4.3 Sessions and training volume
- `sessions(P)` = count of WorkoutSession with status COMPLETED and date in P. Δ = integer difference.
- `repSets(P)` = SessionSets with status DONE, targetType ∈ {REPS, MAX}, belonging to COMPLETED sessions in P.
- `avgRepsPerSet(P) = Σ actualReps / |repSets(P)|`, 1 decimal; "—" if |repSets| = 0.
- `volume(P) = Σ actualReps` over repSets(P) (total reps). Secondary line: `holdSeconds(P) = Σ actualSeconds` over DONE SECONDS sets.
- Δ for each = current − previous; "—" if either side undefined (volume/sessions are 0, not undefined, when no sessions).

### 4.4 Adherence
- Planned days: for each non-archived Plan with non-empty `weekdays`, the dates d in P with `d ≤ today` and `isoWeekday(d) ∈ weekdays`. `planned(P)` = number of (plan, date) pairs.
- Done: a (plan, date) pair is done if a COMPLETED session with that `planId` exists on `date`. `done(P)` = count of done pairs.
- `adherence(P) = done / planned × 100` (integer %); "—" if planned = 0.
- Card: "done / planned (xx %)" + comparison: `adherence(current) − adherence(previous)` in percentage points → "better" (> 0), "worse" (< 0), "same" (= 0); "—" if either undefined.
- Sessions done on non-planned days count in `sessions(P)` but not in adherence.

### 4.5 Nutrition
- `loggedDays(P)` = MacroDays with date in P.
- `avgProtein(P) = Σ proteinG / |loggedDays|`, `avgKcal(P) = Σ kcal / |loggedDays|`, integers; "—" if no logged days.
- Δ = current − previous; "—" if either undefined.
- Protein vs target: `proteinVsTarget(P) = avgProtein(P) − proteinTargetG` (g, signed) and `daysOnTarget(P)` = count of logged days with proteinG ≥ proteinTargetG, shown "x / n days ≥ target".

### 4.6 Goal time progress
- `totalDays = deadline − startDate` (≥ 1).
- `elapsedDays = clamp(today − startDate, 0, totalDays)`.
- `elapsedFraction = elapsedDays / totalDays`.
- `daysLeft = deadline − today` (may be negative → "Overdue by |n| days").

### 4.7 Goal-plan session progress
- `done(goal, plan)` = count of COMPLETED sessions with `planId = plan.id` and `goal.startDate ≤ date ≤ (achievedAt ?: today)`.
- Progress = `done / sessionsNeeded` (capped at 100 % for bar; text may exceed, e.g. "38 / 36").

### 4.8 General edge cases
- Any undefined value renders as "—"; a Δ is undefined if either operand is undefined.
- Charts with zero data points show the axes plus a centered "No data" label.
- Days in the future never contribute planned days (§4.4) nor data.

---

## 5. Notifications

Local only, WorkManager periodic worker running daily at `Settings.dailyCheckTime` (default 18:00 local; ±15 min). Each type has a channel. Frequency cap: at most one notification per type per calendar day, enforced via `Settings.lastNotified[type] == today ⇒ skip`. A type fires only if its toggle is on. All notifications are dismissible and non-persistent.

| Type | ID | Trigger (evaluated at check time, all conditions AND) | Text | Deep link |
|---|---|---|---|---|
| N-TRAIN | F-NOTIF-1 | Either (a) today's ISO weekday ∈ weekdays of any non-archived plan AND no COMPLETED or IN_PROGRESS session with date = today; or (b) no COMPLETED session in the last 3 days (today, −1, −2) AND at least one COMPLETED session exists ever. | (a) "Training day — <plan name>. You haven't trained yet today." (b) "3 days without training. Time to move?" | `lifehub://workout/start` (plan list; if one plan matches today, preselect it) |
| N-PROTEIN | F-NOTIF-2 | Today is Monday. Let W = last week (Mon–Sun), W′ = the week before. `loggedDays(W) ≥ 3` AND (`avgProtein(W) < proteinTargetG` OR (`avgProtein(W′)` defined AND `avgProtein(W) < 0.9 × avgProtein(W′)`)). | "Last week avg protein was <x> g/day (target <t> g)." or "…, down from <y> g/day the week before." | `lifehub://home?period=1W&offset=1` |
| N-WEIGHT | F-NOTIF-3 | `daysSinceLastEntry = today − max(BodyEntry.date)` (∞ if none and at least one other data row exists). Fire when `daysSinceLastEntry ∈ {7, 14}` or `daysSinceLastEntry ≥ 14 AND daysSinceLastEntry mod 7 == 0`. | "You haven't logged your weight for 1 week — weigh yourself." / "…for 2 weeks…" / "…for N weeks…" | `lifehub://body/new?date=today` |

Additional rules:
- Deep links route via Navigation Compose; if an IN_PROGRESS session exists, `workout/start` opens the resume screen instead.
- The worker is (re)scheduled on app start, on boot (BOOT_COMPLETED), and when `dailyCheckTime` changes.
- If the device was off at check time, WorkManager's catch-up run still respects the once-per-day cap.
- On Android 13+, no notification is attempted without POST_NOTIFICATIONS granted; toggles show a hint if denied.

---

## 6. Export

### 6.1 JSON (schemaVersion 1)

Top-level object, keys in this order:

| Key | Type | Content |
|---|---|---|
| schemaVersion | Int | 1 |
| exportedAt | String | ISO-8601 with offset |
| app | Object | `{ "name": "Life Hub", "version": "<semver>" }` |
| readme | String | Human/AI-readable description (text below) |
| units | Object | `{ "weight": "kg", "macros": "g", "energy": "kcal", "duration": "seconds", "dates": "ISO-8601 local date", "timestamps": "epoch millis UTC" }` |
| settings | Object | Settings §3.10 (excluding `lastNotified`) |
| goals | Array<Goal> | §3.1 fields, plus `planLinks: [{ planId, sessionsNeeded }]` |
| plans | Array<Plan> | §3.2 fields, plus `exercises: [PlanExercise §3.5]` ordered by orderIndex |
| exercises | Array<Exercise> | §3.4 (built-in and custom) |
| sessions | Array<WorkoutSession> | §3.6 fields, plus `sets: [SessionSet §3.7]` ordered by exerciseOrder, setIndex |
| macros | Array<MacroDay> | §3.8, sorted by date |
| bodyEntries | Array<BodyEntry> | §3.9, sorted by date, plus derived `fatPct` |

Field naming: camelCase exactly as in §3. Enums as upper-case strings. Nulls written explicitly as `null`.

`readme` text (verbatim):
> Life Hub export. All personal fitness data of one user. `goals` are objectives with a time window (startDate→deadline) and links to `plans` (planLinks.sessionsNeeded = estimated sessions to reach the goal). `plans` are training templates: ordered `exercises`, each with `sets`, `targetType` (REPS = fixed reps, MAX = as many reps as possible, SECONDS = isometric hold), `targetValue`, optional `weightKg` (added load) and `restSeconds`; `weekdays` are ISO weekdays (1=Mon..7=Sun) on which the plan is scheduled. `sessions` are performed workouts (`status` COMPLETED or IN_PROGRESS) with `sets`: `actualReps` for REPS/MAX, `actualSeconds` for SECONDS, `status` DONE/SKIPPED/PENDING, `weightKg` added load. `macros` are daily totals (one per date): proteinG, carbsG, fatG, kcal (kcalIsAuto=true means kcal = 4·protein + 4·carbs + 9·fat). `bodyEntries` are scale readings per date: weightKg, muscleKg, fatKg, fatPct (derived). `settings.proteinTargetG` is the daily protein goal. Ids are UUID strings and are referentially consistent within this file.

### 6.2 CSV (zip)

| File | Columns |
|---|---|
| goals.csv | id,name,notes,startDate,deadline,state,achievedAt,createdAt |
| goal_plan_links.csv | goalId,planId,sessionsNeeded |
| plans.csv | id,name,weekdays(";"-separated ints),notes,isArchived,createdAt |
| plan_exercises.csv | id,planId,orderIndex,exerciseId,exerciseName,sets,targetType,targetValue,weightKg,restSeconds |
| exercises.csv | id,name,category,defaultTargetType,isCustom,isHidden |
| sessions.csv | id,planId,planName,date,startedAt,endedAt,durationSec,status,rpe,notes,isManual |
| session_sets.csv | id,sessionId,sessionDate,exerciseId,exerciseName,exerciseOrder,setIndex,targetType,targetValue,actualReps,actualSeconds,weightKg,status |
| macros.csv | date,proteinG,carbsG,fatG,kcal,kcalIsAuto |
| body_entries.csv | date,weightKg,muscleKg,fatKg,fatPct |
| settings.csv | key,value |
| README.txt | the `readme` text + schemaVersion + exportedAt |

Rules: RFC 4180 quoting, `\n` line endings, empty cell = null, booleans `true/false`, timestamps ISO-8601 with offset.

---

## 7. Mock data (F-MOCK-1)

Generated relative to `today` (T). Deterministic seed so repeated generation yields the same dataset.

| Item | Specification |
|---|---|
| Time span | Day T−70 … T (10 weeks + today) |
| Goals | "Muscle Up": startDate T−70, deadline T+14 (= start + 84), ACTIVE, notes "Clean bar muscle-up, no kipping". "Dragon Flag (clean)": startDate T−63, deadline T+21, ACTIVE, notes "Full dragon flag, 3 reps". |
| Plan A "Pull & Bar" | weekdays {1,3,5}. Exercises: Pull-up 4×REPS 6 rest 120; Muscle-up negative 3×REPS 3 rest 150; Straight-bar dip transition 3×REPS 5 rest 120; Dip 3×REPS 8 rest 90; Explosive pull-up 3×MAX rest 120; Dead hang 2×SECONDS 30 rest 60. Linked to Muscle Up, sessionsNeeded 36. |
| Plan B "Core & Skills" | weekdays {2,4}. Exercises: Dragon flag negative 4×REPS 3 rest 120; Tuck dragon flag 3×REPS 5 rest 90; Hanging leg raise 3×REPS 8 rest 90; Hollow body hold 3×SECONDS 30 rest 60; Tuck front lever 4×SECONDS 10 rest 90; Triceps pushdown 3×REPS 12 weight 20 rest 60. Linked to Dragon Flag, sessionsNeeded 24. |
| Sessions | For every planned day in span (≤ T): session COMPLETED with probability 0.85 (seeded); never on T itself. ~26 Plan A + ~17 Plan B sessions. startedAt 18:00–19:30 local, durationSec 2700–4200. RPE 6–9. Notes on ~20 % ("felt strong", "shoulder tight"). |
| Progression | Linear over 10 weeks with ±1 noise: Pull-up actual 6→10; Muscle-up negative 3→5; Dip 8→12; Explosive pull-up MAX 4→8; Dead hang 30→50 s; Dragon flag negative 3→6; Tuck dragon flag 5→8; Hanging leg raise 8→12; Hollow hold 30→45 s; Tuck front lever 10→22 s; Triceps pushdown 12 reps, weight 20→25 kg. Last set of each exercise 1 rep/5 s lower with probability 0.4. ~5 % of sets SKIPPED. |
| Macros | Each day in span logged with probability 0.85. proteinG ~ N(150, 25) clamped 90–210; carbsG 180–320; fatG 55–95; kcal auto (kcalIsAuto=true) except ~10 % with manual kcal ±80. Weeks T−14…T−8 biased protein −20 g (so N-PROTEIN and the Δ card have something to show). |
| Body entries | Every Sunday in span plus T−1: weightKg 78.4→76.9 linear ±0.3; muscleKg 36.4→37.3; fatKg 14.2→12.5 (1 decimal). |
| Settings | proteinTargetG 150, all toggles on, 18:00, homePeriod ONE_WEEK. |

Expected check: with this dataset, every Home card for period k=0 and k=1 displays a value and a Δ (not "—") for both 1W and 2W.

---

## 8. Out of scope (v1)

| Item | Note |
|---|---|
| Direct AI connection / API / MCP | Export only; data model kept clean for future. |
| Per-meal diet diary, food database, barcode | Daily totals only. |
| Sub-goals / goal hierarchy | Explicitly dropped. |
| Cloud sync, accounts, multi-device | Local DB only; JSON backup is manual. |
| kcal target | Protein target only. |
| Extra body metrics (water, bone, visceral) | Weight, muscle, fat only. |
| Smart-scale / wearable integrations, Health Connect | Manual entry. |
| Custom period ranges on Home | 1W / 2W only. |
| Localization beyond English | — |
| Chart library, analytics SDKs, network permission | Not included. |

---

## 9. Traceability checklist (brief → spec)

| Brief section | Requirement | Feature / spec IDs |
|---|---|---|
| Product intent | All data in one place, exportable, AI-queryable, clean model | P-6, F-EXP-1..3, §3, §6 |
| Product intent | Simple, native, fast, dry, no bloat | P-1, P-2, P-3 |
| Product intent | shadcn-like minimal UI, mobile UX patterns | P-5, P-7 |
| Product intent | English UI | P-4 |
| 1 Calendar | Navigable month, past/future | F-CAL-1 |
| 1 Calendar | Day markers workout/macros/weight | F-CAL-2 |
| 1 Calendar | Day summary with sets/reps, macros, weight | F-CAL-3 |
| 1 Calendar | Add/edit macros, weight, log workout from day | F-CAL-4, F-WO-8 |
| 2 Goals | Example goals, name, notes, start date default today | F-GOAL-1, §7 |
| 2 Goals | Deadline default start + 12 weeks, editable | F-GOAL-1, F-GOAL-2, §3.1 |
| 2 Goals | Time progress bar + days left | F-GOAL-3, §4.6 |
| 2 Goals | States active/achieved/archived | F-GOAL-4 |
| 2 Goals | No sub-goals | F-GOAL-8, §8 |
| 2 Goals | Goal shows plans, weekdays, exercises | F-GOAL-5 |
| 2 Goals | Multiple active goals | F-GOAL-7 |
| 3 Plans | Name, ordered exercises, N sets with target | F-PLAN-1, F-PLAN-2, §3.5 |
| 3 Plans | Optional goal link; workout picks plan only | F-PLAN-3, F-WO-1 |
| 3 Plans | Scheduled weekdays → adherence/notifications | F-PLAN-4, §4.4, N-TRAIN |
| 3 Plans | Sessions needed + progress on goal detail and Home | F-PLAN-3, F-GOAL-6, F-HOME-6, §4.7 |
| 3 Plans | Target types reps / MAX / seconds | F-PLAN-2, §3.5, §3.7 |
| 3 Plans | Optional added weight per set | F-PLAN-2, F-WO-6 |
| 3 Plans | Rest time per exercise | F-PLAN-2, F-WO-9 |
| 4 Library | Small curated calisthenics library + few accessories | F-EX-1, §3.11 |
| 4 Library | Custom exercises (name, category, default type) | F-EX-2 |
| 5 Workout | Open → pick plan → start | F-WO-1 |
| 5 Workout | Step through sets, mark complete | F-WO-2 |
| 5 Workout | REPS default = target, editable | F-WO-3 |
| 5 Workout | MAX must type actual | F-WO-4 |
| 5 Workout | Seconds: isometric timer or type | F-WO-5 |
| 5 Workout | Weight editable per set | F-WO-6 |
| 5 Workout | Modify on the fly (skip/add set, swap/add exercise) | F-WO-7 |
| 5 Workout | Rest timer auto-start, skippable, +/− | F-WO-9 |
| 5 Workout | End: notes + RPE; saved with date, plan, duration | F-WO-10, §3.6 |
| 5 Workout | Survives backgrounding | F-WO-11 |
| 5 Workout | Log past workout manually (backdating) | F-WO-8 |
| 6 Nutrition | Daily totals only P/C/F/kcal | F-NUT-1, F-NUT-4, §3.8 |
| 6 Nutrition | kcal auto 4/4/9 | F-NUT-2 |
| 6 Nutrition | Protein target in settings, no kcal target | F-NUT-3, F-SET-1, §8 |
| 7 Body | Date, weight, muscle, fat only; fat % derived | F-BODY-1, F-BODY-2, §3.9 |
| 7 Body | Manual entry via new entry or notification | F-BODY-1, F-NOTIF-3 |
| 8 Home | Period 1W/2W, navigate back, compare previous | F-HOME-1, §4.1 |
| 8 Home | kcal + protein charts with target line | F-HOME-2 |
| 8 Home | Weight + muscle trend chart | F-HOME-3 |
| 8 Home | Insight cards: body Δ, reps/volume Δ, sessions, adherence, protein/kcal Δ | F-HOME-4, F-HOME-5, §4.2–4.5 |
| 8 Home | Goals overview with time bar + sessions progress | F-HOME-6 |
| 8 Home | Quick actions | F-HOME-7 |
| 9 Notifications | Train today / N days without training | F-NOTIF-1, N-TRAIN |
| 9 Notifications | Protein below usual/target | F-NOTIF-2, N-PROTEIN |
| 9 Notifications | Weight 1/2 weeks, tap opens entry | F-NOTIF-3, N-WEIGHT |
| 9 Notifications | Toggles in settings | F-NOTIF-4, F-SET-2 |
| 10 Settings | Protein target, toggles, daily check time | F-SET-1, F-SET-2, F-NOTIF-5 |
| 10 Settings | Export JSON + CSV via share/save | F-SET-3, F-EXP-1, F-EXP-2, §6 |
| 10 Settings | Import JSON backup | F-SET-4, F-EXP-3 |
| 10 Settings | Generate mock data | F-SET-5, F-MOCK-1, §7 |
| 10 Settings | Clear all data | F-SET-6 |
| Tech | Kotlin, Compose, Room, Nav, WorkManager, kotlinx.serialization, single module, minSdk 26, Canvas charts, system theme | P-2, P-5, F-HOME-2/3, F-NOTIF-5, F-WO-11, §8 (no chart lib) — see `02-architecture.md` (to be written) |
