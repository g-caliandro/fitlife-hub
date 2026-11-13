# 05 — Final review

Reviewer pass over the whole app against `00-requirements-brief.md` (highest priority), `01-spec.md` (§9),
`04-spec-addendum-achievements.md`, `02/03` and the WP notes. Date: 2026-11-12.

## How it was verified

- **End-to-end tour test** `app/src/test/java/com/lifehub/app/e2e/AppTourTest.kt` (Robolectric, real `MainActivity`,
  real `LifeHubApp`/`AppContainer`, fresh Room DB). It generates mock data by tapping **Settings → Generate mock data**,
  then navigates only by clicks through 29 steps and asserts DB results (session saved with RPE + duration, macro kcal
  auto 4/4/9, body entry). Status: **PASS** (whole suite `assembleDebug :app:testDebugUnitTest` green).
  Run: `./gradlew :app:recordRoborazziDebug --offline -q --tests '*AppTourTest*'` → `app/build/screens/tour-XX-*.png`.
- Test tags added (only change to main code): `tab-<Tab>` (AppScaffold bottom bar), `day-<yyyy-mm-dd>` (Calendar
  DayCell), `live-list`, `set-<order>-<index>`, `dock-value` (LiveWorkoutScreen), `rpe-<n>` (FinishSheet),
  `macro-protein|carbs|fat|kcal` (MacroEditScreen), `body-weight|muscle|fat` (BodyEditScreen).
- Every tour screenshot was inspected; code read for all features, nav graph, notifications, export, domain formulas.

Screenshots referenced below: `tour-01-home-empty`, `02-settings-mock-generated`, `03-home-1w-current`,
`04-home-1w-previous`, `05-home-2w-previous`, `06-home-2w-current`, `07-calendar-current`,
`08-calendar-prev-month-day-summary`, `09-goals-list`, `10-goal-detail`, `11-goal-edit`, `12-achievements-log`,
`13-train-plans`, `14-plan-detail`, `15-plan-editor`, `16-workout-start`, `17-live-first-set`, `18-live-rest-timer`,
`19-live-max-input`, `20-live-max-pr-banner`, `21-live-seconds-hold-running`, `22-live-seconds-pr-banner`,
`23-live-finish-sheet`, `24-home-after-workout`, `25-calendar-today-with-workout`, `26-macro-entry`, `27-body-entry`,
`28-home-final`, `29-settings`.

Grep results: no `TODO`/`FIXME`/stub left in `src/main`; `PlaceholderScreen` (`core/ui/PlaceholderScreen.kt`, "Coming
soon") is dead code — no route uses it. All 19 routes in `LhNavHost.kt:91-110` have a real screen; every one is reachable
by UI (Body list via Home "History", Exercises via Train "Library", Session via calendar day / plan "Recent sessions",
Achievements via Goals trophy + Home "Recent achievements"). Back navigation works on every screen visited by the tour.

## 1. Traceability checklist

Legend: PASS = implemented and verified; PARTIAL = works but deviates/incomplete; FAIL = missing.

| # | Brief requirement | IDs | Status | Evidence |
|---|---|---|---|---|
| 1 | All data in one place, exportable, AI-friendly, clean model | P-6, F-EXP-1..3 | PASS | `export/JsonCodec.kt:19-90` (schema v2, readme, all entities incl. achievements, restSeconds, currentSetIndex), `CsvExporter.kt:29-51` (+achievements.csv); `ExportImportTest` round-trip |
| 2 | Simple, native, fast, dry | P-1..P-3 | PASS | Start/Log macros/Log weight are 1 tap from Home (`tour-03`); no network permission (`AndroidManifest.xml`) |
| 3 | shadcn-like minimal UI, mobile UX | P-5, P-7 | PARTIAL | Consistent neutral UI in all shots; layout polish issues P2-1..P2-6 |
| 4 | English UI | P-4 | PASS | all screens |
| 5 | Calendar navigable past/future | F-CAL-1 | PASS | `CalendarScreen.kt:100-126` pager ±2000 months, Today button; `tour-07`, `tour-08` |
| 6 | Day markers workout/macros/weight + legend | F-CAL-2 | PASS | `CalendarScreen.kt:167-185`; `tour-08`, `tour-25` |
| 7 | Day summary with sets/reps, macros, weight | F-CAL-3 | PASS | `CalendarScreen.kt:191-239`; `tour-08`, `tour-25` (skipped-set noise P2-3) |
| 8 | Add/edit macros, weight, log workout from day | F-CAL-4, F-WO-8 | PASS | `CalendarScreen.kt:231-237` → `WorkoutLogScreen.kt`, macro/body edit with date arg |
| 9 | Goal: name, notes, start date default today | F-GOAL-1 | PASS | `GoalEditScreen.kt:71`, `tour-11` |
| 10 | Deadline default start+84 d, editable, auto-recompute until edited | F-GOAL-1/2 | PASS | `GoalEditScreen.kt:52,102-106`; mock goals wrongly flagged custom (P2-7) |
| 11 | Time progress bar + days left (not achievement) | F-GOAL-3, §4.6 | PASS | `domain/GoalProgress.kt:142-146`; `tour-09`, `tour-10` ("Day 70 of 84 · 14 days left") |
| 12 | States active/achieved/archived | F-GOAL-4 | PASS | `GoalDetailScreen.kt:283-291`, `GoalsScreen.kt:101-105` |
| 13 | No sub-goals | F-GOAL-8 | PASS | no entity/UI |
| 14 | Goal shows plans, weekdays, exercises | F-GOAL-5 | PASS | `GoalDetailScreen.kt:314-343`; `tour-10` |
| 15 | Multiple active goals | F-GOAL-7 | PASS | `tour-09` |
| 16 | Plan: name, ordered exercises, N sets with target | F-PLAN-1/2 | PASS | `PlanEditScreen.kt`; `tour-15` (name truncation P2-5) |
| 17 | Optional goal link; workout picks plan only | F-PLAN-3, F-WO-1 | PASS | `PlanEditScreen.kt:311-322`, `tour-16` |
| 18 | Plan weekdays → adherence/notifications | F-PLAN-4, §4.4 | PASS | `Insights.kt:55-69`, `NotificationRules.kt:42-55` |
| 19 | Sessions needed: progress on goal detail AND home | F-GOAL-6, F-HOME-6, §4.7 | PASS | goal detail "27 / 36 sessions" `tour-10`; Home goal cards via `HomeViewModel.kt:100-102` + `GoalCard`; plan detail chip (truncated, P2-4) |
| 20 | Target types reps / MAX / seconds | F-PLAN-2 | PASS | `tour-15`, `tour-17..22` |
| 21 | Optional added weight per set | F-PLAN-2, F-WO-6 | PASS | weight stepper + "Apply weight to remaining" `LiveWorkoutScreen.kt:375-391` |
| 22 | Rest time per exercise | F-PLAN-2, F-WO-9 | PASS | copied to `SessionSet.restSeconds` (`SessionRepository.kt:146`) |
| 23 | Small curated library + custom exercises | F-EX-1/2/3 | PASS | `ExerciseSeed.kt`, `ExerciseEditScreen.kt:111-112` (in-use delete blocked), hide/unhide `ExercisesScreen.kt:106` |
| 24 | Open → pick plan → start | F-WO-1 | PASS | `tour-14` → `tour-16` → `tour-17`; Resume on Home + ActiveWorkoutBar |
| 25 | Step through sets, mark complete | F-WO-2 | PASS | `tour-17` (progress "x / 18 sets", prev/next) |
| 26 | REPS default = target, one tap | F-WO-3 | PASS | `WorkoutOps.prefill` `WorkoutOps.kt:346-355`; `tour-17` → `tour-18` |
| 27 | MAX must type actual | F-WO-4 | PASS | MAX prefill empty, Done disabled until valid; `tour-19` |
| 28 | Seconds: isometric timer or type | F-WO-5 | PASS | Start hold/Stop, beep at target `LiveWorkoutScreen.kt:128-135`; `tour-21`, `tour-22` |
| 29 | Modify on the fly (skip/add/remove set, add/swap exercise) | F-WO-7 | PASS | `LiveWorkoutViewModel.kt:149-211`, menus in `LiveWorkoutScreen.kt:303-320`, `SetRow.kt` |
| 30 | Rest timer auto-start, skip, ±15 s | F-WO-9 | PARTIAL | works in-app (`tour-18`, `tour-20`); **no alert when app is backgrounded/screen off** (P1-2); ToneGenerator leak (P2-12) |
| 31 | End: notes + RPE; saved with date, plan, duration | F-WO-10 | PASS | `tour-23`; asserted in test (rpe 8, durationSec, COMPLETED); `SessionRepository.kt:160-172` |
| 32 | Survives backgrounding / process death | F-WO-11 | PASS | sets/index/restEndsAt persisted on every action (`LiveWorkoutViewModel.kt:91-135`); holdStart in SavedStateHandle; only un-confirmed typed input is lost (P2-13) |
| 33 | Log past workout manually | F-WO-8 | PARTIAL | `WorkoutLogScreen.kt`, `SessionEdit.kt:131-152` — works, but no PR/milestone detection (P1-1); no Home entry point (P2-9) |
| 34 | Session view/edit/delete | F-WO-12/13 | PASS | `SessionScreen.kt`, `SessionEdit.kt`; discard confirm `LiveWorkoutScreen.kt:270-273` |
| 35 | Macros daily totals only P/C/F/kcal | F-NUT-1/4 | PASS | `MacroEditScreen.kt`, `tour-26` |
| 36 | kcal auto 4/4/9 if empty | F-NUT-2 | PASS | `MacroRepository.upsert`, `domain/Nutrition.kt`; asserted in test (2270, kcalIsAuto) |
| 37 | Protein target in settings, used in charts + notifications | F-NUT-3, F-SET-1 | PASS | target line `HomeScreen.kt:256-266`, tile caption; `NotificationRules.kt:62-75`; Settings `tour-29` |
| 38 | Body: date, weight, muscle, fat only; fat% derived | F-BODY-1/2 | PASS | `BodyEditScreen.kt`, `tour-27` (Body fat 15.9 %), asserted in test |
| 39 | Body history list / edit / delete | F-BODY-3 | PASS | `BodyListScreen.kt` via Home "History" |
| 40 | Weight entry from notification | F-NOTIF-3 | PASS | route `Routes.bodyEdit(today)` `NotificationRules.kt:86` → `MainActivity.readRoute` → `LhNavHost.kt:83-88` |
| 41 | Home 1W/2W locked, navigate back, compare previous | F-HOME-1, §4.1 | PASS | `domain/Period.kt:186-195`; tour asserts range text for 1W/2W current+previous (`tour-03..06`) |
| 42 | kcal + protein charts with target line | F-HOME-2 | PASS | `HomeScreen.kt:249-269` |
| 43 | Weight + muscle trend chart | F-HOME-3 | PASS | `HomeScreen.kt:271-289`; `tour-06` |
| 44 | Insight cards Δ body, reps/volume, sessions, adherence, protein/kcal | F-HOME-4/5, §4.2-4.5 | PASS | formulas match spec `domain/Insights.kt:21-128`; `tour-03`; zero-delta arrow (P2-8) |
| 45 | Goals overview on Home | F-HOME-6 | PASS | `HomeScreen.kt:126-138` |
| 46 | Quick actions | F-HOME-7 | PASS | `HomeScreen.kt:157-170` |
| 47 | Notif: train today / N days without training | F-NOTIF-1 | PASS | `NotificationRules.kt:42-55`, deep link `workout/start?planId=` |
| 48 | Notif: protein below usual/target | F-NOTIF-2 | PASS | `NotificationRules.kt:62-75`, deep link `home?period=1W&offset=1` |
| 49 | Notif: weight 1/2 weeks | F-NOTIF-3 | PASS | `NotificationRules.kt:77-87` |
| 50 | Toggles + daily check time | F-NOTIF-4/5, F-SET-2 | PASS | `SettingsScreen.kt:167-173`, `NotificationScheduler.kt:243-257` |
| 51 | Export JSON + CSV via share / save | F-SET-3 | PASS | `SettingsScreen.kt:176-177`, `SettingsViewModel.kt:96-139` |
| 52 | Import JSON backup | F-SET-4 | PASS | `SettingsViewModel.kt:141-180` (preview + confirm, transaction) |
| 53 | Generate mock data | F-SET-5, F-MOCK-1 | PASS | via Settings in tour (`tour-02`), every Home tile non-"—" (`tour-03..06`) |
| 54 | Clear all data | F-SET-6 | PASS | typed "DELETE" `SettingsScreen.kt:195` |
| 55 | About: version + schemaVersion | F-SET-7 | PARTIAL | shows "data schema v1" but export is v2 (P2-10) |
| 56 | Achievement entity, dedupe, export/import, clear | F-ACH-1 | PASS | `Entities.kt`, `JsonCodec.kt:85,142`, `BackupRepository.kt:311` |
| 57 | Detection rules (PR reps/hold/weight, perfect week/month, protein week, milestones, goal) | F-ACH-2 | PARTIAL | engine + backfill OK (`tour-12`); not run for manual/edited sessions (P1-1); "X hold hold" titles (P2-11) |
| 58 | In-workout PR banner + haptic | F-ACH-3 | PARTIAL | works (`tour-20`, `tour-22`), overlaps progress row (P2-2, known) |
| 59 | Achievements log screen, filters, detail, delete | F-ACH-4 | PASS | `AchievementsScreen.kt:109-225`; `tour-12`; reachable from Goals + Home |
| 60 | Motivational notifications, cap, tap → log | F-ACH-5 | PASS | `NotificationRules.kt:103-131`, `DailyCheckWorker.kt:168-180` |
| 61 | Mock achievements | F-ACH-6 | PASS | `tour-12` (84 items, perfect week/month, protein week) |
| 62 | Whole app navigable, no placeholders, back works | — | PASS | tour; only dead code `PlaceholderScreen` (P2-14) |

**Totals: 55 PASS · 7 PARTIAL · 0 FAIL.**

## 2. Findings

No P0 found: nothing blocks use; the full flow (mock → home → calendar → goals → plans → live workout → finish →
calendar → macros → body → settings) works end-to-end.

### P1 — incorrect behavior

**P1-1 Manual / edited sessions skip achievement detection (known).**
`feature/workout/SessionEdit.kt:151` (`save`) only calls `c.sessions.saveFull`.
Fix: after `saveFull`, in the same coroutine call `runCatching { c.achievements.onSessionFinished(session.id, now) }`
for both manual log and edit. For edits, first call a new `AchievementDao.deletePrsBySession(session.id)` path
(expose `AchievementRepository.resetSession(sessionId)` = `dao.deletePrsBySession`) so changed actuals do not leave stale
PRs, then re-run `onSessionFinished`. On `delete` it is already handled (`SessionRepository.kt:183`). Add a repo test:
manual session with reps above history ⇒ one PR_REPS row.

**P1-2 Rest timer gives no alert when the app is backgrounded or the screen is off.**
`LiveWorkoutScreen.kt:120-126` beeps/vibrates only while the composable is alive. Spec F-WO-9 and the brief's gym use
(phone in pocket) need it. Fix (no foreground service needed): add `notifications/RestAlarmReceiver.kt`
(BroadcastReceiver posting a high-importance notification on a new channel `Channels.REST` with vibration + default
sound, content intent `DeepLinks.pendingIntent(ctx, 900, Routes.WORKOUT_LIVE)`), register it in the manifest
(`exported=false`). In `LiveWorkoutViewModel` add `RestAlarm.schedule(app, endsAt)` wherever `restEndsAt` is set
(`done()` line ~131, `adjustRest`) and `RestAlarm.cancel(app)` in `skipRest`, `finish`, `discard`, and when rest is
replaced. Use `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, …)` when `canScheduleExactAlarms()` (API 31+) else
`setAndAllowWhileIdle`. In the receiver skip posting if the app is in the foreground (in-app beep already handles it,
e.g. check `ProcessLifecycleOwner` state or a static `LiveWorkoutVisible` flag set in `LiveWorkoutRoute` via
`DisposableEffect`).

### P2 — polish

- **P2-1 Home "today" goes stale across midnight.** `HomeViewModel.kt:82` computes `today` once per period/offset
  emission; also `CalendarViewModel`, `GoalsViewModel`, `GoalDetailViewModel`. Fix: expose `TimeSource.todayFlow()`
  (ticker emitting on date change, e.g. check every minute) and add it to the `combine` inputs.
- **P2-2 PR banner overlaps the progress row (known).** `LiveWorkoutScreen.kt:256` `padding(top = 60.dp)`. Fix: place the
  banner below the progress row: `padding(top = LhSize.topBar + 40.dp)` (top bar 56 + progress row ~36), or render it
  in place of the progress row while visible (`AnimatedContent`).
- **P2-3 Calendar day sheet: double horizontal padding and noisy skipped sets** (`tour-08`, `tour-25`).
  `CalendarScreen.kt:131` adds `.padding(horizontal = 16.dp)` although `LhBottomSheet` already pads 16 dp → remove it.
  In `DaySummaryContent` (`:205-209`) render only DONE sets via `fmtSet` and append `"· N skipped"` when any are SKIPPED;
  widen/let the exercise-name column wrap less (`width(120.dp)` → `weight(0.45f)`).
- **P2-4 Plan detail goal chips overflow** (`tour-14`: "Muscle Up (27/36)" cut). `PlanDetailScreen.kt:281-289`: replace
  the `Row` with `FlowRow(horizontalArrangement = spacedBy(6.dp), verticalArrangement = spacedBy(6.dp))`.
- **P2-5 Plan editor exercise names truncated without ellipsis** (`tour-15`: "Straight-bar dip"). `PlanEditScreen.kt:335`
  add `overflow = TextOverflow.Ellipsis` or `maxLines = 2`.
- **P2-6 Goal edit date fields truncate the year** (`tour-11`: "Thu 3 Sep 202"). `GoalEditScreen.kt:155-158`: stack the
  two `LhDatePickerField`s vertically (Column, fillMaxWidth) or use a short format without weekday in
  `LhDatePickerField`.
- **P2-7 Mock goals show "custom" deadline** although exactly 84 days (`tour-11`). `MockDataGenerator.kt:64-69`: pass
  `deadlineIsAuto = true` for "Muscle Up" (start+84) and keep false only where the deadline is not start+84.
- **P2-8 Zero/near-zero delta arrow (known).** `LhStatTile.kt:50-58` compares `delta == 0.0` on raw doubles, so e.g.
  37.3−37.2999… shows a coloured arrow with text "+0.0". Fix: decide colour/icon on the *displayed* value: add param
  `deltaDecimals: Int = 1` and use `val shown = round(delta * 10^dec) / 10^dec`; treat `shown == 0.0` as neutral
  (Remove icon, muted colour). Pass `0` for integer tiles in `HomeScreen.kt:197-245`.
- **P2-9 No Home entry point for manual log.** Spec F-WO-8 says "From calendar day or Home overflow". Add an overflow
  (`OverflowMenu`) in Home top bar next to Settings with "Log past workout" → `Routes.workoutLog(state.today)`
  (`HomeScreen.kt:99-101`).
- **P2-10 About shows "data schema v1".** `SettingsScreen.kt:184`: use `JsonCodec.SCHEMA_VERSION`.
- **P2-11 PR hold titles duplicate "hold"** (`tour-12`: "Hollow body hold hold: 43 s"). `AchievementEngine.kt:105`: drop
  the suffix when the name already ends with "hold" (`if (name.endsWith("hold", true)) "$name: $v s" else "$name hold: $v s"`).
  Existing rows keep old titles until "recalculate"; acceptable.
- **P2-12 ToneGenerator never released.** `LiveWorkoutScreen.kt:170-175`: keep one generator per screen
  (`remember { ToneGenerator(...) }` + `DisposableEffect { onDispose { release() } }`) or release after `ms + 50` via
  `Handler(Looper.getMainLooper()).postDelayed({ t.release() }, …)`.
- **P2-13 Typed but unconfirmed set input is lost on process death.** `LiveWorkoutViewModel.kt:59` `edits` is an in-memory
  `MutableStateFlow`; store it in `handle` (`handle.getStateFlow<String?>("editSet")` + value/weight strings) like
  `holdStart`. Also persist `focusedId` in `handle`.
- **P2-14 Dead placeholder code.** Delete `core/ui/PlaceholderScreen.kt` and its use in
  `test/.../screens/ShellScreenshotTest.kt`.
- **P2-15 Snackbars shown from `Dispatchers.Default`.** `LiveWorkoutScreen.kt:154` and `WorkoutLogScreen.kt:263` call
  `container.appScope.launch { snackbar.showSnackbar(…) }`; `appScope` is `Dispatchers.Default`. Use
  `appScope.launch(Dispatchers.Main) { … }` (or a main-scoped snackbar event bus).
- **P2-16 Settings protein field not refreshed after import.** `SettingsScreen.kt:150` `remember { mutableStateOf(...) }`
  → `remember(s.proteinTargetG) { … }`.
- **P2-17 Double-tap "Done" can complete a set twice / restart rest.** `LiveWorkoutViewModel.done()` reads `cur` before the
  DB round-trip. Add a `private var busy = false` guard (set at start, cleared at end of the launched block).
- **P2-18 Conflict dialog dismiss = Resume.** `WorkoutStartScreen.kt:171-176`: tapping outside navigates into the live
  workout. Use `onDismiss = onConflictDismiss` and keep "Resume" as an explicit button (needs a 3-action dialog or
  `LhConfirmDialog` with `dismissText="Resume"` wired to a separate callback).
- **P2-19 (optional, spec-conformant) Adherence counts today as missed before training.** §4.4 counts `d ≤ today`, so mid-day
  Home shows "1/4 · Worse than prev" (`tour-03`). If the user agrees, change `Insights.kt:63` to skip `d == today` unless
  a session exists that day.

**Counts: P0 = 0 · P1 = 2 · P2 = 19.**

## Fix log

- P1-1 FIXED: `SessionEdit.save` runs `resetSession` (edits) + `onSessionFinished` (idempotent via dedupeKey); delete already removed achievements. Test `manualSessionDetectsPrIdempotentlyAndEditResetsStale`.
- P1-2 FIXED: `notifications/RestAlarm.kt` (AlarmManager exact/inexact, receiver, `LiveWorkoutVisible` foreground guard, REST_DONE channel, tap opens live route); SCHEDULE_EXACT_ALARM + receiver in manifest; scheduled on set done/±15 s, cancelled on skip/finish/discard. Test `RestAlarmTest`.
- P2-1 FIXED (`TimeSource.todayFlow()` in Home, Calendar, Goals, GoalDetail). P2-2 FIXED. P2-3 FIXED. P2-4 FIXED (FlowRow). P2-5 FIXED. P2-6 FIXED (stacked date fields). P2-7 FIXED (both 84-day mock goals auto). P2-8 FIXED (`deltaDecimals`, neutral on rounded 0). P2-9 FIXED (Home overflow "Log past workout"). P2-10 FIXED. P2-11 FIXED. P2-12 FIXED (release after tone). P2-13 FIXED (edits/focus in SavedStateHandle). P2-14 FIXED (PlaceholderScreen deleted). P2-15 FIXED (Dispatchers.Main). P2-16 FIXED. P2-17 FIXED (busy guard). P2-18 FIXED (dismiss no longer resumes; explicit Resume button).
- P2-19 FIXED (lead decision): today is excluded from planned until a session for it exists (Insights.kt adherence).
