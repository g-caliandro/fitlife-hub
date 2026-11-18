# Fitness Copilot — owner decisions (2026-11-16)

Approved by Gabriele after phase 0. These override the design doc and the phase 0 files where they differ.

1. **No meal type.** The app has no breakfast/lunch/dinner split and none is added. A "meal" is the set of entries created by one capture. `log_meal` has no `meal_type` argument.
2. **Logs store date AND time.** Meal entries created by the copilot carry a timestamp (date + time), not just a date. This needs a new column on the meal log table in the v9 migration (nullable for existing rows). Body weight stays one row per date.
3. **Plan drafts**: separate `plan_drafts` table. A draft = optional goal with deadline + weekday plans. No per-week variation; the existing auto-progression handles progress.
4. **Approve**: existing plans on the same weekdays are archived only if the user ticks an opt-in checkbox.
5. **Weight on a day that already has one**: overwrite the weight (keep muscle/fat); Undo restores the previous row.
6. **Chat entry point**: badge icon in the Home header plus an item in the "+" sheet. No new tab.
7. **Export**: include portions, household measures, user memory and entry provenance; exclude chats, captures, audio and API keys.
8. **Undo** is strict: DB identical to before, including cached foods and `lastUsedAt`.
9. **Language**: English UI like the rest of the app; the copilot replies in Italian.
10. **"Il solito" with several matches**: most recent wins only on a strong match, otherwise open a pending request.
11. **Open Food Facts products missing macros**: flag as incomplete instead of treating 0 as real.
12. **Future dates**: rejected by the write tools.
13. **Base food table**: USDA SR Legacy (not CREA), ~200 curated foods with Italian names, bundled as an asset. Confirm the licence on the official page before bundling.
14. **"Needs an answer" notification**: 2 quick choices + inline "Reply" (Android's 3-action limit).
15. **API keys**: AES-GCM with a key in the Android Keystore, ciphertext stored under `noBackupFilesDir`. Keys never enter `Settings`/SharedPreferences or the JSON export.
16. **Google backup: leave as is for now.** Do NOT add `dataExtractionRules`/`fullBackupContent` and do not change `allowBackup`. Keys stay out of backup only by living in `noBackupFilesDir`.
17. **Provider defaults** (editable in settings): LLM = OpenRouter `https://openrouter.ai/api/v1`, model `anthropic/claude-haiku-5.5`; STT = OpenRouter (same key as the LLM, used when no STT key is saved), model `openai/whisper-large-v3-turbo`, language `it` (changed from Groq on 2026-11-18: one provider, DeepInfra route about $0.011/h vs about $0.04/h; Groq stays selectable by URL + key).
18. **Recording** happens from the visible capture activity. No foreground service; the manifest note forbidding them stays respected.
19. **No new dependencies** (per `02-architecture.md`), unless a phase proves one unavoidable — then stop and report.
20. **Household measure values** must be validated by Gabriele before phase 3 inserts them.
