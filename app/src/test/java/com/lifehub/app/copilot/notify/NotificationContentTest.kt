package com.lifehub.app.copilot.notify

import com.lifehub.app.copilot.capture.PendingCodec
import com.lifehub.app.copilot.capture.PendingItem
import com.lifehub.app.copilot.capture.PendingOption
import com.lifehub.app.copilot.capture.Per100
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.model.PendingStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Pure content builders of the three copilot notifications (design doc §11, owner decision 14). */
class NotificationContentTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private fun capture(status: CaptureStatus = CaptureStatus.DONE, outcome: String? = null, error: String? = null, transcript: String? = "90 g di riso") =
        Capture("c1", "t1", ChatOrigin.QUICK, status, null, transcript = transcript, zoneId = "Europe/Rome", outcome = outcome,
            errorMessage = error, createdAt = now, updatedAt = now)
    private fun entry(name: String, grams: Double, kcal: Double) =
        FoodEntry(name, LocalDate.of(2026, 11, 16), null, name, null, grams, kcal, 1.0, 1.0, 1.0, now, captureId = "c1")
    private fun pending(kind: PendingKind, options: List<PendingOption>) = PendingInput(
        "p1", "c1", "t1", kind, "Quale pane intendi?", PendingCodec.encodeOptions(options),
        PendingCodec.encodeItem(PendingItem("pane", grams = 50.0)), null, PendingStatus.OPEN, null, now, null,
    )

    @Test fun outcomeListsWhatWasLoggedWithTotalKcalAndUndo() {
        val n = CopilotNotificationContent.outcome(capture(outcome = "Registrato."), listOf(entry("Riso", 90.0, 328.5), entry("Olio", 9.0, 79.56)), "copilot?threadId=t1")
        assertEquals(CopilotChannel.OUTCOME, n.channel)
        assertEquals("Logged 2 items · 408 kcal", n.title)
        assertEquals("Riso 90 g, Olio 9 g\nRegistrato.", n.text)
        assertEquals(listOf(NotificationAction(ActionKind.UNDO, "Undo")), n.actions)
        assertEquals("copilot-capture:c1", n.tag)
        assertEquals("copilot?threadId=t1", n.route)
        assertEquals("Logged 1 item · 329 kcal", CopilotNotificationContent.outcome(capture(), listOf(entry("Riso", 90.0, 328.5)), "r").title)
    }

    @Test fun outcomeNamesTheDayWhenItIsNotToday() {
        val today = LocalDate.of(2026, 11, 16)
        fun at(d: LocalDate) = entry("Salmone", 200.0, 400.0).copy(date = d)
        assertEquals("Logged 1 item · 400 kcal", CopilotNotificationContent.outcome(capture(), listOf(at(today)), "r", today = today).title)
        assertEquals("Logged 1 item · 400 kcal · yesterday", CopilotNotificationContent.outcome(capture(), listOf(at(today.minusDays(1))), "r", today = today).title)
        assertEquals("Logged 1 item · 400 kcal · Fri 13 Nov", CopilotNotificationContent.outcome(capture(), listOf(at(today.minusDays(3))), "r", today = today).title)
    }

    @Test fun weightOutcomeShowsValueAndDayWithUndo() {
        val today = LocalDate.of(2026, 11, 16)
        val w = BodyEntry(today, 74.2, null, null, now)
        val n = CopilotNotificationContent.outcome(capture(), emptyList(), "r", listOf(w), today)
        assertEquals("Weight logged · 74.2 kg", n.title)
        assertEquals(listOf(NotificationAction(ActionKind.UNDO, "Undo")), n.actions)
        assertEquals("Weight logged · 74.0 kg · yesterday", CopilotNotificationContent.outcome(capture(), emptyList(), "r", listOf(w.copy(date = today.minusDays(1), weightKg = 74.0)), today).title)
        // A meal and a weight in one capture: meal title, weight line in the text, one Undo.
        val both = CopilotNotificationContent.outcome(capture(), listOf(entry("Riso", 90.0, 328.5)), "r", listOf(w), today)
        assertEquals("Logged 1 item · 329 kcal", both.title)
        assertTrue(both.text, both.text.contains("Weight 74.2 kg"))
        assertEquals(1, both.actions.size)
    }

    @Test fun outcomeWithoutEntriesShowsTheReplyAndNoUndo() {
        val n = CopilotNotificationContent.outcome(capture(outcome = "Ti mancano 99 g di proteine."), emptyList(), "r")
        assertEquals("Ti mancano 99 g di proteine.", n.text)
        assertTrue(n.actions.isEmpty())
    }

    @Test fun needsAnswerHasTwoQuickChoicesPlusReplyAtMost() {
        val options = listOf(
            PendingOption("Pane comune", "base:1"), PendingOption("Pane integrale di segale a lievitazione naturale", "base:2"),
            PendingOption("Pane in cassetta", "base:3"), PendingOption("Stima", estimate = Per100(270.0, 9.0, 50.0, 3.0)),
        )
        val n = CopilotNotificationContent.needsAnswer(pending(PendingKind.CHOOSE_FOOD, options), "copilot/inbox")
        assertEquals(CopilotChannel.NEEDS_ANSWER, n.channel)
        assertEquals("Copilot needs an answer: pane", n.title)
        assertEquals("Quale pane intendi?", n.text)
        assertEquals(3, n.actions.size)
        assertEquals(listOf(ActionKind.CHOOSE, ActionKind.CHOOSE, ActionKind.REPLY), n.actions.map { it.kind })
        assertEquals(listOf(0, 1, null), n.actions.map { it.option })
        assertEquals("Pane comune", n.actions[0].label)
        assertTrue(n.actions[1].label.length <= 24 && n.actions[1].label.endsWith("…"))
        assertEquals("p1", n.pendingId); assertEquals("copilot-pending:p1", n.tag)

        val estimate = CopilotNotificationContent.needsAnswer(pending(PendingKind.USE_ESTIMATE, listOf(options[3])), "r")
        assertEquals(listOf("Stima (estimate)", "Reply"), estimate.actions.map { it.label })
        val quantity = CopilotNotificationContent.needsAnswer(pending(PendingKind.QUANTITY, emptyList()), "r")
        assertEquals(listOf(ActionKind.REPLY), quantity.actions.map { it.kind })
    }

    @Test fun errorShowsTheCauseAndRetry() {
        val n = CopilotNotificationContent.error(capture(CaptureStatus.FAILED, error = "Couldn't reach the transcription server. Check your connection.", transcript = null), "copilot/inbox")
        assertEquals(CopilotChannel.ERROR, n.channel)
        assertTrue(n.text.startsWith("Couldn't reach the transcription server."))
        assertTrue(n.text.contains("Your recording is saved."))
        assertEquals(listOf(ActionKind.RETRY), n.actions.map { it.kind })
        assertEquals("copilot-capture:c1", n.tag)
    }

    @Test fun neverMoreThanThreeActions() {
        val four = List(4) { NotificationAction(ActionKind.CHOOSE, "x", it) }
        assertThrows(IllegalArgumentException::class.java) {
            NotificationSpec("t", CopilotChannel.NEEDS_ANSWER, "a", "b", four, "c1", route = "r")
        }
    }
}
