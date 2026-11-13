# Spec addendum — Achievements (WP6)

User request (late addition): motivational layer. Goals the user creates manually are the "big"
achievements; the app itself detects and saves "small" achievements automatically. Everything lands
in one achievements log; notifications resurface both ("on this day" anniversaries).

## F-ACH-1 Achievement entity
`Achievement(id, kind, title, detail, value: Double?, unit: String?, exerciseId: Long?, goalId: Long?,
sessionId: Long?, achievedOn: epochDay, createdAt: millis, dedupeKey: String UNIQUE)`
- `kind`: GOAL_ACHIEVED (big) | PR_REPS | PR_HOLD | PR_WEIGHT | PERFECT_WEEK_TRAINING |
  PERFECT_MONTH_TRAINING | PROTEIN_WEEK | SESSION_MILESTONE (big/small flag derived: GOAL_ACHIEVED = big).
- `dedupeKey` prevents duplicates (e.g. `PR_REPS:ex=12:value=12`, `PROTEIN_WEEK:2026-W45`,
  `PERFECT_MONTH:2026-10`, `GOAL:goalId`).
- Included in JSON/CSV export + import, cleared by "clear all data".

## F-ACH-2 Detection rules (pure functions, unit tested)
- **PR_REPS**: a completed REPS/MAX set whose actual reps > best previous actual reps for that exercise
  (any prior completed session), with same-or-higher added weight considered separately is NOT needed —
  keep simple: reps PR only counts sets with weight null/0. Requires ≥1 prior completed set of that
  exercise (first-ever set is not a PR). Title e.g. "12 pull-ups in one set".
- **PR_HOLD**: SECONDS set with actual seconds > previous best. Title "Front lever hold: 12 s".
- **PR_WEIGHT**: set with addedWeight > previous best addedWeight for that exercise (reps ≥ 1).
  Title "Weighted pull-up: +20 kg".
- Only the best value per exercise per session is stored (if 10 then 11 in same session → one PR of 11).
- **PERFECT_WEEK_TRAINING**: a completed Monday–Sunday week where every planned (plan, weekday) slot
  had a session and planned count ≥ 2.
- **PERFECT_MONTH_TRAINING**: same for a full calendar month (planned ≥ 4).
- **PROTEIN_WEEK**: completed week where all 7 days have macros logged and protein ≥ target each day.
- **SESSION_MILESTONE**: total completed sessions reaches 10, 25, 50, 100, 200…
- **GOAL_ACHIEVED**: when a goal is marked achieved (big). Un-marking deletes it.
- Period-based rules are evaluated for completed periods only (daily check + on app start), idempotent
  via dedupeKey. A "Recalculate achievements" (backfill from full history) runs after mock data
  generation and after import.

## F-ACH-3 In-workout celebration
When completing a set produces a PR (rules above, compared against history before the current
session and against earlier sets of the current session), show a short non-blocking celebration:
animated banner/toast at top of the live workout ("New PR · 12 pull-ups", trophy icon, scale+fade
~1.5 s, subtle confetti-free style consistent with minimal design) + success haptic. PR is persisted
when the session is finished (discarding the session discards its PRs).

## F-ACH-4 Achievements log screen
- Route `achievements`. Reachable from: Home ("Recent achievements" card with last 3 + "See all"),
  Goals tab top bar (trophy icon).
- Reverse-chronological list grouped by month; big achievements visually emphasized (accent, trophy);
  small ones compact rows with icon per kind. Filter chips: All / Goals / PRs / Consistency.
- Tap → detail sheet (date, value, link to session or goal). Long-press → delete with confirm.

## F-ACH-5 Notifications (motivational)
- New notification type ACHIEVEMENT_MEMORY (toggle in settings, default on), evaluated in the daily
  check, max 1/day, at most ~2 per week:
  - Anniversary: an achievement achieved exactly 1 month, 3 months, 6 months, 1 year (etc.) ago today
    → "1 year ago today: Muscle Up achieved 🎉". Big achievements prioritized.
  - Otherwise, occasionally (if none sent in last 7 days) a random past PR older than 30 days with
    progress context if available: "On 12 Mar you held a front lever for 8 s. Your best now: 15 s."
- New PRs / period achievements detected in the daily check (e.g. PROTEIN_WEEK, PERFECT_MONTH) send an
  immediate "Achievement unlocked" notification (counts toward the same type cap).
- Tap → opens achievements log.

## F-ACH-6 Mock data
Mock generator runs the backfill so the log contains realistic PRs, a perfect week/month, protein
weeks, session milestones; plus "Dragon Flag" left active and an older achieved goal (e.g. "10 strict
pull-ups", achieved ~9 weeks ago) to demo anniversaries.
