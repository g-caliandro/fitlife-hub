package com.lifehub.app.copilot.capture

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.agent.AgentLoop
import com.lifehub.app.copilot.agent.CopilotTool
import com.lifehub.app.copilot.agent.ToolArgs
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolKind
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.agent.ToolRegistry
import com.lifehub.app.copilot.agent.ToolSchema
import com.lifehub.app.copilot.llm.LlmClient
import com.lifehub.app.copilot.notify.CopilotActions
import com.lifehub.app.copilot.notify.NotificationCenter
import com.lifehub.app.copilot.notify.NotificationSink
import com.lifehub.app.copilot.notify.NotificationSpec
import com.lifehub.app.copilot.stt.SttResult
import com.lifehub.app.copilot.stt.Transcriber
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/** Scripted transcriber: returns [results] in order (the last one repeats) and records every file it got. */
class FakeTranscriber(vararg results: SttResult) : Transcriber {
    private val queue = ArrayDeque(results.toList())
    val calls = mutableListOf<Pair<File, String?>>()
    override suspend fun transcribe(audio: File, language: String?): SttResult {
        calls += audio to language
        return if (queue.size > 1) queue.removeFirst() else queue.first()
    }
}

/** Records what would be shown by Android; [visible] = notifications currently on screen by tag. */
class RecordingSink : NotificationSink {
    val posted = mutableListOf<NotificationSpec>()
    val cancelled = mutableListOf<String>()
    val visible = linkedMapOf<String, NotificationSpec>()
    override fun post(spec: NotificationSpec): Boolean { posted += spec; visible[spec.tag] = spec; return true }
    override fun cancel(tag: String) { cancelled += tag; visible.remove(tag) }
}

/** Stand-in for phase 6's `save_user_memory`: a chat-only tool that a quick capture must never be offered. */
object ChatOnlyMemoryTool : CopilotTool {
    override val name = "save_user_memory"
    override val description = "test"
    override val kind = ToolKind.WRITE
    override val schema = ToolSchema(emptyList())
    override val chatOnly = true
    override suspend fun execute(args: ToolArgs, ctx: ToolContext) = ToolOutcome.Ok(buildJsonObject { })
}

/** Phase 4 wiring over [CopilotFixture] (as in AppContainer), with a fake transcriber, fake sink and a recording scheduler. */
class VoiceFixture(val f: CopilotFixture, llm: LlmClient, val stt: FakeTranscriber, audioDir: File) {
    val sink = RecordingSink()
    var notificationsEnabled = true
    val enqueued = mutableListOf<String>()
    val scheduler = CaptureScheduler { enqueued += it }
    val registry = ToolRegistry(f.fullRegistry.tools + ChatOnlyMemoryTool)
    val center = NotificationCenter(f.captures, f.pending, { f.db.foodEntryDao().byCapture(it) }, sink, { notificationsEnabled }, f.time)
    val processor = CaptureProcessor(f.captures, f.pending, f.writer, AgentLoop(llm, registry, f.chat, f.time), f.chat, f.time, center)
    val runner = CaptureRunner(f.captures, f.chat, processor, stt, { "it" }, f.time, center)
    val voice = VoiceCaptures(f.chat, f.captures, scheduler, f.time)
    val actions = CopilotActions(processor, runner, scheduler, center)
    private val dir = audioDir.also { it.mkdirs() }

    fun newAudio(): File = File(dir, "a${System.nanoTime()}.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    /** What the CaptureWorker does for every queued id (in order), until the queue is empty. */
    suspend fun drainWorker(): List<RunReport> {
        val out = mutableListOf<RunReport>()
        while (enqueued.isNotEmpty()) out += runner.run(enqueued.removeAt(0), notify = true)
        return out
    }
}
