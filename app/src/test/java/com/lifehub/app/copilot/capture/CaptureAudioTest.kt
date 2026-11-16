package com.lifehub.app.copilot.capture

import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.stt.CaptureAudioFiles
import com.lifehub.app.copilot.stt.SttResult
import com.lifehub.app.data.model.CaptureStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Phase 5 carry-overs from phase 4: audio out of Google backup (decision 16) and no audio left behind. */
@RunWith(RobolectricTestRunner::class)
class CaptureAudioTest {
    @get:Rule val tmp = TemporaryFolder()
    private val f = CopilotFixture(seed = false)

    @Before fun setUp() = runBlocking { f.baseFoods.ensureSeeded() }
    @After fun tearDown() = f.close()

    /** One unknown food: the model asks whether to use an estimate, so the capture ends AWAITING_INPUT. */
    private fun asksOne() = StepLlmClient(
        { tools(call("c1", "request_user_input", request("polpette"))) },
    )
    private fun request(name: String) = """{"kind":"use_estimate","prompt":"Uso una stima per $name?",
        "options":[{"label":"$name (stima)","estimate":{"kcal_100g":220,"protein_100g":14,"carbs_100g":8,"fat_100g":15}}],"item":{"name":"$name","grams":100}}"""
    private fun asksTwo() = StepLlmClient(
        { tools(call("c1", "request_user_input", request("polpette")), call("c2", "request_user_input", request("frittata"))) },
    )
    private fun fixture(llm: StepLlmClient) = VoiceFixture(f, llm, FakeTranscriber(SttResult.Ok("100 g di polpette")), tmp.newFolder())

    @Test fun newCapturesAreRecordedUnderNoBackupFilesDirNotFilesDir() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = CaptureAudioFiles.forContext(ctx).newFile()
        assertEquals(File(ctx.noBackupFilesDir, "captures").absolutePath, file.parentFile!!.absolutePath)
        assertFalse(file.absolutePath.startsWith(ctx.filesDir.absolutePath + File.separator))
        assertTrue(file.parentFile!!.isDirectory)
    }

    @Test fun aCaptureWhoseAudioIsAtTheOldPathIsStillProcessedAndItsFileDeleted() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val old = File(ctx.filesDir, "captures").apply { mkdirs() }.let { File(it, "legacy.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) } }
        val llm = StepLlmClient({ text("Ok.") })
        val v = fixture(llm)
        val cap = v.voice.saveQuick(old)
        v.drainWorker()
        val done = f.captures.get(cap.id)!!
        assertEquals(CaptureStatus.DONE, done.status)
        assertEquals(old, v.stt.calls.single().first)
        assertFalse("the old file is deleted like any other", old.exists())
        assertNull(done.audioPath)
    }

    @Test fun anAudioFileThatIsGoneFailsTheCaptureCleanlyWithoutCrash() = runBlocking {
        val v = VoiceFixture(f, StepLlmClient(), FakeTranscriber(SttResult.Failure(com.lifehub.app.copilot.stt.SttError.AudioMissing)), tmp.newFolder())
        val cap = v.voice.saveQuick(File(tmp.newFolder(), "missing.m4a"))
        v.runner.run(cap.id, notify = false)
        assertEquals(CaptureStatus.FAILED, f.captures.get(cap.id)!!.status)
        assertEquals("Stt.AudioMissing", f.captures.get(cap.id)!!.errorCode)
    }

    @Test fun undoDeletesTheAudioOfACaptureStillWaitingForAnswers() = runBlocking {
        val v = fixture(asksOne())
        val audio = v.newAudio()
        val cap = v.voice.saveQuick(audio)
        v.drainWorker()
        assertEquals(CaptureStatus.AWAITING_INPUT, f.captures.get(cap.id)!!.status)
        assertTrue("kept while a question is open", audio.exists())

        v.processor.undo(cap.id)
        assertEquals(CaptureStatus.CANCELLED, f.captures.get(cap.id)!!.status)
        assertFalse(audio.exists())
        assertNull(f.captures.get(cap.id)!!.audioPath)
        assertEquals("the transcript stays", "100 g di polpette", f.captures.get(cap.id)!!.transcript)
    }

    @Test fun discardingTheLastOpenRequestDeletesTheAudioButNotBeforeThat() = runBlocking {
        val v = fixture(asksTwo())
        val audio = v.newAudio()
        val cap = v.voice.saveQuick(audio)
        v.drainWorker()
        val open = f.pending.byCapture(cap.id)
        assertEquals(2, open.size)

        v.processor.discard(open[0].id)
        assertEquals(CaptureStatus.AWAITING_INPUT, f.captures.get(cap.id)!!.status)
        assertTrue("one request is still open: the recording stays", audio.exists())
        assertNotNull(f.captures.get(cap.id)!!.audioPath)

        v.processor.discard(open[1].id)
        assertEquals(CaptureStatus.DONE, f.captures.get(cap.id)!!.status)
        assertFalse(audio.exists())
        assertNull(f.captures.get(cap.id)!!.audioPath)
    }
}
