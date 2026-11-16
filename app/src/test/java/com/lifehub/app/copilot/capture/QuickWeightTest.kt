package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.copilot.notify.ActionKind
import com.lifehub.app.copilot.stt.SttResult
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.CaptureStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/** Phase 5: the outcome notification of a quick (voice) capture for a body weight, with Undo, and the day when not today. */
@RunWith(RobolectricTestRunner::class)
class QuickWeightTest {
    @get:Rule val tmp = TemporaryFolder()
    private val f = CopilotFixture(seed = false)
    @After fun tearDown() = f.close()

    private fun run(spoken: String, args: String): Pair<VoiceFixture, String> = runBlocking {
        val llm = StepLlmClient({ tools(call("c1", "log_body_weight", args)) }, { text("Fatto.") })
        val v = VoiceFixture(f, llm, FakeTranscriber(SttResult.Ok(spoken)), tmp.newFolder())
        val cap = v.voice.saveQuick(v.newAudio())
        v.drainWorker()
        v to cap.id
    }

    @Test fun weightOutcomeNotificationWithUndoThenUndoRestoresTheDb() = runBlocking {
        val before = dumpTables(f.db)
        val (v, id) = run("Stamattina pesavo 74,2", """{"weight_kg":74.2}""")
        assertEquals(CaptureStatus.DONE, f.captures.get(id)!!.status)
        val n = v.sink.visible.getValue(CopilotNotificationContentTag.of(id))
        assertEquals("Weight logged · 74.2 kg", n.title)
        assertEquals(listOf(ActionKind.UNDO), n.actions.map { it.kind })

        v.actions.undo(id)
        assertEquals(before, dumpTables(f.db))
        assertTrue("the notification is removed after Undo", v.sink.visible.isEmpty())
    }

    @Test fun aWeightFromYesterdayNamesTheDayAndKeepsMuscleAndFat() = runBlocking {
        f.body.upsert(BodyEntry(LocalDate.of(2026, 11, 15), 77.4, 35.0, 18.0, Instant.parse("2026-11-15T07:00:00Z")))
        val before = dumpTables(f.db)
        val (v, id) = run("Ieri pesavo 76,9", """{"weight_kg":76.9,"date":"2026-11-15"}""")
        assertEquals("Weight logged · 76.9 kg · yesterday", v.sink.visible.getValue(CopilotNotificationContentTag.of(id)).title)
        assertEquals(35.0, f.body.get(LocalDate.of(2026, 11, 15))!!.muscleKg!!, 0.0)
        v.actions.undo(id)
        assertEquals(before, dumpTables(f.db))
    }
}

private object CopilotNotificationContentTag { fun of(id: String) = com.lifehub.app.copilot.notify.CopilotNotificationContent.captureTag(id) }
