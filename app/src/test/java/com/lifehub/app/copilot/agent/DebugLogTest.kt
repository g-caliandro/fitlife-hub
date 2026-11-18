package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.debug.CopilotDebugLog
import com.lifehub.app.copilot.llm.LlmReply
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.copilot.llm.LlmUsage
import com.lifehub.app.data.model.ChatOrigin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class DebugLogTest {
    private val f = CopilotFixture()
    private val dir = Files.createTempDirectory("dbg").toFile()
    @After fun tearDown() { f.close(); dir.deleteRecursively() }

    private fun lines(log: File) = log.readLines().map { Json.parseToJsonElement(it).jsonObject }

    @Test fun aTurnLeavesTurnLlmToolAndEndEntriesWithUsage() = runBlocking {
        val file = File(dir, "log.jsonl")
        val llm = FakeLlmClient(LlmResult.Ok((tools(call("c1", "get_daily_summary", """{"date":"2026-11-16"}""")) as LlmResult.Ok).reply.copy(usage = LlmUsage(3000, 50, 0, 2900, 0.002))))
        llm.fallback = { LlmResult.Ok(LlmReply(LlmMessage.Assistant("Fatto."), "stop", LlmUsage(3100, 20, 2900, 0, 0.0004), "gen-9", "anthropic/x")) }
        val id = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        AgentLoop(llm, f.registry, f.chat, f.time, debug = CopilotDebugLog(file)).send(id, "Quante proteine mi mancano?")

        val e = lines(file)
        assertEquals(listOf("turn", "llm", "tool", "llm", "turn_end"), e.map { it["t"]!!.jsonPrimitive.content })
        assertTrue(e[0]["system"]!!.jsonPrimitive.content.contains("Fitness Copilot"))
        assertEquals("get_daily_summary", e[2]["name"]!!.jsonPrimitive.content)
        assertEquals("gen-9", e[3]["generation_id"]!!.jsonPrimitive.content)
        val end = e[4]
        assertEquals("answered", end["result"]!!.jsonPrimitive.content)
        assertEquals(2, end["llm_calls"]!!.jsonPrimitive.content.toInt())
        assertEquals(6100, end["prompt_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(2900, end["cached_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(0.0024, end["cost_usd"]!!.jsonPrimitive.content.toDouble(), 1e-9)
    }

    @Test fun rotatesExportsAndClears() {
        val file = File(dir, "log.jsonl")
        val log = CopilotDebugLog(file, maxBytes = 200)
        repeat(10) { i -> log.record("x") { put("i", i); put("pad", "y".repeat(50)) } }
        assertTrue(File(dir, "log.jsonl.1").isFile)
        val out = File(dir, "export.jsonl")
        assertTrue(log.exportTo(out))
        val idx = out.readLines().map { Json.parseToJsonElement(it).jsonObject["i"]!!.jsonPrimitive.content.toInt() }
        assertEquals(idx.sorted(), idx)   // oldest first
        assertEquals(9, idx.last())
        log.clear()
        assertEquals(0L, log.sizeBytes())
        assertFalse(log.exportTo(File(dir, "empty.jsonl")))
    }

    @Test fun neverThrowsOnAnUnwritablePath() {
        CopilotDebugLog(File("/proc/nope/dir/log.jsonl")).record("x") { }
    }
}
