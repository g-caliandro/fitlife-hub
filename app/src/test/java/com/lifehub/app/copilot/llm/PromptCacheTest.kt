package com.lifehub.app.copilot.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCacheTest {
    private val msgs = listOf(LlmMessage.System("STATIC|dynamic clock", staticLength = 6), LlmMessage.User("ciao"))
    private fun req(model: String, openRouter: Boolean) =
        Json.parseToJsonElement(OpenAiCodec.buildRequest(model, msgs, emptyList(), LlmToolChoice.Auto, 100, openRouter)).jsonObject

    @Test fun anthropicOnOpenRouterGetsAutomaticAndSystemBreakpoints() {
        val r = req("anthropic/claude-haiku-5.5", true)
        assertEquals("ephemeral", r["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val parts = r["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("STATIC", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertNotNull(parts[0].jsonObject["cache_control"])
        assertEquals("|dynamic clock", parts[1].jsonObject["text"]!!.jsonPrimitive.content)
        assertNull(parts[1].jsonObject["cache_control"])
        assertEquals(true, r["usage"]!!.jsonObject["include"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun otherModelsAndGatewaysGetThePlainRequest() {
        for (r in listOf(req("openai/gpt-5-mini", true), req("anthropic/claude-haiku-5.5", false))) {
            assertNull(r["cache_control"])
            assertEquals("STATIC|dynamic clock", r["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
        }
        assertNull(req("openai/gpt-5-mini", true)["messages"]!!.jsonArray.let { (it[0] as JsonObject)["content"] as? JsonArray })
    }

    @Test fun parsesUsageCacheAndCost() {
        val body = """{"id":"gen-1","model":"anthropic/claude-haiku-5.5","choices":[{"finish_reason":"stop","message":{"content":"ok"}}],
            "usage":{"prompt_tokens":5000,"completion_tokens":40,"cost":0.0012,"prompt_tokens_details":{"cached_tokens":4800,"cache_write_tokens":0}}}"""
        val reply = (OpenAiCodec.parse(200, body) as LlmResult.Ok).reply
        assertEquals(LlmUsage(5000, 40, 4800, 0, 0.0012), reply.usage)
        assertEquals("gen-1", reply.generationId)
        assertEquals("anthropic/claude-haiku-5.5", reply.model)
        assertTrue((OpenAiCodec.parse(200, """{"choices":[{"message":{"content":"ok"}}]}""") as LlmResult.Ok).reply.usage == null)
    }
}
