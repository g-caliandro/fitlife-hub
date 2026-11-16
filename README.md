# Life Hub

Personal Android app (Kotlin, Jetpack Compose, Room). Build and test:

```
./gradlew :app:testDebugUnitTest --offline -q
./gradlew :app:assembleDebug --offline -q
./gradlew :app:recordRoborazziDebug --offline -q   # screenshots in app/build/screens/
./install.sh                                        # install on the phone (Windows adb.exe from WSL2)
```

Fitness Copilot docs: `docs/copilot/` (decisions, progress, phase 0 research).

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
