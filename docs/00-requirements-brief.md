# Life Hub — Raw requirements brief (from the user, translated & consolidated)

Source: the user's two voice-style messages (Italian) + answers to clarification questions.
This is the input for the spec writer. Nothing here is negotiable unless marked "decision by Claude".

## Product intent
A native Android app to keep ALL personal fitness data in one place: gym/calisthenics activity,
goals, training plans, daily macros, body weight. Data must be exportable so an AI can query it
(future: connect an AI directly to the app — out of scope now, but keep the data model clean and
the export complete/self-describing).

Tone of the product: **simple, native, fast to use, dry, no bloat** ("non deve essere piena di merda").
UI: minimal, inspired by shadcn/ui (neutral palette, thin borders, rounded-md cards, clear typography),
follow best mobile UX patterns for forms and the workout flow.
UI language: **English**.

## 1. Calendar
- Navigable month calendar (past and future).
- Each day shows markers for: workout(s) logged, macros logged, weight logged.
- Tapping a day opens a daily summary: workouts done that day (with sets/reps), macro totals for the
  day, weight entry if any. From there the user can add/edit macros, weight, or log a workout.

## 2. Goals
- Example goals: "Muscle Up", "Dragon Flag (clean)".
- Fields: name, optional description/notes, **start date** (user picks with one tap; default today),
  **deadline / reference date**: pre-set by default to **start + 12 weeks**, editable.
- A **time progress bar**: NOT progress toward the goal, but how much of the time window
  (start → deadline) has elapsed. Show days left.
- Goal states: active / achieved / archived (decision by Claude: allow marking achieved).
- No sub-goals (explicitly dropped).
- Opening a goal shows the plans (schede) linked to it, which weekdays to train, and the exercises.
- Multiple goals can be active at the same time.

## 3. Training plans (schede)
- A plan has a name, ordered list of exercises; each exercise has N sets with a target.
- A plan can be **optionally** linked to a goal. Training is not forced "per goal": when starting a
  workout the user just picks the plan.
- Plan has scheduled **weekdays** (e.g. Mon/Wed/Fri) → used for "which days I should train" and
  adherence insights/notifications.
- Plan ↔ goal link may carry a **"sessions needed"** number: how many sessions with this plan the user
  estimates are needed to reach the goal. Show progress (completed sessions / sessions needed) on goal
  detail and on the home page.
- Exercise target types: **reps** (a number, or **MAX**) or **seconds** (isometric holds, e.g. front lever).
- Optional added weight/ballast (kg) per set (weighted pull-ups/dips, accessories).
- Rest time per exercise (seconds), used by the rest timer.

## 4. Exercise library
- Small, curated, calisthenics-focused: pull-ups variants, rings, bars (dips, muscle-up progressions),
  push-ups variants, core (dragon flag progressions, L-sit, hollow), isometrics (front lever, back
  lever, planche progressions), and a FEW gym accessories (e.g. triceps pushdown). Do NOT ship a huge DB.
- User can add custom exercises (name, category, default type reps/seconds).

## 5. Live workout flow (in the gym)
- Open app → pick plan → start session.
- Step through exercises/sets; for each set: mark complete. Feedback per set:
  - target reps number: default actual = target (one tap to confirm), editable if different.
  - target MAX: must type how many actually done.
  - seconds: integrated **isometric timer** (start/stop), or type actual seconds.
  - weight (kg) editable per set.
- Can modify the session on the fly (skip set, add set, swap/add exercise).
- **Rest timer** auto-starts after completing a set (duration from exercise config), skippable, +/-.
- End of session: optional **notes** and **RPE 1–10**. Session saved to calendar (date, plan, duration).
- Session survives app being backgrounded (persist in-progress state in DB).
- Can also log a past workout manually from the calendar (decision by Claude: allow backdating).

## 6. Nutrition (macros)
- Only a daily total (NOT per meal, NOT a diet diary): protein g, carbs g, fat g, kcal.
  kcal auto-computed from macros (4/4/9) if left empty.
- **Protein target** (g/day) in settings — used in charts and notifications. No kcal target.

## 7. Body weight
- Entry: date, **total weight kg, muscle mass kg, fat mass kg** — nothing else (no water, bone, etc.).
  Body-fat % can be derived for display.
- Entered manually from a smart scale reading, via "new entry" or from the reminder notification.

## 8. Home dashboard
- Period selector locked to **1 week** or **2 weeks** (no custom range), with navigation to previous
  periods. Insights compare the current period vs the **previous period of the same length**.
- Charts: daily **kcal** and **protein** (with protein target line) over the period; **weight and muscle
  mass** trend (same chart OK).
- Insight cards — concrete numbers, dashboard style, e.g.:
  - weight Δ, muscle mass Δ, fat mass Δ vs previous period
  - avg reps per set (or total volume) Δ vs previous period
  - sessions done vs previous period; consistency/adherence: planned sessions (from plan weekdays)
    vs done, better/worse than previous period
  - avg protein / avg kcal Δ
- Goals overview: active goals with time bar and plan session progress (done / needed).
- Quick actions: start workout, log macros, log weight.

## 9. Notifications (periodic, local)
- "You should probably train today" (today is a scheduled weekday of some plan and no session yet,
  or N days without training).
- "Last week you ate less protein than usual / below target".
- "You haven't logged your weight for 1 / 2 weeks — weigh yourself". Tapping opens weight entry.
- Toggles in settings.

## 10. Settings
- Protein target, notification toggles, (time of daily check — decision by Claude).
- **Export** all data: JSON (complete, self-describing, AI-friendly) + CSV, via Android share sheet / save.
- (Decision by Claude) Import JSON backup.
- **Generate mock data** (realistic ~2–3 months: goals Muscle Up & Dragon Flag, plans, sessions,
  macros, weights) to preview the app; and **clear all data**.

## Tech (decision by Claude)
Kotlin, Jetpack Compose (Material3 themed to look shadcn-like), Room, Navigation Compose, WorkManager
for notifications, kotlinx.serialization for export. Single-module app. minSdk 26, target/compile 35.
Charts drawn with Compose Canvas (no chart library). Theme follows system light/dark.
