package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.notify.ActionKind
import com.lifehub.app.copilot.notify.CopilotChannel
import com.lifehub.app.copilot.notify.CopilotNotificationContent
import com.lifehub.app.copilot.notify.NotificationCenter
import com.lifehub.app.copilot.stt.SttResult
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Design doc §7/§12: a pending request is resolved from three places (notification action, inbox, chat) and answering in
 * one closes it in the others, including its notification; it survives an ignored notification and an app restart; after
 * the answer the new entry joins the same meal.
 */
@RunWith(RobolectricTestRunner::class)
class PendingResolutionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val f = CopilotFixture(seed = false)
    private lateinit var llm: StepLlmClient
    private lateinit var v: VoiceFixture
    private lateinit var cap: Capture
    private lateinit var request: PendingInput
    private var riceRef = ""

    @Before fun setUp() = runBlocking {
        f.baseFoods.ensureSeeded()
        // "90 g di riso basmati e 100 g di polpette della nonna Pina": the meatballs need an answer, so NOTHING is written yet
        // (all or nothing); this model also tries log_meal in the same round and the app holds it back.
        llm = StepLlmClient(
            { tools(call("c1", "resolve_food", """{"name":"riso basmati"}""")) },
            { m ->
                riceRef = Json.parseToJsonElement((m.last() as LlmMessage.Tool).content).jsonObject["food"]!!.jsonObject["ref"]!!.jsonPrimitive.content
                tools(
                    call("c2", "log_meal", """{"items":[{"ref":"$riceRef","grams":90}]}"""),
                    call("c3", "request_user_input", """{"kind":"use_estimate","prompt":"Non trovo le polpette della nonna Pina. Uso una stima?",
                        "options":[{"label":"Polpette (stima)","estimate":{"kcal_100g":220,"protein_100g":14,"carbs_100g":8,"fat_100g":15}}],
                        "item":{"name":"polpette della nonna Pina","grams":100}}"""),
                )
            },
        )
        v = VoiceFixture(f, llm, FakeTranscriber(SttResult.Ok("90 g di riso basmati e 100 g di polpette della nonna Pina")), tmp.newFolder())
        cap = v.voice.saveQuick(v.newAudio())
        v.drainWorker()
        request = f.pending.byCapture(cap.id).single()
    }
    @After fun tearDown() = f.close()

    private val pendingTag get() = CopilotNotificationContent.pendingTag(request.id)
    private fun status() = runBlocking { f.pending.get(request.id)!!.status }
    private fun inboxOpen() = runBlocking { f.pending.observeOpen().first().map { it.id } }
    private fun chatCardState() = runBlocking { f.pending.observeByThread(cap.threadId!!).first().single { it.id == request.id }.status }
    private fun entries() = runBlocking { f.db.foodEntryDao().byCapture(cap.id) }

    @Test fun waitingCaptureShowsOutcomeAndOneQuestion() = runBlocking {
        assertEquals(CaptureStatus.AWAITING_INPUT, f.captures.get(cap.id)!!.status)
        assertTrue("nothing is written while a question is open", entries().isEmpty())
        val shown = v.sink.visible.values.toList()
        assertEquals(listOf(CopilotChannel.NEEDS_ANSWER), shown.map { it.channel })
        val q = shown.last()
        assertEquals(listOf(ActionKind.CHOOSE, ActionKind.REPLY), q.actions.map { it.kind })
        assertNotNull(f.pending.get(request.id)!!.notifiedAt)
        // Publishing again (another run, a restart) never re-posts the same question.
        v.center.publish(cap.id); v.center.publish(cap.id)
        assertEquals(1, v.sink.posted.count { it.channel == CopilotChannel.NEEDS_ANSWER })
    }

    @Test fun answeredFromTheNotificationClosesItInInboxAndChatAndJoinsTheSameMeal() = runBlocking {
        // Notification "Polpette (stima)" action, app closed: DB-only change, the turn is queued for the worker.
        assertTrue(v.actions.answer(request.id, option = 0))
        assertEquals(PendingStatus.ANSWERED, status())
        assertFalse(pendingTag in v.sink.visible)
        assertTrue(inboxOpen().isEmpty())
        assertEquals(PendingStatus.ANSWERED, chatCardState())
        assertEquals(CaptureStatus.PROCESSING, f.captures.get(cap.id)!!.status)
        assertEquals(listOf(cap.id), v.enqueued)
        // Too late in the other places: nothing happens twice.
        assertFalse(v.actions.answer(request.id, option = 0))
        assertThrows(IllegalTransitionException::class.java) { runBlocking { v.processor.answer(request.id, option = 0) } }
        assertEquals(listOf(cap.id), v.enqueued)

        // The worker resumes the saved capture: the estimate joins the same meal (capture, date and time).
        val ref = PendingCodec.options(f.pending.get(request.id)!!).single().ref!!
        llm.add { tools(call("c4", "log_meal", """{"items":[{"ref":"$riceRef","grams":90},{"ref":"$ref","grams":100}]}""")) }
        llm.add { text("Registrato il pranzo.") }
        assertEquals(CaptureStatus.DONE, v.drainWorker().single().status)
        val all = entries()
        assertEquals(2, all.size)
        assertEquals("one meal: same date and time", 1, all.map { it.loggedAt to it.date }.distinct().size)
        val outcome = v.sink.visible.values.single()
        assertEquals(CopilotChannel.OUTCOME, outcome.channel)
        assertTrue(outcome.title, outcome.title.startsWith("Logged 2 items"))
    }

    @Test fun answeredFromTheInboxClosesItInChatAndNotification() = runBlocking {
        // Inbox "Reply" (InboxViewModel -> CopilotActions).
        assertTrue(v.actions.answer(request.id, text = "lascia stare le polpette"))
        assertEquals(PendingStatus.ANSWERED, chatCardState())
        assertFalse(pendingTag in v.sink.visible)
        assertTrue(inboxOpen().isEmpty())
        // The notification's Choose arrives afterwards: refused, nothing queued twice.
        assertFalse(v.actions.answer(request.id, option = 0))
        assertEquals(1, v.enqueued.size)
        llm.add { tools(call("c4", "log_meal", """{"items":[{"ref":"$riceRef","grams":90}]}""")) }
        llm.add { text("Registrato solo il riso.") }
        v.drainWorker()
        assertEquals(CaptureStatus.DONE, f.captures.get(cap.id)!!.status)
        assertEquals(1, entries().size)
    }

    @Test fun answeredInTheChatClosesItInInboxAndNotification() = runBlocking {
        // Chat card "Choose" (ChatViewModel.answer -> CaptureProcessor.answer): resumes in place.
        val ref = PendingCodec.options(request).single().ref!!
        llm.add { tools(call("c4", "log_meal", """{"items":[{"ref":"$riceRef","grams":90},{"ref":"$ref","grams":100}]}""")) }
        llm.add { text("Fatto.") }
        v.processor.answer(request.id, option = 0)
        assertEquals(PendingStatus.ANSWERED, status())
        assertTrue(inboxOpen().isEmpty())
        assertFalse(pendingTag in v.sink.visible)
        assertFalse("notification action after the chat answer", v.actions.answer(request.id, option = 0))
        assertTrue(v.enqueued.isEmpty())
        assertEquals(2, entries().size)
    }

    @Test fun discardedFromTheInboxFinishesTheCapture() = runBlocking {
        assertTrue(v.actions.discard(request.id))
        assertEquals(PendingStatus.DISCARDED, status())
        assertFalse(pendingTag in v.sink.visible)
        // Dropping the last open question resumes the capture: the rest of the meal is logged now (nothing was before).
        assertEquals(CaptureStatus.PROCESSING, f.captures.get(cap.id)!!.status)
        assertEquals(listOf(cap.id), v.enqueued)
        assertTrue(entries().isEmpty())
        llm.add { tools(call("c4", "log_meal", """{"items":[{"ref":"$riceRef","grams":90}]}""")) }
        llm.add { text("Registrato il riso.") }
        assertEquals(CaptureStatus.DONE, v.drainWorker().single().status)
        assertEquals("rice logged after the dropped item", 1, entries().size)
        assertFalse(v.actions.discard("unknown"))
    }

    @Test fun requestSurvivesAnIgnoredNotificationAndAnAppRestart() = runBlocking {
        // The user swipes the notification away: the request stays open.
        v.sink.cancel(pendingTag)
        assertEquals(PendingStatus.OPEN, status())
        // App restart: new store instances over the same DB.
        val pending2 = PendingInputStore(f.db)
        val captures2 = CaptureStore(f.db)
        assertEquals(listOf(request.id), pending2.observeOpen().first().map { it.id })
        assertEquals(1, pending2.observeOpenCount().first())
        assertEquals(CaptureStatus.AWAITING_INPUT, captures2.get(cap.id)!!.status)
        val sink2 = RecordingSink()
        NotificationCenter(captures2, pending2, { f.db.foodEntryDao().byCapture(it) }, sink2, { true }, f.time).publish(cap.id)
        assertTrue("never re-posted after a restart", sink2.posted.none { it.channel == CopilotChannel.NEEDS_ANSWER })
        // Still answerable after the restart.
        assertEquals(PendingStatus.ANSWERED, pending2.answer(request.id, PendingAnswer(option = 0), f.now).status)
    }

    @Test fun undoClosesTheOpenRequestAndItsNotification() = runBlocking {
        v.actions.undo(cap.id)
        assertEquals(PendingStatus.DISCARDED, status())
        assertTrue(v.sink.visible.isEmpty())
        assertTrue(entries().isEmpty())
        assertFalse(v.actions.answer(request.id, option = 0))
    }
}
