package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.agent.TurnResult
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.copilot.llm.LlmError
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.copilot.notify.ActionKind
import com.lifehub.app.copilot.notify.CopilotChannel
import com.lifehub.app.copilot.notify.CopilotNotificationContent
import com.lifehub.app.copilot.stt.SttError
import com.lifehub.app.copilot.stt.SttResult
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.FoodEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * Phase 4 capture state machine through the CaptureWorker core ([CaptureRunner]) with a fake transcriber, fake model and
 * fake notification sink on a real in-memory DB. No network, no WorkManager (work-testing is not in the project).
 */
@RunWith(RobolectricTestRunner::class)
class CaptureRunnerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val f = CopilotFixture(seed = false)
    private val sentence = "Ho mangiato 90 grammi di riso basmati, 180 di petto di pollo e un cucchiaio di olio"

    @Before fun setUp() = runBlocking { f.baseFoods.ensureSeeded(); f.settings.setKcalTarget(2200) }
    @After fun tearDown() = f.close()

    private fun lastResults(msgs: List<LlmMessage>): List<JsonObject> =
        msgs.takeLastWhile { it is LlmMessage.Tool }.map { Json.parseToJsonElement((it as LlmMessage.Tool).content).jsonObject }
    private fun refs(msgs: List<LlmMessage>) = lastResults(msgs).map { it["food"]!!.jsonObject["ref"]!!.jsonPrimitive.content }
    private fun entries(): List<FoodEntry> = runBlocking { f.food.observeEntries(f.today.minusDays(30), f.today).first() }

    private fun resolveAll() = { _: List<LlmMessage> ->
        tools(call("c1", "resolve_food", """{"name":"riso basmati"}"""), call("c2", "resolve_food", """{"name":"petto di pollo"}"""), call("c3", "resolve_food", """{"name":"olio"}"""))
    }
    private fun logAll() = { m: List<LlmMessage> ->
        val r = refs(m)
        tools(call("c4", "log_meal", """{"items":[{"ref":"${r[0]}","grams":90},{"ref":"${r[1]}","grams":180},{"ref":"${r[2]}","measure":"cucchiaio"}]}"""))
    }
    private fun riceChickenOil() = StepLlmClient(resolveAll(), logAll(), { text("Registrato: riso, pollo e olio.") })

    private fun fixture(llm: StepLlmClient, vararg stt: SttResult) = VoiceFixture(f, llm, FakeTranscriber(*stt), tmp.newFolder())

    @Test fun phase3AcceptanceExampleByVoiceEndToEnd() = runBlocking {
        val before = dumpTables(f.db)
        val llm = riceChickenOil()
        val v = fixture(llm, SttResult.Ok(sentence))
        val audio = v.newAudio()
        // Quick capture screen: Done -> audio saved + capture RECORDED + queued, before anything else happens.
        val cap = v.voice.saveQuick(audio)
        assertEquals(CaptureStatus.RECORDED, f.captures.get(cap.id)!!.status)
        assertEquals(audio.absolutePath, f.captures.get(cap.id)!!.audioPath)
        assertEquals(listOf(cap.id), v.enqueued)
        val thread = f.chat.thread(cap.threadId!!)!!
        assertEquals(ChatOrigin.QUICK, thread.origin)

        // The worker (app closed): transcribe -> agent turn -> outcome.
        val report = v.drainWorker().single()
        assertEquals(CaptureStatus.DONE, report.status)
        assertEquals(TurnResult.Answered("Registrato: riso, pollo e olio."), report.turn)
        assertEquals("it", v.stt.calls.single().second)
        val e = entries()
        assertEquals(listOf(9.0, 90.0, 180.0), e.map { it.grams }.sorted())
        assertTrue(e.all { it.captureId == cap.id })

        // Quick capture tools: no chat-only tool was offered.
        assertTrue(llm.toolNames.all { "save_user_memory" !in it && "log_meal" in it })
        // The new thread holds the transcript and the outcome, so it is found in the chat.
        val msgs = f.chat.messages(thread.id)
        assertEquals(sentence, msgs.first().text); assertEquals(ChatRole.USER, msgs.first().role)
        assertEquals("Registrato: riso, pollo e olio.", msgs.last().text)
        assertEquals(CaptureRunner.threadTitle(sentence), f.chat.thread(thread.id)!!.title)
        assertEquals(sentence, f.captures.get(cap.id)!!.transcript)
        // Audio deleted once the capture is DONE (the transcript is stored).
        assertFalse(audio.exists()); assertNull(f.captures.get(cap.id)!!.audioPath)

        // Outcome notification: what was logged, total kcal, Undo.
        val n = v.sink.visible.values.single()
        assertEquals(CopilotChannel.OUTCOME, n.channel)
        assertEquals("Logged 3 items · ${e.sumOf { it.kcal }.let(Math::round)} kcal", n.title)
        assertEquals(listOf(ActionKind.UNDO), n.actions.map { it.kind })

        // Undo from the notification (app closed): DB identical to before, notification gone.
        v.actions.undo(cap.id)
        assertEquals(before, dumpTables(f.db))
        assertEquals(CaptureStatus.CANCELLED, f.captures.get(cap.id)!!.status)
        assertTrue(v.sink.visible.isEmpty())
    }

    @Test fun chatCapturesKeepChatOnlyToolsAndPostNoNotification() = runBlocking {
        val llm = StepLlmClient({ text("Ciao") })
        val v = fixture(llm, SttResult.Ok("ciao"))
        val thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        val cap = v.voice.saveInThread(thread, v.newAudio())
        assertTrue("chat voice is run in place, not queued", v.enqueued.isEmpty())
        val stages = mutableListOf<CaptureStage>()
        val r = v.runner.run(cap.id, notify = true) { stages += it }
        assertEquals(CaptureStatus.DONE, r.status)
        assertEquals(listOf(CaptureStage.TRANSCRIBING, CaptureStage.THINKING), stages)
        assertTrue("save_user_memory" in llm.toolNames.single())
        assertTrue(v.sink.posted.isEmpty())
        assertEquals("t", f.chat.thread(thread)!!.title)
    }

    @Test fun noNetworkKeepsTheCaptureWithItsAudioAndRetryFinishesIt() = runBlocking {
        val llm = riceChickenOil()
        val v = fixture(llm, SttResult.Failure(SttError.Network(timeout = false)), SttResult.Ok(sentence))
        val audio = v.newAudio()
        val cap = v.voice.saveQuick(audio)
        val r = v.drainWorker().single()
        assertEquals(CaptureStatus.FAILED, r.status)
        val failed = f.captures.get(cap.id)!!
        assertEquals("Stt.Network", failed.errorCode)
        assertTrue(audio.exists()); assertEquals(audio.absolutePath, failed.audioPath)
        assertNull(failed.transcript)
        assertEquals(0, llm.requests.size)
        assertTrue(f.chat.messages(cap.threadId!!).isEmpty())
        val err = v.sink.visible.values.single()
        assertEquals(CopilotChannel.ERROR, err.channel)
        assertEquals(listOf(ActionKind.RETRY), err.actions.map { it.kind })
        assertTrue(err.text.contains("Couldn't reach the transcription server"))

        // Retry (notification or inbox): back to RECORDED, queued, error notification removed; the worker finishes it.
        assertTrue(v.actions.retry(cap.id))
        assertEquals(CaptureStatus.RECORDED, f.captures.get(cap.id)!!.status)
        assertTrue(v.sink.visible.isEmpty())
        assertEquals(CaptureStatus.DONE, v.drainWorker().single().status)
        assertEquals(3, entries().size)
        assertEquals(2, v.stt.calls.size)
        assertEquals(CopilotChannel.OUTCOME, v.sink.visible.values.single().channel)
        assertFalse("retry of a non-failed capture is refused", v.actions.retry(cap.id))
    }

    @Test fun missingKeysEndInErrorWithoutLosingAnything() = runBlocking {
        // No STT key: nothing reaches the model.
        val v1 = fixture(StepLlmClient(), SttResult.Failure(SttError.MissingKey))
        val c1 = v1.voice.saveQuick(v1.newAudio())
        v1.drainWorker()
        assertEquals("Stt.MissingKey", f.captures.get(c1.id)!!.errorCode)
        assertTrue(v1.sink.visible.values.single().text.contains("No speech-to-text API key"))

        // No LLM key: the transcript is kept and the retry does not transcribe again nor duplicate the user message.
        val llm = StepLlmClient({ LlmResult.Failure(LlmError.MissingKey) })
        val v2 = fixture(llm, SttResult.Ok(sentence))
        val c2 = v2.voice.saveQuick(v2.newAudio())
        assertEquals(CaptureStatus.FAILED, v2.drainWorker().single().status)
        assertEquals(sentence, f.captures.get(c2.id)!!.transcript)
        assertTrue(f.captures.get(c2.id)!!.errorMessage!!.contains("No LLM API key"))
        llm.add(resolveAll()); llm.add(logAll()); llm.add { text("Fatto.") }
        v2.actions.retry(c2.id)
        assertEquals(CaptureStatus.DONE, v2.drainWorker().single().status)
        assertEquals(1, v2.stt.calls.size)
        assertEquals(1, f.chat.messages(c2.threadId!!).count { it.role == ChatRole.USER })
    }

    @Test fun retryAfterAPartialRunResumesWithoutDuplicatingLogs() = runBlocking {
        // The meal is written, then the model call fails (network): FAILED with the log already in the diary.
        val llm = StepLlmClient(resolveAll(), logAll(), { LlmResult.Failure(LlmError.Network(timeout = true)) })
        val v = fixture(llm, SttResult.Ok(sentence))
        val cap = v.voice.saveQuick(v.newAudio())
        assertEquals(CaptureStatus.FAILED, v.drainWorker().single().status)
        assertEquals(3, entries().size)
        val afterFirst = dumpTables(f.db)

        // Retry: the model logs the same items again -> already logged, nothing new written.
        llm.add(resolveAll()); llm.add(logAll())
        llm.add { m -> assertEquals(3, lastResults(m).single()["already_logged"]!!.jsonPrimitive.content.toInt()); text("Già registrato.") }
        v.actions.retry(cap.id)
        assertEquals(CaptureStatus.DONE, v.drainWorker().single().status)
        assertEquals(afterFirst, dumpTables(f.db))
        assertEquals(1, v.stt.calls.size)
        assertEquals(1, f.chat.messages(cap.threadId!!).count { it.role == ChatRole.USER })
        assertEquals(2, f.captures.get(cap.id)!!.attempts)
    }

    @Test fun processDeathMidTurnIsResumedByTheNextRun() = runBlocking {
        // Simulate a process killed after transcription and the user message, during the model turn.
        val v = fixture(StepLlmClient(resolveAll(), logAll(), { text("Ok.") }), SttResult.Ok("unused"))
        val cap = v.voice.saveQuick(v.newAudio())
        f.captures.setTranscript(cap.id, sentence, f.now)
        f.chat.append(cap.threadId!!, com.lifehub.app.data.repo.NewChatMessage(ChatRole.USER, sentence, captureId = cap.id), f.now)
        f.captures.move(cap.id, CaptureStatus.PROCESSING, f.now)
        // WorkManager runs the work again after the restart.
        val r = v.runner.run(cap.id)
        assertEquals(CaptureStatus.DONE, r.status)
        assertTrue(v.stt.calls.isEmpty())
        assertEquals(3, entries().size)
        assertEquals(1, f.chat.messages(cap.threadId!!).count { it.role == ChatRole.USER })
    }

    @Test fun emptyTranscriptAndFinalStatesAreHandled() = runBlocking {
        val v = fixture(StepLlmClient(), SttResult.Ok("   "))
        val cap = v.voice.saveQuick(v.newAudio())
        v.drainWorker()
        assertEquals(CaptureErrors.NO_SPEECH, f.captures.get(cap.id)!!.errorCode)
        // A FAILED capture waits for the user's Retry: a stray run does nothing and posts nothing new.
        val posted = v.sink.posted.size
        assertFalse(v.runner.run(cap.id).ran)
        assertEquals(posted, v.sink.posted.size)
        assertFalse(v.runner.run("unknown").ran)
        // Notifications switched off in settings: the capture still completes, nothing is posted.
        v.notificationsEnabled = false
        v.actions.retry(cap.id)
        v.drainWorker()
        assertEquals(posted, v.sink.posted.size)
    }

    @Test fun anUnexpectedCrashNeverLeavesTheCaptureProcessing() = runBlocking {
        val v = fixture(StepLlmClient(), SttResult.Ok("x"))
        val cap = v.voice.saveQuick(v.newAudio())
        f.captures.move(cap.id, CaptureStatus.PROCESSING, f.now)
        v.runner.failUnexpected(cap.id)
        assertEquals(CaptureStatus.FAILED, f.captures.get(cap.id)!!.status)
        assertEquals(CaptureErrors.UNEXPECTED, f.captures.get(cap.id)!!.errorCode)
        assertEquals(CopilotNotificationContent.captureTag(cap.id), v.sink.visible.keys.single())
    }
}
