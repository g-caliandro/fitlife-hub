package com.lifehub.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.copilot.capture.CaptureStage
import com.lifehub.app.copilot.capture.PendingCodec
import com.lifehub.app.copilot.capture.PendingItem
import com.lifehub.app.copilot.capture.PendingOption
import com.lifehub.app.copilot.capture.Per100
import com.lifehub.app.copilot.stt.RecordingLevel
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.feature.capture.QuickCaptureScreen
import com.lifehub.app.feature.capture.QuickCaptureState
import com.lifehub.app.feature.copilot.CardActions
import com.lifehub.app.feature.copilot.ChatItem
import com.lifehub.app.feature.copilot.ChatScreen
import com.lifehub.app.feature.copilot.ChatUiState
import com.lifehub.app.feature.copilot.InboxScreen
import com.lifehub.app.feature.copilot.InboxUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Phase 4 screens: quick capture, inbox, chat mic states. PNGs in app/build/screens/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class VoiceCaptureScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val now = Instant.parse("2026-11-16T12:40:00Z")
    private val zone = ZoneId.of("Europe/Rome")

    @Test fun quickCaptureRecordingLight() {
        var done = 0
        rule.setContent { LhTheme(darkTheme = false) { QuickCaptureScreen(QuickCaptureState.Recording(7_000, 0.6f), onDone = { done++ }) } }
        rule.onNodeWithTag("capture-recording").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/quick-capture-recording-light.png")
        rule.onNodeWithTag("capture-done").performClick()
        assertEquals(1, done)
    }

    @Test fun quickCaptureSavedDark() {
        rule.setContent { LhTheme(darkTheme = true) { QuickCaptureScreen(QuickCaptureState.Saved) } }
        rule.onNodeWithTag("capture-saved").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/quick-capture-saved-dark.png")
    }

    @Test fun quickCapturePermissionDenied() {
        rule.setContent { LhTheme(darkTheme = false) { QuickCaptureScreen(QuickCaptureState.PermissionDenied) } }
        rule.onNodeWithText("Open app settings").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/quick-capture-denied-light.png")
    }

    private val request = PendingInput(
        "p1", "c1", "t1", PendingKind.USE_ESTIMATE, "Non trovo le polpette della nonna Pina. Uso una stima?",
        PendingCodec.encodeOptions(listOf(PendingOption("Polpette (stima)", "estimate:p1:0", estimate = Per100(220.0, 14.0, 8.0, 15.0)))),
        PendingCodec.encodeItem(PendingItem("polpette della nonna Pina", grams = 100.0)), null, PendingStatus.OPEN, null, now, null,
    )
    private fun capture(id: String, status: CaptureStatus, transcript: String?, error: String? = null) = Capture(
        id, "t-$id", ChatOrigin.QUICK, status, null, audioPath = "/x.m4a", transcript = transcript, zoneId = zone.id,
        errorMessage = error, createdAt = now, updatedAt = now,
    )
    private val inbox = InboxUiState(
        requests = listOf(request),
        failed = listOf(capture("c2", CaptureStatus.FAILED, null, "Couldn't reach the transcription server. Check your connection.")),
        queued = listOf(capture("c3", CaptureStatus.RECORDED, "Uno yogurt greco e una banana")),
        loaded = true,
    )

    @Test fun inboxLight() {
        var retried: String? = null
        var chosen: Pair<String, Int>? = null
        rule.setContent { LhTheme(darkTheme = false) {
            InboxScreen(inbox, onBack = {}, zone = zone, actions = CardActions(onChoose = { p, i -> chosen = p to i }), onRetry = { retried = it })
        } }
        rule.onRoot().captureRoboImage("build/screens/copilot-inbox-light.png")
        rule.onNodeWithTag("inbox-retry").performClick()
        rule.onNodeWithTag("pending-option-0").performClick()
        assertEquals("c2", retried); assertEquals("p1" to 0, chosen)
    }

    @Test fun inboxDark() {
        rule.setContent { LhTheme(darkTheme = true) { InboxScreen(inbox, onBack = {}, zone = zone) } }
        rule.onRoot().captureRoboImage("build/screens/copilot-inbox-dark.png")
    }

    @Test fun inboxEmpty() {
        rule.setContent { LhTheme(darkTheme = false) { InboxScreen(InboxUiState(loaded = true), onBack = {}) } }
        rule.onNodeWithText("Nothing waiting").assertIsDisplayed()
    }

    @Test fun chatRecordingAndTranscribing() {
        val today = LocalDate.of(2026, 11, 16)
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(
                ChatUiState(today, listOf(ChatItem.User("u1", "Quante proteine mi mancano oggi?")), working = true, stage = CaptureStage.TRANSCRIBING,
                    inboxCount = 2),
                onBack = {},
            )
        } }
        rule.onNodeWithTag("chat-transcribing").assertIsDisplayed()
        rule.onNodeWithTag("chat-mic").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-chat-transcribing.png")
    }

    @Test fun chatComposerWhileRecording() {
        var stopped = 0
        rule.setContent { LhTheme(darkTheme = true) {
            ChatScreen(ChatUiState(LocalDate.of(2026, 11, 16), recording = RecordingLevel(4_000, 0.5f)), onBack = {}, onStopMic = { stopped++ })
        } }
        rule.onNodeWithTag("chat-recording").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-chat-recording-dark.png")
        rule.onNodeWithTag("chat-mic-stop").performClick()
        assertEquals(1, stopped)
    }
}
