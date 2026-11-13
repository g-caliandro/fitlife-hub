# UX pass v2 — approved by user: implement ALL items 1–23

References: Hevy / Strong / Fitbod (screensdesign.com), screensdesign workout-tracker article
(rest timer from confirmed set, previous performance, finish summary w/ unlogged-sets choice,
pre-session preview, large tap targets, screen awake).

## Buttons & icons (global)
1. Clear button hierarchy: ONE filled primary per screen; secondary actions = tonal buttons (muted bg)
   with leading icon + label, min height 48dp. No text-only links for actions anywhere.
2. Home quick actions as 3 tiles (icon above label): Workout / Macros / Weight.
3. Top-bar actions as icons: Edit → pencil, Today → calendar-today icon; top-bar "Save" moves to (9).
4. "All"/"See all" → consistent chip with chevron (›).
5. Bottom nav Material-3 style: filled icon when selected + pill indicator behind icon.
6. Home: remove the ⋮ overflow next to the gear (only Settings remains).
7. Empty states: big icon + one-line text + a button.

## Forms
8. BUG: "Workout saved" snackbar persists onto the next screen (e.g. Edit macros). Snackbars must be
   scoped to the screen / dismissed on navigation.
9. Save = full-width filled button docked at the bottom (thumb reach, above IME), not a top-bar pill.
   Applies to: macro edit, body edit, goal edit, plan editor, exercise edit, session edit/manual log.
10. Macros: "Copy yesterday" (or last logged day) action; kcal auto value shown clearly as a computed
    hint/chip ("Auto: 2,270 kcal") not as fake input text; typing overrides it.
11. Weight: prefilled with last entry values; ±0.1 steppers on weight/muscle/fat.
12. Save confirmation snackbar with Undo ("Macros saved · Undo") on return.

## Live workout
13. "Previous" column per set: what was done in the same set index of the same exercise last
    completed session (e.g. "6", "8×+10kg", "12 s"); "—" if none.
14. Big per-row check button on every set row: one tap completes the set with the prefilled value
    (MAX rows: tap focuses the dock input instead since a value is required). Pending "DONE" cell shows
    the prefilled value in muted color instead of "·".
15. Keep screen on while live workout screen is visible (FLAG_KEEP_SCREEN_ON / keepScreenOn).
16. Finish → summary sheet/screen before saving: duration, sets done/skipped, volume, holds, PRs,
    notes + RPE. If unlogged sets exist: "Finish anyway" / "Back to workout".
17. Pre-session preview on workout start: tapping a plan shows exercises list, sets count, estimated
    duration (sum of sets × (avg set time ~40 s + rest)), then Start.
18. Swipe-to-delete (SwipeToDismissBox) for a set row and for an exercise (with Undo), besides ⋮.

## Lists
19. Train: remove the 7 weekday chips from each plan card; show compact "Mon · Sat" text + colored
    day-type icon; per-card ▶ Start button (goes to preview of 17).
20. Goals list: unify with Home goal card — sessions done/needed is the primary bar; time = small
    text (+ optional thin secondary bar). One shared composable used by Home & Goals.
21. Calendar: one "+" (FAB or tonal button) for the selected day opening a small menu/sheet
    (Workout / Macros / Weight) instead of 3 text buttons at the bottom.

## Visual consistency
22. Typography pass: identical top-bar title style on all screens, consistent section header style
    and spacing (16dp page padding, 24dp between sections, 12dp inside cards), tabular numbers.
23. Dark mode pass on every screen (record dark screenshots of every tour step and fix contrast).

## UX v2 QA (final visual pass)

Evidence = `app/build/screens/` (`tour-*` light, `tour-dark-*` dark: `AppTourDarkTest` runs the same e2e tour with `-night` qualifiers).

| # | Status | Evidence / notes |
|---|--------|------------------|
| 1 | DONE | one filled primary per screen (`tour-13-train-plans`, `tour-17-workout-preview`); QA fixed: Settings Share/Save were ghost text -> tonal+icon (`tour-30-settings`), hold "Stop" was a 2nd primary -> tonal (`tour-22-live-seconds-hold-running`), rest-bar Skip -> tonal |
| 2 | DONE | `tour-03-home-1w-current` |
| 3 | DONE | pencil / Today icons: `tour-10-goal-detail`, `tour-07-calendar-current` |
| 4 | DONE | QA fixed Home "All" -> "See all" chip (`tour-29-home-final`, `ds-v2-light`) |
| 5 | DONE | pill + filled icon: any tab screen, `tour-dark-09-goals-list` |
| 6 | DONE | `tour-03-home-1w-current` (gear only) |
| 7 | DONE | `tour-01-home-empty`, `wp1-plans-empty`; QA added icons to the 6 remaining empty states (exercises, body list, session not found, link sheet, start screen, picker) |
| 8 | DONE | QA fixed properly: dismissal in a synchronous `OnDestinationChangedListener`; a bus message lives until the next navigation. `waitSnackGone` waits removed from the tour; `tour-28-body-entry` has no snackbar |
| 9 | DONE | `tour-27-macro-entry`, `tour-28-body-entry`, `tour-11-goal-edit`, `tour-15-plan-editor`; snackbar host is lifted above the docked bar |
| 10 | DONE | `tour-27-macro-entry` (Auto chip + Copy yesterday) |
| 11 | DONE | `tour-28-body-entry` |
| 12 | DONE | bus "X saved · Undo" (FormUndoTest) |
| 13 | DONE | `tour-18-live-first-set`; QA: tour now waits for history load (was capturing "—"), prev column uses body size |
| 14 | DONE | `tour-18-live-first-set`, `tour-19-live-rest-timer` |
| 15 | DONE | KeepScreenOn in live screen |
| 16 | DONE | `tour-24-live-finish-summary` |
| 17 | DONE | `tour-17-workout-preview` |
| 18 | DONE | SwipeToRemove on set rows / exercises (not visible in static shots) |
| 19 | DONE | `tour-13-train-plans`; QA: compact "Sat · 5 exercises" line; the 7 weekday chips were also removed from Plan detail and Goal detail plan cards (`tour-14-plan-detail`, `tour-10-goal-detail`) |
| 20 | DONE | `tour-03-home-1w-current` vs `tour-09-goals-list` (shared GoalCard) |
| 21 | DONE | `tour-08-calendar-prev-month-day-summary` ("+ Add to this day") |
| 22 | DONE | all screens use LhTopBar(LhType.title) + LhSectionHeader; QA: Train lazy list section gap normalised to 24dp (`topPadding`), Plan detail recent sessions in a card aligned to the 16dp grid, tnum added to calendar macros / protein caption |
| 23 | DONE | `tour-dark-01..30`; QA: goal time bar uses muted (was a stark white bar), chart "No data" no longer struck through by the grid line. Tonal bg, borders, day-type colors, chart bars, snackbars/PR banner checked in dark |

Also fixed: the rest dock said "Next: Set 3" while set 2 was next (it skipped the focused set).
