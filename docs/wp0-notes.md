# WP0 Foundation notes

## Public APIs
- Theme: `LhTheme { }`, `LhTheme.colors` (LhColors), `LhSpacing`, `LhRadius`, `LhSize`, `LhType` (`LhType.captionMuted` is @Composable; other styles have no color).
- Components (`core/designsystem/components`): LhCard, LhButton/LhIconButton, LhTextField, LhNumberField, LhStepper, LhBadge, LhProgressBar, LhSegmentedControl, LhTopBar, LhEmptyState, LhSectionHeader, LhStatTile (+DeltaGood), LhDatePickerField, LhTimePickerField, LhBottomSheet, LhConfirmDialog, LhTypedConfirmDialog, LhChip, LhListRow, LhSwitchRow. Exact APIs as in 03-design. `filterNumber(input, decimals)` is public.
- Shared UI (`core/ui`): GoalCard (+GoalCardModel has extra optional `timeCaption`), ExercisePickerSheet, WeekdayChips, `PlaceholderScreen(title, onBack, actions)`.
- Format.kt: fmtKg, fmtDelta, fmtInt, fmtWeight, fmtDate(d, currentYear=Year.now()), fmtDateFull, fmtRange, fmtDuration, fmtClock, fmtTarget, fmtSet.
- Container: `context.container` -> AppContainer(time, db, settings, goals, plans, exercises, sessions, macros, body, backup, appScope). Use `XxxViewModel.factory(c)` with `viewModelFactory { initializer { ... createSavedStateHandle() } }` (no extra helper added).
- Navigation: `Routes.*` patterns + builders (home, goal, goalEdit, plan, planEdit, exerciseEdit, workoutStart, workoutLog, session, macroEdit, bodyEdit); `Routes.BACK`, `Routes.replace(route)` for onNavigate; `LocalSnackbarHostState` (navigation/AppScaffold.kt). Date args are epochDay Long default -1; nullable string args default null. `DeepLinks.EXTRA_ROUTE/intent/pendingIntent`.
- Domain: Periods, DateRange, timeProgress, sessionsDone, planProgress, autoKcal, fatPct. `Channels.ensure`; `NotificationScheduler.schedule` is a no-op stub (WP5).
- Placeholders: every `feature/<pkg>/*Screen.kt` Route stub; owners overwrite whole file.
- Strings: shell strings in `strings_shell.xml`.

## Deviations
- No RestTimerService, no FOREGROUND_SERVICE* permissions, no service in manifest (lead override). VIBRATE + POST_NOTIFICATIONS kept. Rest timer = in-app countdown from `WorkoutSession.restEndsAt`.
- Added `SettingsRepository.setTimezone`; ActiveWorkoutBar text is "Workout · plan · mm:ss" (shortened to avoid truncation).
- ShellScreenshotTest renders the stateless bar/placeholder (not the full NavHost) in `screens/`; DesignSystemScreenshotTest in `screenshots/`.
- `home` deep-link route with null period omits the param.
