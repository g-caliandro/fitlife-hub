# 08 — v3 design spec (OpenNutriTracker-style)

Binding inputs: `docs/07-v3-brief.md`. This spec is literal: values here win over "looks like ONT".
Units: dp / sp. `c` = `LhTheme.colors`. All icons `Icons.Outlined.*` (Filled only for selected bottom-tab icon
and the icon inside a tinted square, see 2.4). Page padding 16dp everywhere.

---------------------------------------------------------------------------------------------------
## 1. Tokens

### 1.1 Colors — `LhColors` (Theme.kt)
Keep **every existing field** (call sites compile); re-map values; **add** the fields marked NEW.

| field | light | dark | use |
|---|---|---|---|
| background | #F6F4EF | #171614 | screen bg, bottom-sheet scrim area, FAB notch |
| card | #FFFFFF | #222019 | cards, bottom bar, sheets, dialogs |
| border | #ECE8DF | #2E2A23 | 1dp card border, dividers |
| input | #E3DED3 | #3A352D | text-field border |
| ring | #43A047 | #66BB6A | focus ring (= primary) |
| foreground | #24221E | #F2EFE8 | text |
| muted | #EFECE5 | #2A2721 | add row, kcal chip bg, selected segment, skeletons |
| mutedForeground | #6F6A61 | #A39E93 | secondary text, inactive tab |
| primary | #43A047 | #66BB6A | green: FAB, primary button, selected tab, kcal |
| primaryForeground (onPrimary) | #FFFFFF | #0E1A0F | text/icon on primary |
| NEW primaryContainer | #E6F2E3 | #26381F | tinted icon squares, tonal button bg, empty-state circle |
| NEW onPrimaryContainer | #2E7D32 | #A5D6A7 | icon/text on primaryContainer |
| secondary | #EFECE5 | #2A2721 | (= muted, legacy) |
| secondaryForeground | #24221E | #F2EFE8 | |
| accent | #43A047 | #66BB6A | legacy blue accent is retired → green |
| accentForeground | #FFFFFF | #0E1A0F | |
| accentMuted | #E6F2E3 | #26381F | (= primaryContainer) |
| success | #2E8B3E | #6CC070 | positive deltas, "Done" check |
| successMuted | #E6F2E3 | #26381F | |
| warning | #C77C0E | #E8A23A | over-target kcal, rest-ending |
| destructive | #D14343 | #EF7A6E | delete, negative deltas |
| destructiveForeground | #FFFFFF | #1A0E0C | |
| NEW track | #EEEAE2 | #332F28 | ring/progress track, chart area baseline |
| NEW kcal | #43A047 | #66BB6A | (= primary) |
| NEW protein | #1F8A80 | #4DB6AC | teal |
| NEW carbs | #B8740F | #E0A243 | amber |
| NEW fat | #D4553A | #EE7B61 | red-orange |
| pull | #3F7CC8 | #74A7E6 | day type |
| push | #E07A2A | #F2A15E | day type |
| legs | #7D8F22 | #B4C658 | day type — olive, must not read as primary green |
| core | #8A63D2 | #B39AF0 | day type |
| NEW scrim | #000000 @ 32% | #000000 @ 56% | sheet scrim |

Helpers (Theme.kt / DayTypeIcons.kt): `fun LhColors.dayTypeContainer(t) = dayType(t).copy(alpha = if (isDark) 0.20f else 0.14f)`,
`fun LhColors.tint(color: Color) = color.copy(alpha = if (isDark) 0.20f else 0.12f)` (generic icon-square bg).

M3 mapping (`toM3`): primary/onPrimary/primaryContainer/onPrimaryContainer from above; surface = card;
background = background; surfaceContainer* = card (Highest = muted); outline = input; outlineVariant = border.

### 1.2 Radii — `LhRadius` (Tokens.kt)
Existing names are kept but re-valued (they are used at ~45 sites):
`sm = 8`, `md = 12`, `lg = 16` (+ their shapes), `fullShape` unchanged. NEW:
`card = 24.dp`, `item = 16.dp`, `cardShape`, `itemShape`, `pill = RoundedCornerShape(percent = 50)`, `sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`.
- card 24: LhCard, standalone list items, macro cards, LhAddRow, stat tiles.
- item 16: icon squares, text fields, inner tiles, sheet action rows, calendar day cells (12 → `md`).
- pill: buttons, chips, segmented control, progress bars, kcal chip.

### 1.3 Spacing — `LhSpacing` unchanged (4/8/12/16/24/32). Rules: page padding 16; gap between cards in a list 12;
between sections 24; card content padding 20 (LhCard default); inside list-item card 12h/12v.

### 1.4 Elevation
Flat. Cards = `card` color + 1dp `border`, shadow 0. Only exceptions: FAB shadow 6dp (ambient+spot color #000 @ 18%),
bottom bar 0 shadow (its top edge is defined by the card/background contrast), bottom sheets 0 shadow + scrim.

### 1.5 Typography — Poppins
Add `res/font/poppins_regular.ttf`, `poppins_medium.ttf`, `poppins_semibold.ttf`, `poppins_bold.ttf`
(google/fonts `ofl/poppins`), license at `app/src/main/assets/licenses/poppins-OFL.txt`.
`val Poppins = FontFamily(Font(R.font.poppins_regular, W400), Font(R.font.poppins_medium, W500), Font(R.font.poppins_semibold, W600), Font(R.font.poppins_bold, W700))`.
Every LhType style gets `fontFamily = Poppins`, `letterSpacing = 0.sp`, `platformStyle = PlatformTextStyle(includeFontPadding = false)`.

| LhType | size/line | weight | use |
|---|---|---|---|
| NEW display | 40/48 | Bold, tnum | ring center value, You circle value |
| timer (kept) | 56/64 | SemiBold, tnum | rest countdown, focus target ("6 reps") |
| NEW screenTitle | 26/34 | Medium | tab screen header ("Diary") |
| title (kept) | 20/28 | SemiBold | sub-screen top bar, sheet title, date heading |
| NEW section | 20/28 | Medium | section title with icon ("Food", "Workouts") |
| heading (kept) | 16/24 | SemiBold | card titles, list item title in big rows |
| stat (kept) | 24/32 | SemiBold, tnum | stat tiles, kcal chip |
| bodyLarge (kept) | 16/24 | Normal | list item title, form fields |
| body (kept) | 14/20 | Normal | default text |
| label (kept) | 14/20 | Medium | buttons, chips, macro values "101/116 g" (+tnum) |
| labelSmall (kept) | 12/16 | Medium | tab labels, badges |
| caption (kept) | 12/16 | Normal | subtitles, axis-free captions |

Numbers that change: `LhType.tnum(style)`. Poppins ships `tnum`; if a timer still jitters in screenshots, the
implementer reports it (don't switch font).

---------------------------------------------------------------------------------------------------
## 2. Components (package `core.designsystem.components` unless stated)

### 2.1 Screen header (tab roots) — NEW `LhScreenHeader.kt`
```kotlin
@Composable fun LhScreenHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {})
```
statusBarsPadding; Row height 64, padding start 16 end 8; icon 26dp `foreground` (use Filled variant: Home, Book,
FitnessCenter, AccountCircle); 16dp gap; `LhType.screenTitle`; actions = `LhIconAction`s. No divider, bg = background.

`LhTopBar` (sub-screens) keeps its signature; v3: bg background, **no bottom divider**, title `LhType.title`,
back = `Icons.AutoMirrored.Outlined.ArrowBack` 48dp touch. Add optional param `icon: ImageVector? = null` (appended
last) shown 22dp before title.

### 2.2 LhCard v3 (LhCard.kt — same signature + appended params)
```kotlin
fun LhCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    containerColor: Color = LhTheme.colors.card, border: Boolean = true, shape: Shape = LhRadius.cardShape,
    content: @Composable ColumnScope.() -> Unit)
```
Pressed (onClick != null): scale 0.98 (spring, stiffness 800), no ripple color change beyond default M3 ripple.
NEW `LhGroupCard(modifier, content: ColumnScope.() -> Unit)` = LhCard with `contentPadding = 0`; children are
`LhListItem(standalone = false)`; 1dp `border` dividers full-width between rows (draw via `showDivider`).
NEW `LhCollapsibleCard(title: String, expanded: Boolean, onToggle: () -> Unit, modifier, subtitle: String? = null,
leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, content: ColumnScope.() -> Unit)`:
header row min 64dp (padding 16h), chevron `ExpandMore` rotates 180° (200ms), content `AnimatedVisibility(expandVertically + fadeIn)`.

### 2.3 LhIconSquare — NEW (in `LhListItem.kt`)
```kotlin
fun LhIconSquare(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LhTheme.colors.primary,
    container: Color = LhTheme.colors.tint(tint), size: Dp = 48.dp, iconSize: Dp = 24.dp)
```
Shape `itemShape` (16). Overload `LhIconSquare(dayType: DayType, ...)` uses `DayTypeIcons.of`, tint `dayType(t)`,
container `dayTypeContainer(t)`.

### 2.4 LhListItem — NEW `LhListItem.kt`
```kotlin
@Composable fun LhListItem(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null,
    icon: ImageVector? = null, iconTint: Color = LhTheme.colors.primary,
    leading: (@Composable () -> Unit)? = null,       // overrides icon (DayType square, food initial, exercise icon)
    trailingText: String? = null,                    // "249 kcal", "87 kg" — label style, tnum, foreground
    trailing: (@Composable () -> Unit)? = null,      // overrides trailingText (chevron, button, switch)
    onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
    standalone: Boolean = true,                      // true = own card (radius 24, border); false = row in LhGroupCard
    showDivider: Boolean = false,                    // only for standalone = false
)
```
Layout: min height 72; padding 12 (standalone) / 16h 12v (grouped); leading 48dp square; 16dp gap; title
`bodyLarge` foreground (1 line, ellipsis); subtitle `body` mutedForeground (1 line); trailing 12dp gap.
`LhListRow` (existing) keeps signature and is restyled to = `LhListItem(standalone=false, showDivider=showDivider)`
with its `leadingIcon` in a 40dp `LhIconSquare` (not 48).

### 2.5 LhAddRow — NEW `LhAddRow.kt`
```kotlin
fun LhAddRow(onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, contentDescription: String = label ?: "Add")
```
fillMaxWidth, height 56, `cardShape`, bg `muted`, no border; `Add` icon 24dp `foreground` at 70% alpha centered;
with label: icon + 8dp + label (`label` style, mutedForeground). Pressed: bg darkens to `border`. Placed 8dp below the
last item of a section.

### 2.6 Rings — NEW `LhRing.kt`
```kotlin
fun LhRing(progress: Float, value: String, label: String, modifier: Modifier = Modifier,
    size: Dp = 168.dp, stroke: Dp = 16.dp, color: Color = LhTheme.colors.primary,
    trackColor: Color = LhTheme.colors.track, overColor: Color = LhTheme.colors.warning)
fun LhMiniRing(progress: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, stroke: Dp = 5.dp)
fun LhMacroRingStat(value: String /*"101/116 g"*/, label: String /*"protein"*/, progress: Float, color: Color, modifier: Modifier = Modifier)
```
Canvas: track = full circle; arc from -90°, clockwise, `StrokeCap.Round`, sweep = 360·clamp(progress,0,1).
progress > 1 → full ring in `overColor`. NaN → 0. Center: `value` in `display` (tnum), `label` in `body` mutedForeground, 2dp gap.
`LhMacroRingStat` = Row(MiniRing 32, 8dp, Column(value `label`+tnum foreground, label `caption` mutedForeground)).

### 2.7 LhMacroCard — NEW `LhMacroCard.kt`
```kotlin
fun LhMacroCard(label: String, color: Color, current: Double, target: Double?, modifier: Modifier = Modifier, unit: String = "g")
```
LhCard(padding 16): Row(8dp dot color, 8dp, label `body`); 12dp; `LhProgressBar(fraction=current/target, color, height 8)`
(no target → empty track); 12dp; text `"101/116 g"` (no target: `"101 g"`) `tnum(label)`. Values rounded to int.
Use 3 in a Row, `weight(1f)`, 12dp gaps, in order **carbs · fat · protein** (ONT order).

### 2.8 Chips (LhChip.kt)
- `LhChip(text, selected, onClick, modifier)` keeps signature. Height 36, pill, padding 16h. selected: bg `primary`, fg
  `primaryForeground`; unselected: bg `card`, 1dp `border`, fg foreground. Text `label`.
- NEW `LhValueChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color = LhTheme.colors.primary, trailingIcon: ImageVector? = Icons.Outlined.Edit, onClick: (() -> Unit)? = null)`:
  height 40, pill, bg card, 1dp border, padding 14h; icon 20dp, 8dp, text `tnum(label)` 15sp, 6dp, trailing 16dp mutedForeground.
  Used for weight chip "⚖ 76.7 kg ✎".
- NEW `LhKcalChip(text: String, modifier)` — pill, bg `muted`, padding 16h/8v, text `stat` tnum ("2,432/2,600 kcal").

### 2.9 Segmented control v3 (LhSegmentedControl.kt — same signature)
Height 40, pill outline 1dp `border`, bg card, segments equal width (min 56), 1dp `border` vertical dividers between,
selected segment bg `muted` + text foreground SemiBold; unselected text foreground Normal. `wrapContentWidth` by default
(ONT 7d/30d/90d/All), `fillMaxWidth` when caller passes it.

### 2.10 Buttons v3 (LhButton.kt — same signature/enums)
All pill. Heights: Sm 36, Md 48, Lg 56; padding 20h (Sm 14h); text `label` SemiBold 15sp (Sm 14sp); icon 20dp, 8dp gap.
| variant | bg | fg | border |
|---|---|---|---|
| Primary | primary | primaryForeground | – |
| Tonal | primaryContainer | onPrimaryContainer | – |
| Secondary (legacy) | muted | foreground | – |
| Outline | transparent | foreground | 1dp input |
| Ghost | transparent | foreground | – |
| Destructive | destructive | destructiveForeground | – |
Pressed: scale 0.97 + bg blended 10% toward foreground. Disabled: alpha 0.4. `LhIconButton`: 48dp circle, same variants
(Tonal = primaryContainer). `LhSaveBar`: Lg Primary full width.

### 2.11 Progress bars (LhProgressBar.kt)
Same signature; defaults change: `color = LhTheme.colors.primary`, `height = 8.dp`; append `trackColor: Color = LhTheme.colors.track`.
Pill shape both track and fill. Animated fraction (§4).

### 2.12 Stat tiles (LhStatTile.kt — same signature)
LhCard(padding 16, radius 24): label `caption` mutedForeground; 4dp; value `stat` + unit `body` mutedForeground baseline-aligned;
delta row `labelSmall` with arrow 14dp, success/destructive/mutedForeground; caption `caption` mutedForeground.

### 2.13 Section title with icon (LhSectionHeader.kt)
Existing `LhSectionHeader` signatures stay; style → `LhType.section`. Append param `icon: ImageVector? = null`
(24dp foreground, 12dp gap, ONT "🏃 Activity"). Overflow action = `LhIconAction(Icons.Outlined.MoreVert, ...)` or
`LhSeeAllChip` (restyled: pill, bg muted, height 32, label `labelSmall`).

### 2.14 Bottom bar + FAB (navigation/AppScaffold.kt, NEW navigation/BottomBarShape.kt)
- `enum class Tab { Home, Diary, Train, You }` (Diary route = `Routes.CALENDAR` unchanged, label "Diary"; You route = `Routes.YOU`).
  Icons: Home `Outlined.Home/Filled.Home`; Diary `Outlined.Book/Filled.Book`; Train `Outlined.FitnessCenter/Filled`;
  You `Outlined.AccountCircle/Filled.AccountCircle`. testTags `tab-<Name>`, FAB `fab-add`.
- Bar: bg `card`, height **64 + navigationBars inset**, no shadow, no top divider. 5 slots of equal weight: Home, Diary,
  (FAB slot, empty), Train, You. Item: icon 24, 4dp, label `labelSmall`; selected = Filled icon + both `primary`;
  unselected = Outlined + `mutedForeground`. No indicator pill. Touch target = full slot.
- Notch: `BottomBarShape(fabRadius = 28.dp, gap = 6.dp, fillet = 8.dp)` — a `Shape` = rect minus a circle of radius
  34dp centered at (width/2, 0), with 8dp concave fillets joining the top edge (cubic). The notch shows `background`.
- FAB: 56dp circle, `primary`, `Add` icon 28dp `primaryForeground`, shadow 6dp, center at (width/2, bar top + 0dp),
  i.e. half above the bar. contentDescription "Add". Hidden (with bar) on non-tab routes, as today.
- Scaffold content gets bottom padding = 64 + inset (the FAB overhang 28dp overlaps content; lists add 32dp extra
  bottom contentPadding: `LhSpacing.xxl`).

### 2.15 (+) Add sheet — NEW navigation/AddActionSheet.kt
```kotlin
@Composable fun AddActionSheet(onDismiss: () -> Unit, activeWorkout: String? /* "Push · 23 min" or null */,
    onAddFood: () -> Unit, onQuickMacros: () -> Unit, onStartWorkout: () -> Unit, onLogWeight: () -> Unit)
```
`LhBottomSheet` (bg card, `sheetShape`, drag handle 32×4 `border`), title "Add" (`title`), 4 rows, 8dp gaps, 16 page padding,
24dp bottom + nav inset. Row = `LhListItem(standalone = true)` height 72 with 48dp icon square:
| row | icon | tint | subtitle |
|---|---|---|---|
| Add food | Outlined.Restaurant | primary | "Search, scan or pick a recent food" |
| Quick macros | Outlined.Bolt | protein | "Add protein/carbs/fat totals" |
| Start workout (`activeWorkout != null` → "Resume workout") | Outlined.FitnessCenter | push | "Pick a plan" / activeWorkout |
| Log weight | Outlined.MonitorWeight | carbs | "Weight and body fat" |
Targets: Add food → `Routes.foodSearch(selectedDateOrToday)` (constant owned by Food agent; if absent at DS merge, DS adds
`FOOD_SEARCH = "food/search?date={date}"` + builder, the Food agent wires the NavHost entry); Quick macros → `macroEdit(date)`;
Start → `workoutStart()` or `WORKOUT_LIVE` when active; Log weight → `bodyEdit(today)`. "date" = Diary selected date when
on Diary, else today (AppScaffold reads a `LocalDiaryDate` MutableState set by Diary).

### 2.16 Bottom sheet (LhBottomSheet.kt — same signature): bg card, `sheetShape`, handle, scrim token, title `title` 20/28.

### 2.17 Charts (core/ui/charts — signatures kept)
- Shared: plot inside LhCard (padding 20); chart height 180 (`ChartHeight`); grid: 3 horizontal lines 1dp `border`
  solid; y labels 10sp mutedForeground right-aligned in 32dp gutter; x labels 11sp, today = foreground SemiBold.
- Target line: dashed (6dp on / 6dp off) 1.5dp, `foreground` @ 55%; label at right end `caption`.
- `BarChart`: default `barColor = primary`; bar width = 56% of slot (max 20dp), top corners fully rounded (radius = width/2),
  bottom square; zero days = 4dp stub in `track`; `aboveTargetColor` default null. Callers pass macro colors
  (kcal → kcal, protein → protein, …).
- NEW `AreaLineChart.kt`: `fun AreaLineChart(range: DateRange, points: List<ChartPoint>, modifier: Modifier = Modifier,
  color: Color = LhTheme.colors.primary, target: Double? = null, today: LocalDate? = null, name: String = "Line chart")`.
  Monotone cubic line 2.5dp round cap; fill under line = `color` @ 12% (dark 18%) down to baseline; dots 4dp radius
  `color` with 2dp `card` ring; missing days break the line (no interpolation across gaps > 1 day).
- `DualLineChart`: line 2.5dp, dots as above, no area fill.

### 2.18 Empty state (same signature): 72dp circle `primaryContainer`, icon 32dp `onPrimaryContainer`; 16dp; title `heading`;
4dp; description `body` mutedForeground centered max 280dp; 16dp; action Tonal Md.

### 2.19 Text fields / steppers / dialogs: radius `item` (16); field height 52; bg card; 1dp `input`; focused 2dp `primary`.
Stepper buttons 48dp circles Tonal. Dialogs: card bg, radius 28, padding 24.

---------------------------------------------------------------------------------------------------
## 3. Screens (ASCII; `[ ]` = card, `( )` = pill, `▣` = tinted icon square)

### 3.1 Home (tab)
```
 ⌂  Life Hub                                  ⚙        LhScreenHeader(Home filled; action Settings→YOU)
 Today                                                 LhSectionHeader(section) — date caption right "Thu 12 Nov"
 (⚖ 76.7 kg ✎)  (🔥 5-day streak)                      LhValueChip weight→bodyEdit; streak chip only if achievements expose it
[ ▣↓ 1,783              ▣P 90 g                ]      Ring card (LhCard padding 20)
[   eaten                 protein               ]     left: tint square 40 (ArrowDownward, primary) + value stat + "eaten"
[            ╭───────╮                          ]     right: square (protein tint, FitnessCenter? use Outlined.EggAlt) + "90 g" + "protein"
[           │   817   │                         ]     LhRing 168: target kcal → value = left kcal, label "kcal left",
[           │kcal left│                         ]       progress eaten/target; no kcal target → value eaten,
[            ╰───────╯                          ]       label "kcal eaten", progress = protein/proteinTarget (color protein)
[ carbs ▬▬▬▬░ 205/250 g ] [ fat ▬▬▬ 67 g ] [ protein ▬▬░ 90/150 g ]   3×LhMacroCard
 🏋 Training                                           section w/ icon
[ ▣Push  Push day                      ( ▶ Start ) ]   LhListItem leading=LhIconSquare(dayType); subtitle "5 exercises · Wed"
                                                       done today: subtitle "Done · 46 min · 81%", trailing ✓ success
                                                       rest day: icon Outlined.Bedtime mutedForeground, "Rest day", trailing none
 ⚑ Goals                                    (See all)
[ Muscle Up                           19 / 36 ]       LhCard: heading + tnum right; LhProgressBar primary;
[ ▬▬▬▬▬▬░░░░  sessions done · 14 days left   ]        caption; sub-bars per day type in day-type colors (6dp)
 ⟲ History                         (1W|2W)            segmented wrapContent
 (‹)  Mon 9 Nov – Sun 15   (›)                        icon buttons Ghost
[Sessions 2 ↓-2] [Plan done 2/2]                       2×2 LhStatTile grid, 12dp gaps
[Avg reps 11.3 ↑] [Volume 315 reps]
[ Calories                       (kcal|Protein) ]      LhCard; header Row(heading, segmented Sm)
[ AreaLineChart kcal, dashed target            ]      kcal: AreaLineChart color kcal target=kcalTarget;
[                                              ]      Protein: BarChart color protein target=proteinTarget
[ Daily average                                 ]     ONT "Daily average": 3 rows label+ "381 / 438 g" + bar 8dp macro color
[ Body weight  DualLineChart                    ]     existing body chart restyled
```

### 3.2 Diary (tab, replaces Calendar; route `calendar` kept)
```
 📖 Diary                                     📅      action Today (jumps to today)
[  ‹        October 2026         ›           ]        calendar card padding 16
[ Mon Tue Wed Thu Fri Sat Sun                ]        weekday labels caption mutedForeground
[ 28  29  30   1   2   3   4                 ]        cell 40dp circle area, 48dp row height, number body tnum
[  ·       ·        ·  ·                     ]        marker row below number (6dp tall): day-type dot(s) 6dp in
[  5   6  (7)  8   9  10  11                 ]          type color + food/macro-logged dot primary (max 3 dots, 3dp gap)
                                                      workout day: number circle bg = dayTypeContainer, number color = type
                                                      selected: 40dp filled primary circle, number primaryForeground SemiBold
                                                      today (not selected): 1.5dp primary ring; out-of-month 40% alpha
 legend row (6dp dots + caption): Pull Push Legs Core Food Weight   (scrolls horizontally if needed)
 Wednesday, 21 October 2026                           LhType.title, 24dp top
[ (2,432/2,600 kcal)                           ]      LhKcalChip; no kcal target → "2,432 kcal"
[ ◔ 247/250 g  ◕ 88/80 g  ◔ 163/150 g          ]      3×LhMacroRingStat carbs·fat·protein; 16dp below chip
 🍴 Food                                       ⋮      overflow: Copy yesterday, Clear day (confirm)
[ ▣B  Greek yogurt            320 kcal  ]             LhListItem; leading = 48dp square primary tint with first letter
[     200 g · P 20 · C 8 · F 6          ]               (heading, onPrimaryContainer); swipe-to-delete + snackbar Undo; tap→edit grams
[ ▣Q  Quick add               540 kcal  ]             quick macros record: icon Bolt protein tint, subtitle "P 40 · C 60 · F 15"
( + )                                                 LhAddRow → food search for this date
 🏋 Workouts
[ ▣Push Push · 46 min · RPE 8     81%  ⌄ ]            LhCollapsibleCard; trailing compliance "81%" stat+tnum + "3/5 exercises" caption;
[   expanded: exercise rows (name, sets "8·8·6", PR badge) ]  expanded body = existing session detail rows; "Open" Tonal Sm
( + Log workout )                                     LhAddRow(label) → workoutLog(date)
 ⚖ Body
[ ▣⚖ 76.7 kg · 14.2 % bf          ›     ]             LhListItem → bodyEdit(date); none → LhAddRow("Log weight")
```
Empty day: Food section shows only the add row + caption "Nothing logged" (no big empty state).

### 3.3 Add food flow (Food agent builds; uses DS components)
```
 ←  Add food · Wed 21 Oct                              LhTopBar
 [ 🔍 Search foods…                    ▦ ]             field height 52, pill, bg card, border; ▦ = QrCodeScanner icon 48dp → scanner
 ( Recent | Search | My foods )                        LhSegmentedControl fillMaxWidth
[ ▣G Greek yogurt 2%                 59 kcal ]         LhListItem; subtitle "Fage · per 100 g"; trailing "+" LhIconButton Tonal 40
[ ▣C Chicken breast                 165 kcal ]           (quick add 100 g / last used grams) ; tap row → detail
 Search tab states: idle caption "Type at least 2 letters"; loading 3 skeleton item cards (muted); error LhEmptyState
 (CloudOff, "Can't reach Open Food Facts", "Recent and My foods work offline", action Retry); no results → empty + action
 "Create custom food".   My foods: list + LhAddRow("Create food").
```
Barcode scanner: full-screen camera, black bg, 260×160 viewfinder rounded 24 with 3dp primary corners, close X top-left
(white 48dp), caption "Point at a barcode" white; on hit: haptic, go to detail; not found → sheet "Not found" + Create food.

Food detail:
```
 ←  Greek yogurt 2%                       ☆          favorite toggle
 Fage · 100 g = 59 kcal                               caption
[        ╭────╮                                ]      LhRing 140 (kcal of entry vs remaining kcal target, else full)
[        │ 118│  kcal                          ]
[ ◔ 7 g carbs  ◔ 4 g fat  ◔ 20 g protein       ]      3×LhMacroRingStat, live
 Amount
 [  200        g ]  (−)(+)                             LhStepper step 10, suffix g; or Serving toggle chips "g | serving"
 (50 g) (100 g) (150 g) (200 g) (1 serving 170 g)      LhChip row, selected = matches value
 ─────────────
 [ Add to Wed 21 Oct ]                                 LhSaveBar Primary Lg; edit mode: "Save" + Destructive outline "Delete"
```
Custom food form: LhTopBar "New food"; fields (LhTextField/LhNumberField, 16dp gaps): Name*, Brand, Barcode (with scan icon),
section "Per 100 g": kcal, Protein, Carbs, Fat (2×2 grid, suffix), "Serving size (g)" optional; live hint caption
"kcal from macros: 412" (warning color if |diff| > 15%); LhSaveBar "Save food".

### 3.4 Train (tab)
```
 🏋 Train                                     +        action New plan
 [ ▶ Start workout ]                                   Primary Lg full width (Resume workout · Push 23 min when active)
 Plans
[ ▣Pull  Pull                       ( ▶ Start ) ]      LhListItem standalone; leading LhIconSquare(dayType);
[        Mon · 6 exercises · Muscle Up         ]       subtitle "Mon · 6 exercises" (+ goal names, 1 line ellipsis);
                                                       trailing LhButton Tonal Sm "Start" leadingIcon PlayArrow; tap → plan detail
 Library
[ ▣≡ Exercises            24 exercises    › ]          LhListItem icon Outlined.ListAlt
```
Plan detail / editor: LhTopBar(title, icon via `titleIcon` dayType); exercise rows = LhGroupCard of LhListRow with
40dp exercise icon (`ExerciseIcons.forExercise`, fallback day-type square); subtitle "4 × 6 reps · +10 kg".

### 3.5 You (tab, NEW route `you`)
```
 ◉ You
          ╭──────────╮                                 160dp circle, bg tint(primary) , 2dp primary @ 50% border
          │   76.7   │                                 display tnum
          │    kg    │                                 body mutedForeground
          ╰──────────╯
        14.2 % body fat · 2 days ago                   heading center + caption; no data → circle "—", text "Log your weight" Tonal
 Progress                                              LhSectionHeader (labelSmall? no → `body` mutedForeground, 24 top, 8 bottom)
[ ▣⚑ Goals            2 active            › ]          LhGroupCard
[ ▣🏆 Achievements    12 / 40 unlocked    › ]
[ ▣⚖ Body history     Last: 76.7 kg       › ]
 Nutrition
[ ▣◎ Targets          Protein 150 g · 2,600 kcal › ]
 Settings
[ ▣🔔 Notifications   Reminders on       › ]
[ ▣⇅ Data             Export, import, mock › ]
[ ▣ⓘ About            v0.3                › ]
```
Icons: Flag, EmojiEvents, MonitorWeight, TrackChanges, Notifications, ImportExport, Info. All primary tint.

### 3.6 Goals list / detail / Achievements
- Goals (route kept, now pushed from You, LhTopBar back + `+` action): goal = LhCard (heading + "19 / 36" stat tnum
  right; LhProgressBar 8 primary; caption "sessions done · 14 days left"; per-type sub-bars 6dp in day-type color with
  `labelSmall` "Pull 10/24"). Finished goals under section "Completed" with ✓ success square.
- Goal detail: header card with LhRing 140 (progress, value "53%", label "complete"), stat tile row (Sessions, Days left),
  linked plans as LhListItem(dayType), chart AreaLineChart cumulative sessions.
- Achievements: 3-column grid of tiles (LhCard padding 12, radius 24, square): 56dp circle — unlocked: bg tint(warning)
  icon `warning`; locked: bg muted icon mutedForeground 50%; name `labelSmall` 2 lines centered; progress bar 4dp when
  partially done. Header card: "12 / 40 unlocked" + LhProgressBar.

### 3.7 Forms (macro / body / goal / plan / exercise editors)
LhTopBar back + title; content in a single LhCard per group (padding 20, 16dp field gaps), group titles `heading` above
card (24 top/8 bottom); LhSaveBar bottom. Quick macros form: kcal auto-computed line (`stat` tnum, "≈ 2,432 kcal") inside
card, fields protein/carbs/fat with macro-color 8dp dot before label. Body form: weight stepper (0.1), body-fat stepper,
date field. Goal form: target fields + chips. Plan editor: day-type picker = 4 `LhIconSquare(dayType)` 56dp choosers with
2dp type-colored border on selected, weekday LhChips, exercise LhGroupCard with drag handles.

### 3.8 Settings sub-screens (from You)
- Targets: LhGroupCard rows each with LhNumberField trailing (protein g, kcal, carbs g, fat g; empty = "No target"),
  caption "kcal ring shows kcal left when a kcal target is set".
- Notifications: LhGroupCard of LhSwitchRow + time fields (existing settings content moved).
- Data: rows Export (FileUpload), Import (FileDownload), Load mock data (Science), Delete all data (DeleteForever,
  destructive tint, LhTypedConfirmDialog) — existing behavior.
- About: app icon 72dp, name `title`, version caption, licenses rows (Poppins OFL, Open Food Facts ODbL attribution).

### 3.9 Focus-mode live workout (Workout agent builds per this; full screen, no bottom bar, KeepScreenOn)
Exercise step:
```
 ✕   Exercise 2/6 · Set 3/4        12:41     ☰        top row 56: ✕ = minimize (back to app, workout stays active),
 ▬▬▬ ▬▬▬▬ ▬▬▓░ ░░░ ░░░ ░░                              ☰ = overview sheet; title labelSmall mutedFg center, elapsed tnum label
                                                       segmented bar: 6dp high, pill segments, 3dp gap within exercise,
                                                       10dp gap between exercises, 16dp side padding.
                                                       done=primary, skipped=mutedForeground@35%, current=primary pulsing, todo=track
            ╭──────────────╮
            │   [ICON]     │                           exercise icon 152dp inside 200dp circle bg tint(dayType color)
            ╰──────────────╯                           (fallback: day-type icon 96dp in same circle)
            Pull-up                                    title (20/28 SemiBold → use `screenTitle` 26) center
            Set 3 of 4                                 body mutedForeground
             6 reps                                    LhType.timer (MAX → "MAX", seconds → "20 s"); 
            +10 kg                                     stat primary (only if weight)
         Last time: 6 · 6 · 5                          caption mutedForeground
 ─────────────── spacer (weight 1) ───────────────
 [            ✓  Done            ]                     Primary Lg (56) full width
 ( Skip )                ( Adjust )                    two Outline Md, weight 1 each, 12dp gap; 16dp + nav inset bottom
```
SECONDS set: target shows "20 s"; primary button "▶ Start hold" → button area becomes LhRing 200 counting down
(value "0:14", label "hold"), then Primary "✓ Done" (stopwatch beyond target allowed; ring turns success at target).
MAX set: Done opens Adjust sheet pre-focused on actual reps (required).
Adjust sheet (LhBottomSheet "Adjust set 3"): LhStepper Reps (or Seconds), LhStepper Weight kg (step 2.5), "Apply" Primary.

Rest step:
```
 ✕   Rest · after Set 3/4          12:52     ☰
 ▬▬▬ ▬▬▬▬ ▬▬▬░ ...
              ╭──────────╮
             │   1:24    │                             LhRing 240 stroke 12, progress = remaining/total (shrinks),
             │   rest    │                             color primary; last 10 s color warning; value timer style
              ╰──────────╯
       ( −15 s )      ( +15 s )                        Outline Md
 Up next                                               body mutedForeground
[ ▣icon  Pull-up · Set 4 of 4          6 reps ]        LhListItem leading 48 exercise icon
 [          Skip rest          ]                       Tonal Lg full width
```
Ready state (rest reached 0; tone + vibration):
```
              ╭──────────╮
             │    ✓      │                             ring full primary, center check 56dp primary; whole screen bg
              ╰──────────╯                             = primaryContainer (crossfade 300ms)
          Ready — next set                             screenTitle center
     Pull-up · Set 4 of 4 · 6 reps                     body mutedForeground
 [        ▶ Start next set        ]                    Primary Lg — acknowledging advances
```
Overview sheet (☰): LhBottomSheet full-height (skipPartiallyExpanded), title "Workout"; per exercise a LhCollapsibleCard
(expanded for current) listing set rows (existing SetRow restyled: status icon ✓/–/•, "Set 3 · 6 reps · +10 kg", tap =
jump), "+ Add set" LhAddRow per exercise; reorder via drag handle; bottom LhAddRow("Add exercise"); footer Destructive
Outline "Finish workout" (confirm when sets remain).
Summary (after last set): screen, not sheet:
```
            ( ✓ )                                      96dp circle primary, check 48 white (Done burst on enter)
        Workout complete                               screenTitle
        Push · 46 min                                  body mutedForeground
 [Sets 18] [Reps 142] / [Volume 1,420 kg] [Compliance 81%]  2×2 LhStatTile
 🏆 New records                                         section; LhListItem rows icon EmojiEvents warning tint
 RPE  (6)(7)(8)(9)(10)                                 LhChip row
 [ notes field ]
 [        Save workout        ]                         LhSaveBar
```

---------------------------------------------------------------------------------------------------
## 4. Motion (all in `core/designsystem/Motion.kt`, NEW)
```kotlin
object LhMotion { val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f); val standard = FastOutSlowInEasing
  const val short = 150; const val medium = 300; const val long = 500 }
fun focusStepTransition(forward: Boolean): ContentTransform
```
- Focus steps: `AnimatedContent(targetState = step, transitionSpec = { focusStepTransition(forward) })`.
  forward: `(slideInHorizontally(tween(300, easing = emphasized)) { it / 4 } + fadeIn(tween(220, delayMillis = 80)))
  togetherWith (slideOutHorizontally(tween(300, easing = emphasized)) { -it / 4 } + fadeOut(tween(150)))`;
  backward (jump to earlier set): mirror signs. `SizeTransform(clip = false)`. Key = step id (setId + phase).
- Ready state: crossfade bg color `animateColorAsState(tween(300))`; check icon scale 0.6→1 spring(damping 0.5, stiffness 500).
- Done burst (on Done tap): 72dp primary circle with white check over the exercise icon: scale 0→1.15→1.0 (spring damping
  0.5, stiffness 400) ; 8 dots (4dp, primary) radiate 0→56dp from center at 45° steps with alpha 1→0, 450ms EaseOut;
  `view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` (API 30+, else `LONG_PRESS`); after 450ms trigger step change.
  Skip: no burst, haptic `CLOCK_TICK`, immediate transition.
- Segmented bar current segment pulse: `rememberInfiniteTransition` alpha 0.35↔1.0, 900ms, `RepeatMode.Reverse`.
- FAB: press scale 0.92 spring(dampingRatio 0.6, stiffness Medium); icon rotates 0→45° (tween 200) while the add sheet is open.
- Rings/bars on load: sweep/fraction animate from 0 with `tween(700, easing = emphasized)` on first composition; mini rings
  stagger 60ms each; later value changes `tween(400)`. Ring center number: `animateIntAsState(tween(700))` count-up.
  Bar charts: bars grow from baseline 500ms, stagger 25ms per bar. Area chart: clip-reveal left→right 600ms.
- Collapsible cards: `expandVertically(tween(250, easing = emphasized)) + fadeIn(tween(200))`, chevron rotate 200ms.
- Tab switch: no slide; `fadeIn(tween(150))` / `fadeOut(tween(100))` in NavHost for tab roots; sub-screens keep current.
- Reduced motion: if `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` → `LhMotion.reduced = true` → all of the above snap,
  burst skipped (haptic kept).

---------------------------------------------------------------------------------------------------
## 5. Implementation plan

Order: **V3-DS first** (blocking). Then **V3-SCREENS-A** and **V3-SCREENS-B** in parallel. Food agent and Workout agent
run concurrently with V3-DS; after all merge, A and B do the light restyle passes listed below.

### Backward-compat rules for V3-DS (Food/Workout agents compile against current DS)
1. No removal/rename of any public function, parameter, enum value, object member or `LhColors` field. New params are
   appended **with defaults**. New `LhColors` fields are only constructed in Theme.kt (two instances).
2. Route constants & builders unchanged; only add (`YOU`, `TARGETS`, `SETTINGS_NOTIFICATIONS`, `SETTINGS_DATA`, `ABOUT`,
   and `FOOD_SEARCH` only if the Food agent hasn't). `TAB_ROOTS = [HOME_ROOT, CALENDAR, PLANS, YOU]`; `GOALS` becomes a
   pushed screen (its screen gains `onBack`).
3. Do not touch `feature/food/**`, `feature/workout/LiveWorkout*`, `RestTimerBar`, `FinishSheet`, `SetRow`,
   `AddExerciseSheet`, `data/**`. When merging LhNavHost/Routes with the other agents, keep all their entries.
4. Until DS lands, Food/Workout agents use existing components (LhCard, LhListRow, LhButton, LhSegmentedControl,
   LhBottomSheet, LhStepper); they get v3 look automatically, and may switch to LhListItem/LhRing/LhAddRow once merged.

### V3-DS (owner of)
- `core/designsystem/Theme.kt, Tokens.kt, Motion.kt (new), DayTypeIcons.kt (color helpers only)`
- `core/designsystem/components/*` (restyle all) + new `LhScreenHeader.kt, LhListItem.kt (LhListItem, LhGroupCard,
  LhIconSquare), LhAddRow.kt, LhRing.kt, LhMacroCard.kt, LhCollapsibleCard.kt`; LhValueChip/LhKcalChip in LhChip.kt
- `core/ui/charts/*` restyle + `AreaLineChart.kt`
- `res/font/poppins_*.ttf`, `assets/licenses/poppins-OFL.txt`, tab strings in `res/values/strings.xml`
- `navigation/AppScaffold.kt` (Tab enum, bar, FAB, LocalDiaryDate), `navigation/BottomBarShape.kt`,
  `navigation/AddActionSheet.kt`, `navigation/Routes.kt`, `navigation/LhNavHost.kt`
- shells so routes resolve: `feature/you/YouScreen.kt` (header + circle + rows wired to routes),
  `feature/settings/TargetsScreen.kt, NotificationsScreen.kt, DataScreen.kt, AboutScreen.kt` as stubs that call into the
  existing `SettingsScreen` sections (B replaces bodies; DS never edits them again)
- Done when: `./gradlew assembleDebug testDebugUnitTest` green, screenshots of Home/Diary/Train/You light+dark with new
  bar + FAB + sheet, docs/ux-v2-ds-notes.md updated → rename section "v3".

### V3-SCREENS-A (owner of)
- `feature/home/**` (§3.1), `feature/calendar/**` → Diary (§3.2; keep file names; screen title "Diary"),
  `feature/nutrition/**` quick-macros form (§3.7).
- After Food agent merges: light restyle of `feature/food/**` screens to §3.3 (swap rows to LhListItem, add row,
  rings on detail, segmented tabs) — no data/VM changes; Diary Food section consumes the Food agent's repository/VM API.

### V3-SCREENS-B (owner of)
- `feature/plans/**` (§3.4), `feature/you/**` (§3.5), `feature/goals/**`, `feature/achievements/**` (§3.6),
  `feature/settings/**` (§3.8), `feature/body/**` (§3.7), `feature/workout/WorkoutStartScreen.kt,
  WorkoutStartPreview.kt, SessionScreen.kt, SessionEdit.kt, WorkoutLogScreen.kt, WorkoutOps.kt`.
- Targets screen UI uses the Food agent's target fields (kcal/carbs/fat); if absent, render protein only + TODO note.
- After Workout agent merges: light restyle pass on `LiveWorkoutScreen.kt`/focus files, `FinishSheet.kt`, `SetRow.kt`
  to match §3.9 exactly (colors, sizes, motion constants from `LhMotion`) — no behavior changes.

### Workout agent / Food agent
Build to §3.9 / §3.3 directly; read tokens via `LhTheme.colors` (new fields available after DS merge — until then use
`primary`/`muted`, never hardcoded hex).

---------------------------------------------------------------------------------------------------
## v3 QA (design lead, 2026-11-12)

Checked: every `tour-*` / `tour-dark-*` screenshot (39 steps each, real app + mock data) and the per-screen shots
(food, forms, live workout, You/settings, goals, plans) in light and dark, against ONT refs 1–6 and this spec.
Build `assembleDebug :app:testDebugUnitTest` green; tour now also drives the (+) sheet.

Fixed
- **FAB overhang**: tab content is padded by bar (64 + nav inset) + 28dp FAB overhang in AppScaffold, and every
  tab scroll container (Home, Diary, Train, You) adds `LocalTabBottomPadding` (16dp, LhActions.kt) → last row ends
  16dp above the FAB. Snackbar host lifted above the FAB too. Tour `performSemanticsAction` workarounds removed
  (plain `performScrollTo().performClick()`; the tour lets "Undo" snackbars time out before tapping list bottoms).
- **LhStatTile**: delta row hidden when `delta == null` (no lone "—"); value/unit baseline-aligned ("315 reps",
  "14 d"); Home 2×2 tiles use equal row heights (IntrinsicSize.Min).
- **Live workout**: Done/quick-done/hold-done = `HapticFeedbackConstants.CONFIRM` (API 30+, else LONG_PRESS), Skip =
  `CLOCK_TICK` (§4). Overview sheet exercises are collapsible (chevron header `ex-header-<order>`, current exercise
  expanded, "done/total · target" summary, animateItem).
- **Live workout minimize bug**: entering the live screen from Start/Preview now pops the whole start chain, so ✕
  returns to the tab (before, Back landed on the Start screen, which bounced straight back into the workout).
- **(+) sheet → Add food bug**: it opened Quick macros (looked up a never-registered `FOOD_SEARCH` route); now goes to
  `foodAdd(date)`; dead route removed. Tour verifies Add food / Quick macros / Log weight / Start workout / Resume.
- **Diary Food ⋮**: Copy yesterday (entries + quick add if the day has none; Undo) and Clear day (confirm dialog;
  Undo restores entries + quick add).
- **Add food rows**: trailing 40dp tonal "+" quick-add (1 serving if the food has one, else 100 g) with
  "Added … · Undo" snackbar; kcal moved into the subtitle ("165 kcal / 100 g · Brand") so names no longer truncate.
- **Home**: ONT streak chip ("🔥 N-day streak", consecutive days with food or quick-add macros, alive until today
  ends; `loggingStreak`, unit-tested) next to the weight chip; **Daily average** card under the Nutrition chart
  (avg kcal + days logged, carbs/fat/protein rows "264 / 250 g" with 8dp macro-colored bars; `macroAverages`).
  "Workout in progress" row uses an icon-only Primary ▶ (text button truncated the title).
- **Diary legend**: wraps (FlowRow) instead of clipping "Weig…"; "Macros" → "Food" per §3.2.
- **Dark contrast**: snackbar action (inversePrimary) was light green on the light inverse surface (~2:1); now
  #2E7D32 in dark / #81C784 in light.

Remaining gaps (not blocking)
- Carbs/fat rings and macro cards render an empty track when no carbs/fat target is set (default); consider a
  "set target" hint or hiding the ring.
- Train plan subtitles ("5 exercises · Sat · Dragon Flag…") still ellipsize at 360dp with the Tonal "Start" button.
- Goal detail lacks the cumulative-sessions AreaLineChart (§3.6); workout summary is still a sheet, not a screen (§3.9).
- Over-target kcal ring switches to warning amber (reads like the carbs color) — acceptable, but worth a legend.
- Tour screenshots are 360×1400dp, so focus-mode screens look sparse; check on a real 360×800 device.
