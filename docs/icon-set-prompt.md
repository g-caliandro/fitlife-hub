# Icon set — prompt per ChatGPT

**Come usarlo.** Apri una chat nuova con ChatGPT (generazione immagini). Incolla prima il blocco **"Style brief"** e
chiedi la contact sheet di tutto il set: serve solo a fissare lo stile. Se va bene, chiedi le icone **a lotti di 6–8**
(un lotto per sezione A1, A2, ...), ricordando ogni volta: *"same style as the contact sheet, one icon per file, SVG
(or 512x512 transparent PNG), filename = key"*. Se un'icona non torna, rigenerala da sola citandone la key. Per il grasso
chiedi entrambe le varianti (`metric_fat_a`, `metric_fat_b`) e scegli tu: quella scelta diventa `metric_fat`.
Alla fine metti i file in `docs/icons-raw/` (SVG preferiti, altrimenti PNG) con i nomi esatti delle key e dimmelo.

**Conversione in Android.** SVG → Android Studio › *New › Vector Asset › Local file*, nome = key (es. `plan_legs`),
size 24dp → finisce in `app/src/main/res/drawable/plan_legs.xml`. Togli eventuali `fillColor` bianchi/di sfondo; il
colore resta nero (`#000000`): l'app lo tinta a runtime. Se arrivano solo PNG, vanno in `drawable-nodpi/` con lo stesso
nome (peggio: niente scaling pulito). I nomi devono essere solo `[a-z0-9_]`.

---

## Style brief (paste first)

```
You are designing a complete, coherent ICON SET for "Life Hub", an Android fitness app (calisthenics workouts,
workout plans, daily macros, body weight, goals, calendar diary). The icons sit next to Google's Material Symbols
Outlined (Material Icons "Outlined", weight 400) and the Poppins font, so they MUST look like they belong to that
family.

Style rules (apply to every icon, no exceptions):
- Monochrome LINE icons, pure black (#000000) strokes on a fully TRANSPARENT background. They are tinted at runtime,
  so no colour, no grey, no gradients, no shadows, no 3D, no textures, no background shapes or circles.
- 24x24 grid, live area 20x20 (2px padding on every side), optical centring; all icons visually the same size.
- One stroke weight everywhere: 2px at 24px (≈ 43px at 512px). Rounded line caps and rounded joins. Corner radius
  ~2px on rectangles. Fills only for tiny solid dots (e.g. an eye or a head ≤ 3px), otherwise outline only.
- Minimal detail: must stay readable at 16–24dp. Max ~4–6 strokes per icon. No text, letters or numbers.
- Consistent perspective: objects flat/front or side view; human figures as simple stick/pictogram figures
  (round head + single-line limbs), side view where it shows the movement best — same figure style in every icon.
- Friendly, clean, slightly rounded geometry (like Material Symbols Outlined); not cartoonish, not anatomical.
- Body-area icons show the body part as a simple outline silhouette or a pictogram figure with that area emphasised
  by shape (never by colour or fill).

Deliverables:
1. First, ONE contact sheet: all icons in a grid on a white background, each with its key written small UNDER it
   (labels only on the contact sheet, never inside the icons).
2. Then each icon on its own, as SVG (single <path>/<g>, stroke="#000", fill="none", viewBox="0 0 24 24",
   stroke-width="2", stroke-linecap="round", stroke-linejoin="round"). If you cannot output SVG, give a 512x512 PNG
   with transparent background. File name = the key, e.g. plan_legs.svg.
```

## Icon list (paste after the brief, or batch by batch)

```
A1 — Workout plan icons: body areas
plan_legs        side-view bent leg / squat figure (thigh, shin, foot)
plan_arms        flexed arm, biceps curve
plan_shoulders   torso top with rounded deltoid caps, arrow up over head optional
plan_push        push-up figure on floor line (chest / push day)
plan_pull        horizontal bar on top, arrow pulling up toward it (back / pull day)
plan_core        torso outline with 6-pack segments (core / abs)
plan_glutes      side-view hip hinge / glute bridge figure
plan_full_body   standing figure, arms and legs spread (star pose)
plan_upper_body  torso + arms only, cut at the waist
plan_lower_body  hips + both legs only, cut at the waist

A2 — Workout plan icons: equipment & styles
plan_dumbbell    classic dumbbell, horizontal (DEFAULT icon, must be the most neutral of the set)
plan_kettlebell  kettlebell, front view
plan_barbell     barbell with two plates each side, horizontal
plan_pullup_bar  pull-up bar on two posts (calisthenics)
plan_rings       two gymnastic rings hanging from straps
plan_handstand   upside-down figure in handstand on a floor line
plan_skill       figure in planche / muscle-up position on a bar (skill work)
plan_hiit        stopwatch with a small lightning bolt inside
plan_mobility    figure in a deep lunge stretch, arm reaching up
plan_yoga        seated cross-legged figure, hands on knees
plan_run         running figure, mid-stride
plan_walk        walking figure (or a pair of footprints) — steps
plan_cycling     bicycle, side view
plan_swim        swimmer head + arm over two wave lines
plan_rest        crescent moon with one small 4-point star beside it

A3 — Nutrition / metric icons
metric_calories  small flame (single teardrop flame with an inner flick)
metric_protein   chicken drumstick (meat + bone end)
metric_fat_a     oil drop: one droplet with a small highlight curve inside
metric_fat_b     butter block: rectangular butter stick with a cut slice / knife line
metric_carbs     bread slice (rounded top crust, square bottom) or a small loaf with scoring
metric_training  same dumbbell language as plan_dumbbell but tilted 45° (training done/logged)

A4 — Diary monthly-summary status icons
Build them as: the base glyph (same as above, ~80% size) + a small badge in the bottom-right corner
(circle r≈4.5 with a 1.5px transparent gap cut around it) containing ×, ↑, ↓ or ✓ drawn with strokes.
status_missed_training   metric_training glyph + × badge
status_protein_missed    metric_protein glyph + × badge
status_calories_over     metric_calories glyph + ↑ badge
status_calories_under    metric_calories glyph + ↓ badge
status_streak            flame above three short horizontal dashes in a row ("days in a row"; must differ from
                         metric_calories at a glance)
status_goal_reached      flag on a pole + ✓ badge
status_perfect_day       star outline (all targets hit)
```

Fat: the **drop** (`metric_fat_a`) is the convention in most nutrition apps and reads best at 16dp; butter is
more literal but risks looking like cheese or a bar of soap. Default to the drop unless variant B is clearly better.

## Key table

| key | meaning |
|---|---|
| plan_dumbbell | generic strength / default plan icon |
| plan_legs | legs day |
| plan_arms | arms / biceps-triceps |
| plan_shoulders | shoulders |
| plan_push | chest / push day |
| plan_pull | back / pull day |
| plan_core | core / abs |
| plan_glutes | glutes / posterior chain |
| plan_full_body | full body |
| plan_upper_body | upper body |
| plan_lower_body | lower body |
| plan_kettlebell | kettlebell training |
| plan_barbell | barbell / weightlifting |
| plan_pullup_bar | calisthenics (bar) |
| plan_rings | gymnastic rings |
| plan_handstand | handstand practice |
| plan_skill | skill work (planche, muscle-up, levers) |
| plan_hiit | HIIT / timed circuits |
| plan_mobility | mobility / stretching |
| plan_yoga | yoga |
| plan_run | running / cardio |
| plan_walk | walking / steps |
| plan_cycling | cycling |
| plan_swim | swimming |
| plan_rest | rest / recovery day |
| metric_calories | calories (kcal) |
| metric_protein | protein |
| metric_fat | fat (chosen variant a or b) |
| metric_carbs | carbohydrates |
| metric_training | training session done |
| status_missed_training | planned training day missed |
| status_protein_missed | protein target missed |
| status_calories_over | calories over target |
| status_calories_under | calories under target |
| status_streak | streak (days in a row) |
| status_goal_reached | goal reached |
| status_perfect_day | all daily targets hit |
