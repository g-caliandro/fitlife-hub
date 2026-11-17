# Life Hub

Personal Android app (Kotlin, Jetpack Compose, Room). Build and test:

```
./gradlew :app:testDebugUnitTest --offline -q
./gradlew :app:assembleDebug --offline -q
./gradlew :app:recordRoborazziDebug --offline -q   # screenshots in app/build/screens/
./install.sh                                        # install on the phone (Windows adb.exe from WSL2)
```

Fitness Copilot docs: `docs/copilot/` (decisions, progress, phase 0 research).

## Fitness Copilot

An assistant inside the app (sparkle icon in the Home header, or "Ask copilot" in the "+" sheet) that talks to a language
model you configure in **You → Copilot** (your own API keys, stored encrypted on the phone, never exported). It replies in
Italian and only acts through app tools, so every number comes from the app:

- **Log meals and weight** by text or voice ("90 g di riso e 180 di pollo", "stamattina pesavo 74,2", "ieri sera..."):
  written immediately, with an **Undo** on the card or notification that restores the database exactly.
- **Open questions** (unknown or ambiguous food, missing amount or package weight) stay in the **Inbox** until answered
  from the chat, the inbox or the notification; an estimate is never logged unless you pick it.
- **Voice capture without opening the app** (side key → "Talk to copilot"), result as a notification.
- **Barcode in chat**: scan a product and say how much you ate.
- **Questions** about the diary, targets, goals, planned workouts and weight.
- **Meal suggestions** ("cosa preparo per cena?") from what is left today, computed by the app; **Log as meal** writes it
  with Undo. Foods listed under `disliked_foods` in the memory are never suggested.
- **Workout plan drafts** ("impostami 4 settimane verso il muscle up"): always a draft, never active until you **Approve**
  it (chat card or Train → Drafts). Approve can archive the plans on the same weekdays only if you tick the checkbox.
- **Memory** (You → Copilot → Memory): short facts the copilot keeps about you, saved only from the chat, editable,
  included in the data export.
- **Conversations**: the history icon in the chat lists past threads (open-question badge), opens, deletes or starts one.

## Copilot voice capture: phone setup

1. In the app: **You → Copilot**. Set the language model key, and the speech-to-text key (default Groq,
   `https://api.groq.com/openai/v1`, model `whisper-large-v3-turbo`, language `it`). Keep **Capture notifications** on.
2. Open **Talk to copilot** once (the second launcher icon, or long-press the Life Hub icon → *Talk to copilot*) and allow
   the microphone and notifications.
3. Samsung side key: **Settings → Advanced features → Side button → Double press → Open app**, then choose:
   - **Life Hub** and, if a gear icon is shown next to it, the in-app shortcut **Talk to copilot**; or
   - the **Talk to copilot** app entry (dedicated launcher icon), if the shortcut is not offered.

Using it: double press the side key, speak ("90 grammi di riso basmati, 180 di petto di pollo e un cucchiaio di olio"),
tap **Done** (or just stop talking for ~3 s) and lock the phone. The audio is saved immediately; the result arrives as a
notification (**Undo**), a question (**2 choices + Reply**) or an error (**Retry**). Everything is also in the chat
(each voice capture is a new thread) and open questions / failed captures are in **Copilot → Inbox**.

### To verify on the device (cannot be checked in the emulator-free build)

- Side key: is the gear / in-app shortcut offered for Life Hub? Does *Talk to copilot* (shortcut or launcher entry) start?
- With the phone locked: does the capture screen appear over the lock screen without unlocking, and does recording work?
- Locking the phone mid-recording saves what was said (the capture is processed anyway).
- Silence auto-stop: ~3 s after speaking (threshold may need calibration in `SilenceDetector`).
- With the app closed and the phone locked: the notification arrives; Undo, the 2 choices and inline Reply work from the
  lock screen; the question stays in the Inbox after dismissing the notification and after force-stopping the app.
- Airplane mode: an Error notification with Retry; Retry after reconnecting completes without duplicate diary entries.
- Groq accepts the `.m4a` recordings (AAC 16 kHz mono 32 kbps) and transcribes Italian correctly.

### Phase 5 checks on the device (barcode in chat, body weight, retroactive logs)

- Chat composer: the barcode button opens the scanner; a scanned product comes back as a chip above the field (remove with
  the X); "ne ho mangiati 30 grammi" logs that product, and the sent bubble shows the product name.
- Scan a product Open Food Facts does not know: the scanner says so and offers only "Scan again" (no custom food) in this mode.
- Scanning from Add food still opens the food detail as before.
- "Stamattina pesavo 74,2" (text and voice, also with the phone locked): the Weight card / notification shows the value, Undo
  restores the previous weight of that day (muscle and fat untouched) or removes a new day's row.
- "Ieri sera ho mangiato 200 g di salmone": the card and the notification say Yesterday, the diary shows it on yesterday.
- New voice captures are stored in the app's no-backup folder; recordings of earlier captures are still found and deleted.

### Phase 6 checks on the device (plan drafts, meal suggestion, memory, conversations)

- "Impostami 4 settimane verso il muscle up": a Plan draft card ("Not active"); Train shows it under "Drafts from the
  copilot"; the existing plans and Home's "today" workout are unchanged until Approve.
- Approve from the card and from the draft screen: with the checkbox unticked the old plans stay active; ticked, only the
  active plans sharing a weekday are archived. Reject / Delete leave every plan as it was.
- "Ricordati che non mi piacciono i funghi" in the chat saves a memory entry (You → Copilot → Memory); the same sentence by
  voice capture does not. Edit and delete an entry there (the edit dialog is not covered by automated tests).
- "Cosa preparo per cena?": Meal suggestion card with totals and coverage; Log as meal adds the items to today's diary
  with the same values; Undo removes them.
- Chat → history icon: threads with last update and badge; open an older thread, start a new one, delete one (its open
  questions disappear from the Inbox).

### Consolidated on-device checklist (all phases)

1. Copilot settings: Test connection succeeds with the real key; the key survives an app restart (Keystore).
2. Side key → Talk to copilot (shortcut or launcher entry), also with the phone locked; recording over the lock screen.
3. Lock mid-dictation: the partial recording is processed; silence auto-stop feels right.
4. App closed + phone locked: outcome notification with Undo; question notification with 2 choices + Reply.
5. Dismiss a question and force-stop the app: it is still in the Inbox; answering logs into the same meal.
6. Airplane mode: Error with Retry; Retry online finishes with no duplicate diary entries.
7. Groq transcribes Italian `.m4a` correctly; expedited work starts promptly and after a reboot.
8. Barcode in chat (pick mode), weight log + Undo, retroactive "ieri sera" on yesterday.
9. Plan draft never active before Approve; Approve with/without the archive checkbox; Reject; Delete from Train.
10. Meal suggestion → Log as meal → Undo; disliked foods respected.
11. Memory saved only from the chat, editable in settings, present in the JSON export (`userMemory`).
12. Conversations list: open, new, delete; badges match the Inbox.
