# Exercise icons

The focus-mode live workout shows a big (140 dp) illustration for the current exercise. The app looks for a drawable named
`ex_<slug>` (`core/designsystem/ExerciseIcons.kt`, `ExerciseIcon(name, size)`); when none exists it falls back to a
tinted circle with the plan day-type icon (or a dumbbell). Drop files into `app/src/main/res/drawable/` (or
`drawable-nodpi/` for PNG) and they are picked up automatically, no code change.

## Slug rule
Lowercase the exercise name, replace every run of non-alphanumeric characters with `_`, trim `_`, prefix `ex_`.
`Pull-up` -> `ex_pull_up`, `L-sit` -> `ex_l_sit`. Custom exercises use the same rule on their name.

## Recommended asset spec
- Vector drawable XML (preferred, scales and tints) or 512 x 512 px PNG (`ex_<slug>.png`).
- Transparent background, no text, subject centered with ~8% padding.
- Single-color line style (about 6 px stroke at 512 px, round caps/joins), drawn in solid black: the app tints it with the
  theme foreground color, so it works in light and dark mode.
- Consistent figure style across the set (same stroke weight, athlete shown in side view where possible).
- File names must be lowercase `[a-z0-9_]` only (Android resource rule).

Suggested ChatGPT prompt: "Minimal single-color line icon, 512x512, transparent background, round line caps, 6px stroke,
athlete performing <exercise>, side view, no text, no shading."

## Exercises
There is no built-in exercise library: the user creates every exercise (category Pull, Push, Core or Legs). An icon is
optional and keyed by the exercise name, e.g. a "Front lever" exercise uses `ex_front_lever`.
