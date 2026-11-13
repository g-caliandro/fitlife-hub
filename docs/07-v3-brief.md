# v3 brief — OpenNutriTracker-style restyle, Food logging, Focus-mode workout

User request (2026-11-12), translated:
1. Restyle the whole app strongly inspired by **OpenNutriTracker (ONT)** — "almost the same interface",
   with the raised center "+" button in the bottom bar to add food or activity.
2. Implement the **food** part now (log foods, not only daily macro totals).
3. Redesign the **live workout** into a focus mode: one current step full screen, not the whole list.
4. (Already done in UX v2: icon buttons, tonal buttons — keep that quality.)

Reference screenshots (ONT, Play Store set): /tmp/claude-1000/-home-gabri-workspace-personal-life-hub/3aa728a3-481e-450e-9a00-87089d5e9b59/scratchpad/ont/1.png … 6.png
(1 Home: weight chip, kcal ring "kcal left" with supplied/burned, 3 macro cards with colored bars, Activity
list, bottom bar Home·Diary·(+)·Trends·You with raised green circular +; 2 Diary: calendar card with dots,
day summary card "2897/3097 kcal" chip + 3 macro rings, collapsible "Today's nutrients"; 3 Trends: streak
card, 7d/30d/90d/All segmented, calories area chart with target dashed line, daily average bars;
4 You: avatar, big BMI circle, settings-like grouped list rows with tinted icon squares; 5 Diary meals:
Activity section with item cards (tinted icon square, title, subtitle, trailing kcal) + full-width "+" row,
meal sections; 6 nutrients expanded with colored progress bars.)

## Lead decisions
### Visual language (ONT-like)
- Warm off-white background (light ≈ #F6F4EF), white cards, radius 24dp, very soft 1dp border (#ECE8DF),
  no heavy shadows; green primary (≈ #43A047 light / #66BB6A dark); macro colors: carbs amber (#B8740F),
  fat red-orange (#D4553A), protein teal (#1F8A80); kcal = primary green. Dark theme: warm dark (#171614 bg,
  #222019 cards).
- Typography: bundle **Poppins** (OFL, res/font: regular/medium/semibold/bold) for titles/numbers, body
  can stay Poppins too. Screen title = leading icon + large title (like "📖 Diary"), no top-bar divider.
- List rows/items: card with a tinted rounded-square icon (primary 12% bg), title, subtitle, trailing value.
- Add rows: full-width soft "+" pill (muted bg) under each list section.
- Rings (Canvas) for kcal and macro mini-rings; thick round-cap strokes.
- Keep: day-type colors/icons (Pull/Push/Legs/Core), achievements, everything functional.

### Navigation
Bottom bar: **Home · Diary · (+) · Train · You**. Raised circular green "+" (56dp) in a notch.
- (+) opens a bottom sheet: **Add food**, **Quick macros**, **Start workout**, **Log weight**.
- Home = Today (kcal ring + macro cards + today's training card + weight chip + goals) and below the
  1W/2W history (charts + training stats) — keep the content the user already approved, restyled ONT-like.
- Diary = the calendar (ONT diary card) + selected-day summary (kcal chip + macro rings) + Food list +
  Workouts (collapsible cards with compliance %) + Body.
- Train = plans list (+ exercise library).
- You = profile-like hub: big circle with latest weight / body-fat %, rows: Goals, Achievements,
  Body history, Targets (protein/kcal/carbs/fat), Settings (notifications, export/import, mock data).

### Food
- User previously said: NO meal split (no breakfast/lunch/dinner). Keep a single per-day food list
  (no meal categories). Daily macros = sum(food entries) + quick-add macros (existing MacroDay becomes the
  "quick add" record for that day).
- Food entry: food ref/snapshot (name, brand, per-100g P/C/F/kcal), grams (or servings), computed macros,
  date, createdAt. Foods table: custom foods + cached Open Food Facts products (barcode, name, brand,
  per100g values, servingSize). Recents/favorites.
- Add-food flow (ONT-like): search screen with tabs Recent / Search (Open Food Facts text search, network)
  / My foods; barcode scan button (CameraX + ML Kit barcode scanning, bundled model); food detail →
  amount in g (quick chips 50/100/150/200 + serving) → live macros → Add (to selected date).
- Create custom food form. Edit/delete entry (swipe + undo).
- Targets: protein (existing) + optional kcal/carbs/fat targets in You → Targets. Kcal ring shows
  "kcal left" when a kcal target exists, otherwise "kcal eaten" with ring = protein progress.
- Export/import include foods + food entries (bump schemaVersion, keep older imports).
- Mock data: realistic food entries for most days (instead of only totals), plus some quick adds.
- Offline-safe: search failure shows a friendly error; recent/custom work offline. INTERNET permission.
  Use HttpURLConnection + kotlinx.serialization (no Retrofit). OFF API:
  search `https://world.openfoodfacts.org/cgi/search.pl?search_terms=<q>&search_simple=1&json=1&page_size=25&fields=code,product_name,brands,nutriments,serving_size`,
  barcode `https://world.openfoodfacts.org/api/v2/product/<code>.json?fields=...`; set a User-Agent
  "LifeHub/0.3 (Android)".

### Focus-mode live workout
- Full-screen, one step at a time. Top: segmented progress bar (one segment per set, grouped by exercise,
  completed = filled, skipped = muted, current = pulsing), "Exercise 2/6 · Set 3/4", elapsed time, close/
  overview icon (opens the full list as a sheet for editing: add exercise/set, reorder, jump to a set).
- Center: big **exercise icon** (128–160dp). Icon lookup `ExerciseIcons.forExercise(exercise)`: looks for a
  drawable named `ex_<slug>` (slug of exercise name, e.g. `ex_pull_up`) and falls back to a neutral
  placeholder (day-type icon in a big tinted circle). The user will later drop generated icons into
  res/drawable with those names — document the slug list in docs/exercise-icons.md.
- Below: exercise name, "Set 3 of 4", target big ("6 reps" / "MAX" / "20 s", "+10 kg"), previous value.
- Actions: **Done** (primary, large), **Skip**, **Adjust** (edit target/actual reps, seconds, weight for
  this set via steppers; MAX requires entering actual). SECONDS sets: "Start hold" countdown/stopwatch,
  then Done.
- Animations: on Done a quick check-mark burst + haptic, then the content slides out/in (AnimatedContent)
  to the rest screen; on Skip slide to next.
- Rest: after Done the rest timer starts automatically: big circular countdown, "Up next" preview with
  icon, ±15 s, Skip rest. When it reaches zero: play a **soft, round, non-shrill tone** (synthesized with
  AudioTrack: e.g. two sine partials ~392 Hz + 588 Hz, 350–500 ms, smooth attack 20 ms and exponential
  decay, low volume, USAGE_ASSISTANCE_SONIFICATION) + gentle vibration, and show **"Ready — next set"**
  that the user must acknowledge; acknowledging advances to the next step. Background: keep existing
  RestAlarm notification behavior.
- End: after the last set → summary (existing finish summary, restyled).
- Keep all persistence/resume/PR detection behavior.
