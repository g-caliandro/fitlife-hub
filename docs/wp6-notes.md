# WP6 Achievements notes
- Room v2 (Migration 1->2, schema 2.json). `Achievement` entity ids are Strings (UUID) like the rest of the app; exercise/goal/session links are plain nullable strings (no FKs). Deleting a goal/session removes its achievements.
- `domain/AchievementEngine` (pure): liveHits, sessionPrs, prs, milestones, perfectWeeks/Months, proteinWeeks, goals, `backfill`. `AchievementRepository`: onSessionFinished, detectPeriods (app start + daily check), recalculate (import of files without achievements).
- User-deleted achievements are remembered (prefs `dismissedAchievementKeys`) so detection does not recreate them; recalculate clears that.
- Export schemaVersion 2 (`achievements`, settings.notifyAchievements); v1 files import. CSV `achievements.csv`.
- Notifications: NotifType.ACHIEVEMENT_MEMORY (channel "Achievements"), rules in NotificationRules.achievementMemory (unlocked > anniversary > occasional PR memory; 1/day, anniversaries/memories <=2/week, unlocked bypasses weekly cap). Sent dates/notified keys kept in prefs.
- UI: route `achievements`, Home "Recent achievements" card, trophy icon in Goals top bar, live "New PR" pill (PrBannerOverlay) + vibration.
- Mock: backfill included in snapshot; forced perfect month (last full month) and protein week (3 weeks back); extra achieved goal "10 strict pull-ups" (63 days ago).
