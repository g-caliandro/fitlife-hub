# Life Hub — UX & Visual Design

Version 1.0 · Pairs with `docs/02-architecture.md` (files, APIs, ownership) and `docs/01-spec.md` (feature IDs).

Tone: shadcn/ui "zinc" — neutral, flat, 1dp borders, no shadows, no gradients, no illustrations, no FABs. Numbers are the content; chrome is quiet. If in doubt, remove it.

---

## 1. Tokens (`core/designsystem/Theme.kt`, `Tokens.kt`)

### 1.1 Colors

`data class LhColors(...)` provided via `LocalLhColors`; access as `LhTheme.colors.x`. Also mapped into the M3 `ColorScheme` (mapping column) so stock M3 widgets (DatePicker, Switch, ModalBottomSheet, Snackbar) look right. Theme follows `isSystemInDarkTheme()`.

| Token | Light | Dark | M3 mapping |
|---|---|---|---|
| background | `#FFFFFF` | `#09090B` | background, surface, surfaceContainer* |
| foreground | `#09090B` | `#FAFAFA` | onBackground, onSurface |
| card | `#FFFFFF` | `#09090B` | — (cards = background + border) |
| muted | `#F4F4F5` | `#27272A` | surfaceVariant, secondaryContainer |
| mutedForeground | `#71717A` | `#A1A1AA` | onSurfaceVariant |
| border | `#E4E4E7` | `#27272A` | outline, outlineVariant |
| input | `#E4E4E7` | `#27272A` | — (field borders) |
| ring | `#A1A1AA` | `#D4D4D8` | — (focused field border) |
| primary | `#18181B` | `#FAFAFA` | primary |
| primaryForeground | `#FAFAFA` | `#18181B` | onPrimary |
| secondary | `#F4F4F5` | `#27272A` | secondary |
| secondaryForeground | `#18181B` | `#FAFAFA` | onSecondary |
| accent | `#2563EB` | `#3B82F6` | tertiary — the ONE accent: current set, live/active state, chart primary series, workout marker |
| accentForeground | `#FFFFFF` | `#FFFFFF` | onTertiary |
| accentMuted | `#EFF6FF` | `#172554` | tertiaryContainer — current-set row bg, active-workout bar bg |
| success | `#16A34A` | `#22C55E` | — positive delta, DONE check, macros marker, muscle series |
| successMuted | `#F0FDF4` | `#052E16` | — DONE row bg |
| warning | `#D97706` | `#F59E0B` | — weight marker, "overdue" |
| destructive | `#DC2626` | `#F87171` | error |
| destructiveForeground | `#FFFFFF` | `#09090B` | onError |

Rules: accent only for "live / current / primary series". Success/destructive only for semantic state. Everything else zinc.

### 1.2 Typography (`LhType`, default font family = system Roboto; numbers use `fontFeatureSettings = "tnum"`)

| Style | Size/Line | Weight | Use | M3 slot |
|---|---|---|---|---|
| title | 20/28 | SemiBold | top bar title | titleLarge |
| heading | 16/24 | SemiBold | card titles, section headers | titleMedium |
| body | 14/20 | Normal | default text | bodyMedium |
| bodyLarge | 16/24 | Normal | input text | bodyLarge |
| caption | 12/16 | Normal, mutedForeground | secondary text, axis labels | bodySmall |
| label | 14/20 | Medium | buttons, field labels | labelLarge |
| labelSmall | 12/16 | Medium | badges, chips, table headers | labelMedium |
| stat | 24/32 | SemiBold, tnum | stat tile value | headlineSmall |
| timer | 48/56 | SemiBold, tnum | rest / isometric timer, workout dock | displaySmall |

No uppercase transforms. Letter spacing 0 except `stat`/`timer` −0.5sp.

### 1.3 Shape, spacing, sizes

- Radius: `sm = 6.dp` (badges, chips), `md = 8.dp` (buttons, inputs, segmented control, list rows), `lg = 12.dp` (cards, sheets' top corners, dialogs), `full` (dots, progress bars).
- Borders: `1.dp` `border` color everywhere elevation would be used. `tonalElevation = 0`, `shadowElevation = 0` on every M3 surface.
- Spacing scale `LhSpacing`: `xs 4, sm 8, md 12, lg 16, xl 24, xxl 32` (dp). Screen horizontal gutter 16; card padding 16; gap between cards 12; gap label→field 6.
- Heights: button sm 32 / md 40 / lg 48; input 44; list row 56; set row 48; top bar 56; bottom bar 64. Min touch target 48 (pad with `minimumInteractiveComponentSize()`).
- Icons: Material Icons **Outlined**, 20dp in buttons/rows, 24dp in bars.

---

## 2. Components (`core/designsystem/components/`, one file each, owner WP0)

All take `modifier: Modifier = Modifier` as the first optional param. All have light/dark previews and appear in `DesignSystemScreenshotTest`.

```kotlin
// LhCard.kt — background + 1dp border + radius lg. Clickable when onClick != null (ripple clipped to shape).
@Composable fun LhCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit)

// LhButton.kt
enum class LhButtonVariant { Primary, Secondary, Outline, Ghost, Destructive }
enum class LhButtonSize { Sm, Md, Lg }
@Composable fun LhButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: LhButtonVariant = LhButtonVariant.Primary, size: LhButtonSize = LhButtonSize.Md,
    leadingIcon: ImageVector? = null, enabled: Boolean = true)
@Composable fun LhIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, variant: LhButtonVariant = LhButtonVariant.Ghost, enabled: Boolean = true)
```
Variants: Primary = primary bg / primaryForeground; Secondary = secondary bg; Outline = background + 1dp border; Ghost = transparent, muted bg on press; Destructive = destructive bg. Disabled = 50% alpha. Radius md, horizontal padding 16 (Sm 12), label style.

```kotlin
// LhTextField.kt — label above field (not floating), 1dp input border, focused border = ring 1.5dp, error border + caption = destructive.
@Composable fun LhTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, placeholder: String? = null, error: String? = null, supportingText: String? = null,
    singleLine: Boolean = true, minLines: Int = 1, maxLength: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
    keyboardActions: KeyboardActions = KeyboardActions.Default, enabled: Boolean = true)

// LhNumberField — String-backed; filters input. decimals=0 → digits only (KeyboardType.Number);
// decimals=1 → digits + one '.' (',' converted to '.'), max 1 fractional digit (KeyboardType.Decimal).
// suffix shown inside right edge in mutedForeground ("kg", "g", "s"). textAlign End when suffix present.
@Composable fun LhNumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, placeholder: String? = null, suffix: String? = null, decimals: Int = 0,
    error: String? = null, supportingText: String? = null, imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}, enabled: Boolean = true)

// LhStepper — [−] value [+] in one bordered md box, value tappable to type (inline LhNumberField).
@Composable fun LhStepper(value: String, onValueChange: (String) -> Unit, step: Double, modifier: Modifier = Modifier,
    label: String? = null, suffix: String? = null, decimals: Int = 0, min: Double = 0.0, max: Double = 999.0,
    placeholder: String? = null)

// LhBadge.kt — pill, radius sm, labelSmall, h 22, padding 8h.
enum class LhBadgeVariant { Default, Secondary, Outline, Accent, Success, Warning, Destructive }
@Composable fun LhBadge(text: String, modifier: Modifier = Modifier, variant: LhBadgeVariant = LhBadgeVariant.Secondary)

// LhProgressBar.kt — track muted, fill foreground (default) or color; height 6, radius full; fraction clamped 0..1.
@Composable fun LhProgressBar(fraction: Float, modifier: Modifier = Modifier,
    color: Color = LhTheme.colors.foreground, height: Dp = 6.dp)

// LhSegmentedControl.kt — muted bg container radius md, 3dp inner padding; selected segment = background + 1dp border + label foreground; others mutedForeground. Equal-width segments, h 36.
@Composable fun <T> LhSegmentedControl(options: List<T>, selected: T, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, label: (T) -> String)

// LhTopBar.kt — h 56, background, 1dp bottom border, title left (16dp gutter, or after back icon), actions right. No elevation, no color change on scroll.
@Composable fun LhTopBar(title: String, modifier: Modifier = Modifier, subtitle: String? = null,
    onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})

// LhEmptyState.kt — centered column: optional 32dp icon (mutedForeground), heading, caption, optional Outline button.
@Composable fun LhEmptyState(title: String, modifier: Modifier = Modifier, description: String? = null,
    icon: ImageVector? = null, actionText: String? = null, onAction: (() -> Unit)? = null)

// LhSectionHeader.kt — heading left, optional Ghost Sm action right ("See all", "History"). Top padding 24, bottom 8.
@Composable fun LhSectionHeader(title: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: (() -> Unit)? = null)

// LhStatTile.kt — LhCard(padding 12): caption label; stat value (+ unit in caption size); delta row: arrow icon (ArrowUpward/ArrowDownward/Remove, 14dp) + delta text + " vs prev".
enum class DeltaGood { Up, Down, Neutral }   // which direction is "good"
@Composable fun LhStatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null,
    delta: Double? = null, deltaText: String? = null, good: DeltaGood = DeltaGood.Up, caption: String? = null,
    onClick: (() -> Unit)? = null)
```
Delta color: `delta == null` → text "—" mutedForeground, no arrow; `delta == 0` → mutedForeground, `Remove` icon; sign matches `good` → success; opposite → destructive; `Neutral` → mutedForeground always. Used with: Weight `Neutral`, Muscle `Up`, Fat `Down`, Sessions `Up`, Adherence `Up`, Avg reps/set `Up`, Volume `Up`, Avg protein `Up`, Avg kcal `Neutral`.

```kotlin
// LhDatePickerField.kt — looks like a read-only LhTextField with CalendarToday trailing icon; ONE tap opens M3 DatePickerDialog
// (initial = date, selectableDates limited by min/max). Display "Wed 12 Nov 2026". Confirm sets date; Cancel no-op.
@Composable fun LhDatePickerField(date: LocalDate, onDateChange: (LocalDate) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, minDate: LocalDate? = null, maxDate: LocalDate? = null, error: String? = null)
// Same file: LhTimePickerField(time: LocalTime, onTimeChange, label) → M3 TimePicker in an LhDialog, 24h per system setting.
@Composable fun LhTimePickerField(time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier, label: String? = null)

// LhBottomSheet.kt — ModalBottomSheet(skipPartiallyExpanded = true), containerColor background, top radius lg, small drag handle (32×4 muted),
// optional title row (heading + close icon). Content gets 16dp horizontal padding and navigationBarsPadding + imePadding.
@Composable fun LhBottomSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit)

// LhDialog.kt — AlertDialog restyled: background, 1dp border, radius lg, heading title, body text, buttons right-aligned (Outline cancel, Primary/Destructive confirm).
@Composable fun LhConfirmDialog(title: String, message: String, confirmText: String, onConfirm: () -> Unit,
    onDismiss: () -> Unit, destructive: Boolean = true, dismissText: String = "Cancel")
// Confirm enabled only when typed text == requiredText (case-sensitive). Used for Clear all data.
@Composable fun LhTypedConfirmDialog(title: String, message: String, requiredText: String, confirmText: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit)

// LhChip.kt — radius sm, h 32, Outline when unselected, primary bg/fg when selected.
@Composable fun LhChip(text: String, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier)

// LhListRow.kt — h ≥ 56, 16dp padding, title body + optional caption subtitle, optional leading icon, trailing slot; 1dp divider below unless last.
@Composable fun LhListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null, showDivider: Boolean = true, trailing: @Composable (() -> Unit)? = null)
// Same file: LhSwitchRow(title, subtitle, checked, onCheckedChange, enabled) — M3 Switch themed (checked track primary, thumb primaryForeground).
```

Shared UI in `core/ui/` (WP0):
```kotlin
// WeekdayChips.kt — 7 square 36dp chips "M T W T F S S" (ISO 1..7). onToggle null = read-only (selected = primary, others muted text).
@Composable fun WeekdayChips(selected: Set<Int>, onToggle: ((Int) -> Unit)?, modifier: Modifier = Modifier, highlightToday: Int? = null)

// GoalCard.kt — see §4.3.
data class GoalCardModel(val id: String, val name: String, val state: GoalState, val time: TimeProgress, val plans: List<PlanProgress>)
@Composable fun GoalCard(model: GoalCardModel, onClick: () -> Unit, modifier: Modifier = Modifier)

// ExercisePickerSheet.kt — see §4.10.
@Composable fun ExercisePickerSheet(exercises: List<Exercise>, onPick: (Exercise) -> Unit, onDismiss: () -> Unit,
    onCreateCustom: ((String) -> Unit)? = null, title: String = "Add exercise")
```

Format helpers (`core/designsystem/Format.kt`): `fmtKg(Double?) = "76.9"` (1 decimal, "—" if null); `fmtDelta(Double?, decimals, unit)` → `"+0.6 kg"`, `"−0.6 kg"` (U+2212), `"0.0 kg"`, `"—"`; `fmtInt(Int?)` → `"2,450"` or "—"; `fmtDate(LocalDate)` → `"Wed 12 Nov"` (+ `" 2025"` if not current year); `fmtRange(DateRange)` → `"Mon 3 Nov – Sun 9 Nov"`; `fmtDuration(sec)` → `"52 min"` / `"1 h 05 min"`; `fmtClock(sec)` → `"1:23"` / `"1:02:10"`; `fmtTarget(type, value)` → `"6"`, `"MAX"`, `"30 s"`; `fmtSet(set)` → `"6 × 20 kg"`, `"6"`, `"30 s"`, `"skipped"`.

---

## 3. Navigation structure

```
┌───────────────────────────────┐
│           (screen)            │
├───────────────────────────────┤
│ ● Pull & Bar · 32:10  Resume ›│  ActiveWorkoutBar — only when IN_PROGRESS; accentMuted bg, accent text, h 48
├───────────────────────────────┤
│  ⌂ Home  ▦ Calendar  ⚡ Train  ◎ Goals │  bottom bar, h 64, 1dp top border
└───────────────────────────────┘
```
- **4 tabs**: Home (`Icons.Outlined.Home`), Calendar (`CalendarMonth`), Train (`FitnessCenter`, route `plans`), Goals (`Flag`). Selected = foreground icon + label; unselected = mutedForeground; no pill indicator (set `indicatorColor = Transparent`).
- **Settings**: gear icon in Home top bar (rarely used → not a tab). Body history: "History" action on Home body chart + top-bar action in Body entry.
- **Start workout** lives in: Home quick actions (primary button), Train tab header (primary button), ActiveWorkoutBar (resume). Workout screens hide the bottom bar.
- **No FABs.** "Add" = `+` icon in top bar (Goals, Train, Exercises, Body history).
- Back: system back + top-bar back arrow on non-tab screens. Live workout back = leave screen (session stays IN_PROGRESS, bar appears).

---

## 4. Screens

Wireframe legend: `[Btn]` primary, `(Btn)` outline/secondary, `‹ ›` icon buttons, `▕▇▇▇░░▏` progress bar, `…` = repeats. Width ≈ 360dp, 16dp gutter.

### 4.1 Home (`feature/home`, F-HOME-1..7)

```
Life Hub                                   ⚙
[▶ Start workout          ] (Macros) (Weight)     ← Row: Start = weight 1f Primary Md; Macros/Weight Outline Md with icons
                                                    IN_PROGRESS ⇒ "Resume workout" (accent bg)
‹  Mon 3 Nov – Sun 9 Nov  ›          [1W|2W]       ← › disabled at offset 0; tap range = jump to current
┌Sessions──────┐ ┌Adherence─────┐
│ 3            │ │ 3/3  100%    │
│ ↑ +1 vs prev │ │ ↑ better     │                  ← +x pts in caption
├Weight────────┤ ├Muscle────────┤
│ 76.9 kg      │ │ 37.3 kg      │
│ −0.3 kg      │ │ ↑ +0.1 kg    │
├Fat───────────┤ ├Avg protein───┤
│ 12.5 kg 16.3%│ │ 148 g        │                  ← protein caption "4/6 days ≥ 150 g"
│ ↓ −0.2 kg    │ │ ↑ +12 g      │
├Avg kcal──────┤ ├Avg reps/set──┤
│ 2,410        │ │ 7.4          │
│ +80          │ │ ↑ +0.3       │
└──────────────┘ └──────────────┘
┌Volume─────────────────────────┐                  ← full width; caption "Holds 6:20 (+0:40)"
│ 412 reps        ↑ +36 vs prev │
└───────────────────────────────┘
Nutrition                         [kcal|Protein]   ← LhSectionHeader + small segmented
┌ bar chart 160dp ──────────────┐
└───────────────────────────────┘
Body                                  History ›
┌ line chart 160dp (weight, muscle) ┐
└───────────────────────────────────┘
Goals                                   All ›
GoalCard …  (empty: "No active goals" + (Add goal))
```
- `LazyColumn`, 16 gutter, tiles in 2-column rows with 12dp gaps.
- Data loads instantly from DB; show tiles with "—" rather than spinners.
- Tiles not clickable (keep dry), except Weight/Muscle/Fat → Body history.

### 4.2 Calendar (`feature/calendar`, F-CAL-1..4)

```
Calendar                                (Today)
‹        November 2026        ›
 M   T   W   T   F   S   S                       ← caption, mutedForeground
┌───┬───┬───┬───┬───┬───┬───┐
│27 │28 │29 │30 │31 │ 1 │ 2 │                     ← out-of-month days mutedForeground @ 50%
│   │•▪ │   │•▪◦│   │ ▪ │▪◦ │                     ← markers row under number
├───┼───┼───┼───┼───┼───┼───┤
│ …                         │
│12̲ │   (today: 1.5dp foreground ring, radius md)
└───────────────────────────┘
● Workout   ■ Macros   ○ Weight                  ← legend, caption
```
- Cell: square-ish, h 52, number top-center (body), markers 6dp row bottom-center, 3dp gap: workout = filled circle accent, macros = filled square (radius 1dp) success, weight = 1.5dp ring warning. Shape + color so it works in grayscale.
- Horizontal swipe on grid (`HorizontalPager` with large virtual page count, start page = current month) + chevrons. "Today" Ghost button in top bar.
- Tap day → **Day sheet** (`LhBottomSheet`, title = `fmtDate` with weekday, e.g. "Wed 12 Nov"):

```
Wed 12 Nov                                   ✕
Workouts
┌ Pull & Bar · 52 min · RPE 8          › ┐   ← tap → session/{id}
│ Pull-up        6, 6, 7, 6                │   ← fmtSet joined; weighted "6×10 kg"
│ Dead hang      30 s, 35 s                │
└──────────────────────────────────────────┘
  (none: caption "—")
Macros                                  Edit  ← Edit only if exists
 P 152 g · C 240 g · F 70 g · 2,198 kcal       (or "—")
Body                                    Edit
 76.9 kg · muscle 37.3 · fat 12.5 (16.3%)      (or "—")
(Log workout) (Log macros) (Log weight)        ← 3 Outline Sm buttons, wrap Row; Log workout hidden for future dates
```

### 4.3 GoalCard (shared) & Goals list (`feature/goals`, F-GOAL-3,4,6,7)

```
┌ Muscle Up                        [Achieved]? ┐ ← badge only when not ACTIVE
│ ▕▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇░░░░▏                       │ ← time bar: foreground fill; warning fill when overdue
│ 14 days left · ends 26 Nov                    │ ← caption; "Overdue by 3 days" in warning
│ Pull & Bar   ▕▇▇▇▇▇▇░░░▏  26 / 36             │ ← per linked plan, bar h 4, success fill; no needed → "26 sessions"
└───────────────────────────────────────────────┘
```
Goals screen: top bar "Goals" + `+`. `LhSegmentedControl` Active · Achieved · Archived. List of GoalCards (12 gap). Empty: "No active goals" / "Set a time window for a skill you're chasing." + (Add goal).

### 4.4 Goal detail (F-GOAL-3..6)

```
← Muscle Up                               ⋮      ← ⋮: Edit, Mark achieved | Reactivate, Archive, Delete
ACTIVE badge · 3 Sep → 26 Nov
▕▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇░░░░▏
Day 70 of 84 · 14 days left
Notes text (body, mutedForeground), if any
Plans                                  + Link plan   ← opens sheet: plan list (not yet linked) → sessionsNeeded field → Link
┌ Pull & Bar                            ⋮ ┐          ← ⋮: Edit sessions needed, Unlink, Open plan
│ M [T] W [T] F  S  S   (scheduled = primary chips) │
│ ▕▇▇▇▇▇▇░░░▏ 26 / 36 sessions                     │
│ Pull-up              4 × 6                        │
│ Muscle-up negative   3 × 3                        │
│ Explosive pull-up    3 × MAX                      │
│ Dead hang            2 × 30 s                     │
└───────────────────────────────────────────────────┘   ← tap card → plan/{id}
(empty: "No plans linked" + (Create plan) → plan/edit?goalId=)
```
Delete: `LhConfirmDialog("Delete goal?", "Linked plans are kept.", "Delete")` → back.

### 4.5 Goal edit (F-GOAL-1,2)

```
← New goal                                  [Save]   ← Save = Primary Sm in top bar actions
Name
[ Muscle Up                       ]   0/60 counter caption right
Notes
[                                  ]  minLines 3
Start date                Deadline
[ Wed 12 Nov 2026  📅 ]   [ Wed 4 Feb 2027  📅 ]     ← two columns
caption: "12 weeks" (live: weeks+days between dates)
```
- Deadline auto = start + 84 until the user picks a deadline (then caption shows "custom").
- Errors: name empty → "Required"; deadline ≤ start → "Must be after start date".
- Name field auto-focused on new goal only.

### 4.6 Train tab = Plans list (`feature/plans`, F-PLAN-1..5)

```
Train                                    +
[▶ Start workout                          ]          ← Primary Lg full width → workout/start
Plans
┌ Pull & Bar                [Today]      › ┐          ← Today badge (Accent) if today ∈ weekdays
│ M [T] W [T] F S S  ·  6 exercises        │
│ Muscle Up                                │          ← linked goal names, caption
└──────────────────────────────────────────┘
…
Archived (2)  ⌄                                         ← collapsed section
Library
Exercises                                 ›            ← LhListRow → exercises
```
Empty plans: "No plans yet" + "A plan is an ordered list of exercises you repeat." + (Create plan).

### 4.7 Plan detail (`plan/{id}`)

```
← Pull & Bar                                 ⋮      ← ⋮: Edit, Duplicate, Archive|Unarchive, Delete
M [T] W [T] F S S
Goals: Muscle Up (26/36)                              ← caption chips; tap → goal
[▶ Start this plan                         ]          ← → workout/start?planId=
Exercises
 1  Pull-up                4 × 6          rest 2:00
 2  Muscle-up negative     3 × 3          rest 2:30
 3  Dip                    3 × 8 +10 kg   rest 1:30
 4  Explosive pull-up      3 × MAX        rest 2:00
 5  Dead hang              2 × 30 s       rest 1:00
Recent sessions                                        ← last 5, LhListRow "Wed 12 Nov · 52 min" → session
```
Delete: confirm "Delete plan? Logged sessions are kept."

### 4.8 Plan editor (`plan/edit`, F-PLAN-1..3)

```
← Edit plan                                  [Save]
Name
[ Pull & Bar                        ]
Days
M [T] W [T] F S S                                    ← WeekdayChips toggle
Exercises
┌ 1  Pull-up                         ↑ ↓ ✕ ┐        ← ↑/↓ LhIconButton (disabled at ends), ✕ remove (undo snackbar)
│ Sets           Target                       │
│ [− 4 +]        [ REPS | MAX | SEC ]          │        ← LhStepper 1..20, LhSegmentedControl
│ Reps      Weight       Rest                   │
│ [ 6 ]     [ 0.0  kg ]  [ 120  s ]             │        ← "Reps"→"Seconds" for SEC; hidden for MAX (replaced by caption "As many as possible")
└──────────────────────────────────────────────┘
…
(+ Add exercise)                                      ← Outline full width → ExercisePickerSheet; new row uses defaultTargetType, sets 3, value 8 (REPS) / 30 (SEC), rest 90
Linked goals
☐ Muscle Up            Sessions needed [ 36 ]        ← field enabled only when checked; ACTIVE goals only
☐ Dragon Flag (clean)  Sessions needed [    ]
Notes
[                                  ]
```
- Validation on Save: name required; ≥ 1 exercise ("Add at least one exercise"); REPS/SEC value ≥ 1; weight ≥ 0; rest 0–600; sessionsNeeded 1–500 or empty. First invalid card scrolls into view; errors under fields.
- Weight empty = no added weight (null).

### 4.9 Exercises library (`exercises`, F-EX-1..3) and edit

List grouped by category (LhSectionHeader per category), rows: name + caption "Reps"/"Seconds" + `[Custom]` badge; hidden built-ins at 50% alpha with "Hidden" badge. Row tap: custom → edit; built-in → sheet with Hide/Unhide. Search field at top filters live.
Edit form: Name, Category (LhChip row, wrap), Default type `[Reps|Seconds]`, Save; Delete (Destructive Outline at bottom) for custom → confirm; if in use → snackbar "Used in plans or sessions — can't delete. Hide it instead." Duplicate name → field error "Already exists".

### 4.10 Exercise picker sheet (shared, F-EX-3)

```
Add exercise                                   ✕
[ 🔍 Search exercises            ]            ← auto-focus? NO (keyboard covers list); tap to search
(All) (Pull) (Push) (Core) (Legs)   ← LhChips, single-select
Pull-up                         Reps
Chin-up                         Reps
…
+ Create "Muscle-up kip"                       ← only when query non-empty and no exact match and onCreate != null; opens the inline
                                                 "New exercise" form (name, category, Reps/Seconds), Save returns to the list
Empty library: "No exercises yet" + [Add your first exercise] → same inline form
```
Filters `!isHidden`, sorted by name; search = case-insensitive contains on name. Tap row = pick + dismiss. Sheet height = full (skipPartiallyExpanded).

### 4.11 Workout start (`workout/start`, F-WO-1)

```
← Start workout
Today · Wed
┌ Pull & Bar                  [Today]   ┐
│ M [T] W [T] F S S · 6 exercises · ~21 sets │
│                              [▶ Start]     │      ← Primary Sm; preselected planId card gets 1.5dp accent border
└────────────────────────────────────────────┘
Other plans
┌ Core & Skills …            (▶ Start)  ┐         ← Outline Sm
```
- Today-scheduled plans first, then others alphabetical; archived excluded.
- If a session is IN_PROGRESS on entry → navigate `replace:workout/live`. If one appears when tapping Start (race) → `LhConfirmDialog("Workout in progress", "Resume it or discard it and start <plan>?", confirm "Discard & start", dismiss "Resume")`.
- First start ever: request POST_NOTIFICATIONS (rest alerts) before navigating.
- Empty: "No plans" + (Create plan).

### 4.12 Live workout (`workout/live`, F-WO-2..12) — the critical screen

Layout: top bar, progress strip, scrolling exercise list, **bottom dock** (current set input or rest timer). One input location (the dock) keeps it one-handed and avoids per-row text fields.

```
← Pull & Bar                    32:10   [Finish]  ⋮     ← elapsed since startedAt (fmtClock, ticks 1 s); ⋮ = Discard workout
▕▇▇▇▇▇▇▇▇▇░░░░░░░░░░░▏ 9 / 21 sets                       ← done+skipped / total; bar foreground h 4
───────────────────────────────────────────────
Pull-up                                  4 × 6   ⋮      ← heading; ⋮: Add set, Swap exercise
 SET   TARGET    KG       DONE                           ← labelSmall mutedForeground, column header
  1     6         –        6       ✓                     ← DONE row: successMuted bg, ✓ success
  2     6         –        6       ✓
▌ 3     6         –        ·                             ← CURRENT: accentMuted bg + 3dp accent left bar
  4     6         –        ·
 + Add set                                               ← Ghost Sm
Muscle-up negative                       3 × 3   ⋮
  1     3         –        —   skipped                   ← SKIPPED: mutedForeground, strikethrough target
  …
Explosive pull-up                       3 × MAX  ⋮
  1    MAX        –        ·
Dead hang                               2 × 30 s ⋮
  1    30 s       –        ·
(+ Add exercise)                                          ← Outline full width → picker → inline sheet: sets, type, value, weight
                                                            (bottom spacer = dock height)
┌─────────────────────────────────────────────────┐
│ Pull-up · Set 3 of 4              ‹    ›          │    ← ‹ › move focus prev/next set (any status)
│ Weight              Reps                           │
│ [ −   +0 kg   + ]   [ −    6    + ]                │    ← LhStepper: weight step 0.5 (empty = bodyweight "–"), reps step 1
│ [              ✓  Done                          ]  │    ← Primary Lg, full width
│  Skip set              Apply weight to remaining   │    ← Ghost Sm ×2 (apply only if weight changed)
└─────────────────────────────────────────────────┘
```
Dock variants by target type:
- **REPS**: reps prefilled with target → one tap Done (F-WO-3).
- **MAX**: reps field empty, placeholder "max reps"; Done disabled until a value is entered; stepper `+` from empty sets 1 (F-WO-4).
- **SECONDS**: replaces reps stepper with stopwatch block:
  ```
  │        0:18 / 0:30                      │  timer style; turns success color at ≥ target (vibrate + beep once)
  │ [ ▶ Start hold ]    Seconds [ −  18  + ]│  Start ↔ Stop (Outline Lg); Stop writes elapsed into Seconds (step 5)
  │ [              ✓  Done              ]  │
  ```
- Focused set already DONE: button reads "Update" (Outline), values prefilled with actuals.
- After Done: haptic `LongPress`, row animates to DONE, focus auto-advances to next PENDING, dock switches to **rest** (unless no PENDING left):
  ```
  │ Rest                         Next: Pull-up · Set 4 │
  │                 1:23                               │  timer style, counts down from restEndsAt
  │ ▕▇▇▇▇▇▇▇▇▇▇░░░░░░▏                                │  remaining / total, accent fill
  │ (−15 s)        (+15 s)          [ Skip rest ]      │
  ```
  At 0 → dock returns to input (service handles vibration/sound). While resting, tapping a row still focuses it (rest keeps running in a slim 40dp strip above the input: "Rest 1:23 · Skip").
- All sets done/skipped: dock shows "All sets done" + [Finish workout] (Primary Lg).
- Row long-press → dropdown: Skip / Unskip, Remove set (undo snackbar 5 s), Edit (focus).
- Swap exercise → picker → replaces only PENDING sets (caption in sheet: "Only remaining sets are changed").
- Leaving screen: no dialog; session persists.
- Discard: `LhConfirmDialog("Discard workout?", "All sets logged in this session will be deleted.", "Discard")`.

**Finish sheet** (F-WO-10):
```
Finish workout                                ✕
18 of 21 sets · 52 min
⚠ 3 sets not done will be saved as skipped       ← caption warning, only if pending > 0
How hard was it? (RPE, optional)
(1)(2)(3)(4)(5)
(6)(7)(8)(9)(10)                                  ← LhChip toggle, tap again to clear
Notes
[                                    ]           ← minLines 3, optional
[ Save workout                         ]          ← Primary Lg → navigate to Home tab (pop workout screens), snackbar "Workout saved"
```

### 4.13 Session detail / edit (`session/{id}`, F-WO-13) & manual log (`workout/log`, F-WO-8)

Same layout, one screen component `SessionEditScreen(mode)`:
```
← Pull & Bar                               ⋮        ← ⋮ Delete (confirm). Manual mode: no ⋮
Date [ Wed 12 Nov 2026 📅 ]  (max today)
Start [ 18:30 ]  Duration [ 52 ] min                ← manual: optional; detail: start read-only, duration editable
RPE (1)…(10)      Notes [            ]
Pull-up
  1   6   [ 0.0 kg ] [ 6  ] ☑                       ← inline LhNumberFields here (not live mode): weight, actual; ☑ = DONE/SKIPPED toggle
  …
[ Save ]                                             ← top bar Save
```
Manual flow: if no plan chosen → first a plan list (like 4.11, button "Select"). Sets prefilled DONE with actual = target; MAX rows empty → saved SKIPPED if left empty (caption hint on the row: "empty = skipped").

### 4.14 Macro entry (`macros/edit`, F-NUT-1,2)

```
← Log macros                                 [Save]
Date [ Wed 12 Nov 2026 📅 ]
Protein          Carbs           Fat
[ 150    g ]     [ 240    g ]    [ 70     g ]       ← 3 equal columns, LhNumberField ints; Protein auto-focused (keyboard up on open)
kcal
[ 2,190 (auto)  kcal ]                              ← empty: placeholder shows live 4P+4C+9F; typing overrides
caption: "Auto: 4·P + 4·C + 9·F" / "Manual — clear to auto-calculate"
Protein target 150 g · today 150 ✓                  ← caption, success when ≥ target
                                                       Existing date: fields prefilled, top bar shows "Edit macros", ⋮ Delete (confirm)
```
ImeAction Next P→C→F→kcal; Done on kcal = Save. Error: all empty/zero → "Enter at least one macro". Changing date to one with existing data loads it (caption "Editing existing entry").

### 4.15 Body entry (`body/edit`, F-BODY-1,2) & history (`body`, F-BODY-3)

```
← Log weight                    History   [Save]
Date [ Wed 12 Nov 2026 📅 ]
Weight
[ 76.9               kg ]                           ← decimals=1, auto-focus, required 20–300
Muscle mass          Fat mass
[ 37.3   kg ]        [ 12.5   kg ]                  ← optional, 0–weight
Body fat 16.3 %                                     ← caption, live; "—" if fat empty
Last: 77.2 kg on Sun 2 Nov                          ← caption, helps sanity-check
```
Errors: "Between 20 and 300 kg", "Can't exceed weight". History list: LhListRow title "76.9 kg", subtitle "Wed 12 Nov · M 37.3 · F 12.5 (16.3%)", tap → edit, long-press → confirm delete. Empty: "No weigh-ins yet" + (Log weight).

### 4.16 Settings (`settings`, F-SET-1..7, F-NOTIF-4,5)

```
← Settings
Nutrition
 Protein target              [ 150  g ]           ← LhNumberField saves on change (debounced 500 ms), 0–500
Notifications
 ⚠ Notifications are blocked. (Open settings)       ← only if denied (API 33+)
 Training reminders                       ( ●)     ← LhSwitchRow, subtitle "On plan days, or after 3 days off"
 Protein check                            ( ●)     ← "Mondays, if last week was low"
 Weigh-in reminder                        ( ●)     ← "After 1 week without a weigh-in"
 Daily check time                        18:00 ›   ← LhTimePickerField
Data
 Export JSON                         Share | Save   ← LhListRow with two Ghost Sm buttons
 Export CSV (zip)                    Share | Save
 Import JSON backup                             ›   ← picker → dialog "Replace all data? 2 goals, 2 plans, 43 sessions, 60 macro days, 11 weigh-ins." (Replace = Destructive)
 Generate mock data                             ›   ← confirm "Replace all data with ~10 weeks of sample data?" if non-empty
 Clear all data                                 ›   ← destructive text; LhTypedConfirmDialog requiredText "DELETE"
About
 Life Hub 0.1.0 · data schema v1                    ← caption
```
Long ops (export/import/mock) show a 16dp `CircularProgressIndicator` in the row trailing slot and disable the row; result via snackbar ("Exported", "Imported 118 items", "Import failed: <reason>").

---

## 5. Interaction rules

- **Keyboards**: ints → `KeyboardType.Number`; kg → `KeyboardType.Decimal`; names → `Text` + `Sentences`; `ImeAction.Next` through a form, `Done` on last field (hides keyboard; never auto-saves except Macro kcal → Save). Screens use `imePadding()`; Save stays in the top bar so it's never hidden by the keyboard.
- **Auto-focus** only on: Macro entry (Protein), Body entry (Weight), new Goal (Name). Never in sheets with lists.
- **Validation**: validate on Save tap and on field blur after first edit; never block typing. Save button always enabled; on failure show inline errors and scroll to the first. Error text = caption, destructive color, under the field.
- **Destructive actions**: confirm dialog for DB deletes (goal, plan, session, exercise, macro day, body entry, discard workout, import replace, clear data). **Undo snackbar** (5 s, "Undo") only for in-editor removals: plan-editor exercise row, live-workout remove set, unlink plan from goal.
- **Feedback**: snackbar after saves that navigate away ("Workout saved", "Macros saved", "Weight saved"); none for in-place edits.
- **Haptics**: set Done / Update → `HapticFeedbackType.LongPress`; isometric target reached → 300 ms vibration; rest end → service vibration (2×200 ms).
- **Empty states**: every list/section has one (`LhEmptyState`, 1 sentence max + 1 action). Missing numbers render "—".
- **Loading**: no skeletons/spinners for DB reads (initial `UiState` renders "—"/empty for a frame); spinners only for export/import/mock.
- **Dates**: date fields default to today or the route arg; `maxDate = today` for every log (workout, macros, body). Goal dates are unrestricted.
- **Lists**: tap = open; long-press = contextual actions; no swipe-to-delete (avoids accidental deletes).
- **Motion**: default M3 transitions; no custom animations except set-row status color crossfade (150 ms).

---

## 6. Charts (`core/ui/charts`, WP4, Compose Canvas only)

Common (`ChartScaffold.kt`):
```kotlin
data class ChartPoint(val date: LocalDate, val value: Double)
@Composable fun BarChart(range: DateRange, points: List<ChartPoint>, modifier: Modifier = Modifier,
    barColor: Color = LhTheme.colors.foreground, targetLine: Double? = null, targetLabel: String? = null,
    valueFormatter: (Double) -> String = { it.roundToInt().toString() })
data class LineSeries(val name: String, val points: List<ChartPoint>, val color: Color, val unit: String)
@Composable fun DualLineChart(range: DateRange, left: LineSeries, right: LineSeries?, modifier: Modifier = Modifier)
```
- Size: full width × 160dp. Plot insets: left 36dp (y labels), right 8dp (36dp when a right axis exists), top 12dp, bottom 20dp (x labels).
- X: one slot per day in `range` (7 or 14); value at slot center. Labels caption/mutedForeground via `rememberTextMeasurer`: 1W → "M T W T F S S"; 2W → day-of-month on every other day (`3 5 7 …`). Today's label foreground + medium weight.
- Y gridlines: 3 horizontal lines (0 or min, mid, max), 1dp `border` color; labels right-aligned in left gutter. Nice max = smallest of {1,2,2.5,5}×10ⁿ ≥ data max (and ≥ target if present).
- Empty (0 points): axes + gridlines with no labels, centered caption "No data".
- **kcal bars** (`Nutrition` toggle = kcal): bar width 60% of slot (max 24dp), top corners 3dp radius, color `foreground` at 85% alpha; Y from 0. Days without MacroDay: no bar.
- **Protein bars**: same, color `foreground`; bars ≥ target in `success`. Target: dashed horizontal line (`PathEffect.dashPathEffect([6,4])`, 1.5dp, `mutedForeground`) with label "150 g" at right end above line (caption).
- **Weight + muscle** (`DualLineChart`): weight = left axis, `accent`, 2dp stroke; muscle = right axis, `success`, 2dp stroke. Each axis scaled independently to its own `[min − 0.5, max + 0.5]` rounded to 0.5, 3 ticks, labels in series color. Points 3dp radius circles (filled with series color, 1.5dp background outline). Lines connect consecutive points by date (gaps are bridged). Single point → just the dot. Legend row above chart: `● Weight 76.9 kg   ● Muscle 37.3 kg` (latest values in period), caption.
- No touch interaction, no animation. Content description: "<chart name>, N days, latest <value>".
