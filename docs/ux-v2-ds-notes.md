# UX v3 design-system notes (supersedes v2; see 08-v3-design.md)

Package `com.lifehub.app.core.designsystem` (+ `.components`). Icon set: **Icons.Outlined.*** everywhere (Filled only for the selected bottom-nav tab). Never mix Rounded.

## Components
- `LhButton(text, onClick, modifier, variant = Primary, size = Md, leadingIcon: ImageVector? = null, enabled = true)`; variants `Primary | Tonal | Secondary(legacy) | Outline | Ghost | Destructive`. Md = 48dp (buttonMd), Lg 48dp, Sm 32dp (still 48dp touch).
- `LhActionTile(icon, label, onClick, modifier)` - icon above label; put 3 in a Row with `Modifier.weight(1f)`, 12dp spacing.
- `LhSeeAllChip(onClick, modifier, label = "See all")` - pill with chevron.
- `LhIconAction(icon, contentDescription, onClick)` - top-bar action, 48dp touch (pencil `Outlined.Edit`, `Outlined.Today`).
- `LhEmptyState(title, modifier, description?, icon?, actionText?, onAction?, actionIcon?)` - old signature kept; icon now 40dp in 72dp muted circle, action is Tonal.
- `LhBottomActionBar(modifier) { content }` - divider + padded content, handles imePadding + navigationBarsPadding. Use as Scaffold `bottomBar` (no extra imePadding on the screen content duplicate).
- `LhSaveBar(onClick, modifier, label = "Save", enabled = true, secondary: (@Composable () -> Unit)? = null)` - full-width filled button; `secondary` (e.g. Tonal "Copy yesterday") sits below it.
- `LhSectionHeader(title, modifier, topPadding = 24.dp, action: (@Composable () -> Unit)? = null)` - heading style, 24dp top / 4dp bottom padding built in, callers provide 16dp page padding. Legacy `(title, actionText=, onAction=)` overload renders a SeeAllChip.
- `LhTopBar(title, ...)` is the single top-bar (LhType.title); do not make custom headers.
- `LhBottomBar` (navigation/AppScaffold.kt): M3 style, filled icon + accentMuted pill on selected (`Tab.selectedIcon`).
- `KeepScreenOn()` - call inside a screen's composition (live workout).

## Snackbars
AppScaffold hosts one SnackbarHostState and dismisses the current snackbar on every destination change. To show a message on the screen you return to (e.g. after save): navigate back first, then
`SnackbarBus.show("Macros saved", actionLabel = "Undo", onAction = { undo() })` (no `onAction` = no button). The host dismisses the current snackbar synchronously in a NavController destination listener, so a bus message lives exactly until the next navigation (never leaks onto the next form). The host is lifted above any `LhBottomActionBar` via `LocalDockedBarHeight`. `LocalSnackbarHostState` still works for in-screen messages (auto-dismissed on leaving).

## Rules
- One Primary (filled) button per screen. Secondary actions = Tonal + leadingIcon. No text-only action links; use `LhSeeAllChip`/Tonal/Outline.
- Save lives in `LhSaveBar`, not the top bar. Top-bar actions are icons via `LhIconAction`.
- Page padding 16dp, 24dp between sections, 12dp inside cards. Numbers that change use `LhType.tnum(...)`.

## v3 additions (V3-DS)
Poppins, green/warm palette (`LhColors` + primaryContainer/track/kcal/protein/carbs/fat/scrim, `LhColors.tint`, `dayTypeContainer`), radii card 24 / item 16 / pill, `LhMotion` + `lhTween` (reduced-motion aware).
New: `LhScreenHeader`, `LhListItem/LhGroupCard/LhIconSquare/LhLetterSquare`, `LhAddRow`, `LhRing/LhMiniRing/LhMacroRingStat`, `LhMacroCard`, `LhCollapsibleCard`, `LhValueChip/LhKcalChip`, `AreaLineChart`. `LhTopBar` gained `icon` (before `actions`), `LhSectionHeader` gained `icon` (before `action`). Bottom bar `Home·Diary·(+)·Train·You` with `LocalDiaryDate`; `AddActionSheet`. You tab (`feature/you`), Settings sub-route shells (Targets/Notifications/Data currently reuse SettingsRoute; About real).
