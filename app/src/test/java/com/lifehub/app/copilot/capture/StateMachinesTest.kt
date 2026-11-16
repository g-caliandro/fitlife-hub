package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.CaptureStatus.*
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.model.PendingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StateMachinesTest {
    private val f = CopilotFixture(seed = false)
    @After fun tearDown() = f.close()

    @Test fun captureTransitionTable() {
        val ok = setOf(
            RECORDED to PROCESSING, RECORDED to FAILED, RECORDED to CANCELLED,
            PROCESSING to DONE, PROCESSING to AWAITING_INPUT, PROCESSING to FAILED, PROCESSING to CANCELLED,
            AWAITING_INPUT to PROCESSING, AWAITING_INPUT to DONE, AWAITING_INPUT to CANCELLED,
            FAILED to RECORDED, FAILED to PROCESSING, FAILED to CANCELLED,
            DONE to CANCELLED,
        )
        for (from in CaptureStatus.entries) for (to in CaptureStatus.entries) {
            if (from == to) continue
            assertEquals("$from -> $to", (from to to) in ok, CaptureStateMachine.canMove(from, to))
        }
        assertFalse("CANCELLED is final", CaptureStatus.entries.any { CaptureStateMachine.canMove(CANCELLED, it) })
        assertEquals(setOf(PROCESSING), CaptureStatus.entries.filter(CaptureStateMachine::acceptsWrites).toSet())
    }

    @Test fun pendingTransitionTable() {
        assertTrue(PendingStateMachine.canMove(PendingStatus.OPEN, PendingStatus.ANSWERED))
        assertTrue(PendingStateMachine.canMove(PendingStatus.OPEN, PendingStatus.DISCARDED))
        listOf(PendingStatus.ANSWERED, PendingStatus.DISCARDED).forEach { from ->
            PendingStatus.entries.forEach { to -> assertFalse("$from -> $to", PendingStateMachine.canMove(from, to)) }
        }
    }

    @Test fun captureStoreEnforcesTransitions() = runBlocking {
        val c = f.captures.create("t", ChatOrigin.CHAT, "ciao", f.zone, f.now)
        assertEquals(RECORDED, c.status)
        assertEquals(1, f.captures.move(c.id, PROCESSING, f.now).attempts)
        f.captures.move(c.id, DONE, f.now, outcome = "ok")
        try { f.captures.move(c.id, PROCESSING, f.now); fail("DONE -> PROCESSING must be refused") } catch (_: IllegalTransitionException) {}
        f.captures.move(c.id, CANCELLED, f.now)
        try { f.captures.move(c.id, DONE, f.now); fail("CANCELLED is final") } catch (_: IllegalTransitionException) {}
        assertEquals("ok", f.captures.get(c.id)!!.outcome)
    }

    @Test fun pendingStoreAnswerDiscardAndBadge() = runBlocking {
        val c = f.captures.create("t", ChatOrigin.CHAT, "x", f.zone, f.now)
        val opts = listOf(PendingOption("A", ref = "base:a"), PendingOption("Stima", estimate = Per100(100.0, 5.0, 10.0, 2.0)))
        val p1 = f.pending.create(c.id, "t", PendingKind.CHOOSE_FOOD, "Quale?", opts, PendingItem("x", grams = 100.0), null, f.now)
        val p2 = f.pending.create(c.id, "t", PendingKind.QUANTITY, "Quanto?", emptyList(), PendingItem("y"), null, f.now)
        assertEquals(2, f.pending.observeOpenCount().first())

        try { f.pending.answer(p1.id, PendingAnswer(option = 5), f.now); fail("option out of range") } catch (_: IllegalTransitionException) {}
        try { f.pending.answer(p1.id, PendingAnswer(), f.now); fail("empty answer") } catch (_: IllegalTransitionException) {}
        val a = f.pending.answer(p1.id, PendingAnswer(option = 1), f.now)
        assertEquals(PendingStatus.ANSWERED, a.status); assertEquals(1, PendingCodec.answer(a)!!.option)
        // Answering from a second place (notification / inbox) after the chat: refused, the first answer stays.
        try { f.pending.answer(p1.id, PendingAnswer(option = 0), f.now); fail("already answered") } catch (_: IllegalTransitionException) {}
        try { f.pending.discard(p1.id, f.now); fail("answered is final") } catch (_: IllegalTransitionException) {}
        assertEquals(1, f.pending.observeOpenCount().first())

        assertEquals(PendingStatus.DISCARDED, f.pending.discard(p2.id, f.now).status)
        assertEquals(PendingStatus.DISCARDED, f.pending.discard(p2.id, f.now).status)   // idempotent
        assertEquals(0, f.pending.observeOpenCount().first())
        // Options and item survive the round trip.
        assertEquals(opts, PendingCodec.options(f.pending.get(p1.id)!!))
        assertEquals(PendingItem("x", grams = 100.0), PendingCodec.item(f.pending.get(p1.id)!!))
    }

    @Test fun discardingTheLastOpenRequestFinishesTheCapture() = runBlocking {
        val thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        val c = f.captures.create(thread, ChatOrigin.CHAT, "x", f.zone, f.now)
        f.captures.move(c.id, PROCESSING, f.now); f.captures.move(c.id, AWAITING_INPUT, f.now)
        val p1 = f.pending.create(c.id, thread, PendingKind.QUANTITY, "Quanto?", emptyList(), PendingItem("a"), null, f.now)
        val p2 = f.pending.create(c.id, thread, PendingKind.QUANTITY, "Quanto?", emptyList(), PendingItem("b"), null, f.now)
        val proc = f.processor(com.lifehub.app.copilot.FakeLlmClient())
        proc.discard(p1.id)
        assertEquals(AWAITING_INPUT, f.captures.get(c.id)!!.status)
        proc.discard(p2.id)
        assertEquals(DONE, f.captures.get(c.id)!!.status)
    }

    @Test fun parseGramsAnswer() {
        assertEquals(400.0, CaptureProcessor.parseGrams("400")!!, 0.0)
        assertEquals(400.0, CaptureProcessor.parseGrams("pesa 400 g")!!, 0.0)
        assertEquals(450.0, CaptureProcessor.parseGrams("0,45 kg")!!, 1e-9)
        assertEquals(null, CaptureProcessor.parseGrams("non lo so"))
    }
}
