package com.lifehub.app.copilot.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

private class FakeTransport(private vararg val steps: Any) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun post(request: HttpRequest): HttpResponse {
        requests += request
        val step = steps[minOf(requests.size - 1, steps.size - 1)]
        if (step is Throwable) throw step
        return step as HttpResponse
    }
}

class LlmClientTest {
    private val KEY = "sk-test-SECRET-1234567890"
    private fun cfg(base: String = "https://openrouter.ai/api/v1/", key: String? = KEY, model: String = "vendor/model") = { LlmConfig(base, model, key) }
    private fun client(t: HttpTransport, c: () -> LlmConfig = cfg()) = OpenAiCompatibleLlmClient(c, t, maxRetries = 1, retryDelayMs = 0)
    private fun ok(body: String) = HttpResponse(200, body)
    private fun fail(r: LlmResult) = (r as LlmResult.Failure).error
    private val pingTool = LlmTool("ping", "desc", buildJsonObject { put("type", "object") })
    private fun chat(c: LlmClient, tools: List<LlmTool> = emptyList(), choice: LlmToolChoice = LlmToolChoice.Auto) =
        runBlocking { c.chat(listOf(LlmMessage.System("sys"), LlmMessage.User("hi")), tools, choice, maxTokens = 16) }

    private val textReply = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Ciao"}}]}"""
    private val toolReply = """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
        "tool_calls":[{"id":"call_1","type":"function","function":{"name":"ping","arguments":"{\"a\":1}"}}]}}]}"""

    @Test fun buildsRequest() {
        val t = FakeTransport(ok(textReply))
        chat(client(t), listOf(pingTool), LlmToolChoice.Function("ping"))
        val r = t.requests.single()
        assertEquals("https://openrouter.ai/api/v1/chat/completions", r.url)
        assertEquals("Bearer $KEY", r.headers["Authorization"])
        val b = Json.parseToJsonElement(r.body).jsonObject
        assertEquals("vendor/model", b["model"]!!.jsonPrimitive.content)
        assertEquals(16, b["max_tokens"]!!.jsonPrimitive.content.toInt())
        val msgs = b["messages"]!!.jsonArray
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("hi", msgs[1].jsonObject["content"]!!.jsonPrimitive.content)
        val tool = b["tools"]!!.jsonArray[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals("ping", tool["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val tc = b["tool_choice"]!!.jsonObject
        assertEquals("ping", tc["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("true", b["provider"]!!.jsonObject["require_parameters"]!!.jsonPrimitive.content)
    }

    @Test fun noProviderBlockForOtherGatewaysAndNoToolsKeyWithoutTools() {
        val t = FakeTransport(ok(textReply))
        chat(client(t, cfg(base = "https://api.example.com/v1")), listOf(pingTool))
        val b = Json.parseToJsonElement(t.requests.single().body).jsonObject
        assertNull(b["provider"]); assertEquals("auto", b["tool_choice"]!!.jsonPrimitive.content)
        val t2 = FakeTransport(ok(textReply)); chat(client(t2))
        val b2 = Json.parseToJsonElement(t2.requests.single().body).jsonObject
        assertNull(b2["tools"]); assertNull(b2["tool_choice"])
    }

    @Test fun serialisesToolRoundTripMessages() {
        val body = OpenAiCodec.buildRequest(
            "m", listOf(
                LlmMessage.Assistant(null, listOf(LlmToolCall("c1", "get", "{\"x\":1}"))),
                LlmMessage.Tool("c1", "result"),
            ), emptyList(), LlmToolChoice.Auto, null, false,
        )
        val m = Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray
        val call = m[0].jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("c1", call["id"]!!.jsonPrimitive.content)
        assertEquals("{\"x\":1}", call["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
        assertEquals("c1", m[1].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test fun parsesTextAndToolCalls() {
        val a = (chat(client(FakeTransport(ok(textReply)))) as LlmResult.Ok).reply
        assertEquals("Ciao", a.message.content); assertTrue(a.message.toolCalls.isEmpty()); assertEquals("stop", a.finishReason)
        val b = (chat(client(FakeTransport(ok(toolReply))), listOf(pingTool)) as LlmResult.Ok).reply
        assertNull(b.message.content)
        assertEquals(LlmToolCall("call_1", "ping", "{\"a\":1}"), b.message.toolCalls.single())
    }

    @Test fun missingKeyDoesNotTouchTheNetwork() {
        val t = FakeTransport(ok(textReply))
        assertEquals(LlmError.MissingKey, fail(chat(client(t, cfg(key = null)))))
        assertEquals(LlmError.MissingKey, fail(chat(client(t, cfg(key = "  ")))))
        assertTrue(t.requests.isEmpty())
    }

    @Test fun invalidConfig() {
        assertTrue(fail(chat(client(FakeTransport(ok(textReply)), cfg(base = "ftp://x")))) is LlmError.InvalidConfig)
        assertTrue(fail(chat(client(FakeTransport(ok(textReply)), cfg(model = " ")))) is LlmError.InvalidConfig)
    }

    @Test fun mapsHttpErrors() {
        fun err(code: Int, msg: String = "x") = fail(chat(client(FakeTransport(HttpResponse(code, """{"error":{"code":$code,"message":"$msg"}}""")))))
        assertEquals(LlmError.Auth(401), err(401))
        assertEquals(LlmError.Auth(403), err(403))
        assertEquals(LlmError.PaymentRequired, err(402))
        assertEquals(LlmError.RateLimited, err(429))
        assertEquals(LlmError.ModelNotFound, err(404, "No such model"))
        assertEquals(LlmError.ToolsUnsupported, err(404, "No endpoints found that support tool use"))
        assertEquals(LlmError.ToolsUnsupported, err(400, "tools are not supported by this model"))
        assertEquals(LlmError.Http(400, "bad"), err(400, "bad"))
        assertEquals(LlmError.Http(500, null), fail(chat(client(FakeTransport(HttpResponse(500, "<html>"))))))
    }

    @Test fun errorInsideA200Body() {
        val e = fail(chat(client(FakeTransport(ok("""{"error":{"code":402,"message":"credits"}}""")))))
        assertEquals(LlmError.PaymentRequired, e)
    }

    @Test fun malformedResponses() {
        listOf("not json", "[]", "{}", """{"choices":[]}""", """{"choices":[{"message":{"tool_calls":[{"function":{"name":"x"}}]}}]}""",
            """{"choices":[{"message":{"tool_calls":"nope"}}]}""").forEach {
            assertTrue(it, fail(chat(client(FakeTransport(ok(it))))) is LlmError.Malformed)
        }
    }

    @Test fun retriesTemporaryServerFailureOnceThenSucceeds() {
        val t = FakeTransport(HttpResponse(503, "{}"), ok(textReply))
        assertTrue(chat(client(t)) is LlmResult.Ok); assertEquals(2, t.requests.size)
    }

    @Test fun retryIsBounded() {
        val t = FakeTransport(HttpResponse(503, """{"error":{"code":503,"message":"none"}}"""))
        assertTrue(fail(chat(client(t))) is LlmError.Http); assertEquals(2, t.requests.size)
    }

    @Test fun doesNotRetryAuthRateLimitOrReadTimeout() {
        val a = FakeTransport(HttpResponse(401, "{}")); chat(client(a)); assertEquals(1, a.requests.size)
        val b = FakeTransport(HttpResponse(429, "{}")); chat(client(b)); assertEquals(1, b.requests.size)
        val c = FakeTransport(SocketTimeoutException("read")); assertEquals(LlmError.Network(true), fail(chat(client(c)))); assertEquals(1, c.requests.size)
    }

    @Test fun retriesConnectFailureOnce() {
        val t = FakeTransport(ConnectFailedException(IOException("no route")), ok(textReply))
        assertTrue(chat(client(t)) is LlmResult.Ok); assertEquals(2, t.requests.size)
        val t2 = FakeTransport(ConnectFailedException(IOException("no route")))
        assertEquals(LlmError.Network(false), fail(chat(client(t2)))); assertEquals(2, t2.requests.size)
        assertEquals(LlmError.Network(false), fail(chat(client(FakeTransport(IOException("reset"))))))
    }

    @Test fun toStringsNeverLeakKeyOrContent() {
        val t = FakeTransport(ok(textReply)); chat(client(t))
        val r = t.requests.single()
        assertFalse(r.toString().contains(KEY)); assertFalse(r.toString().contains("hi"))
        assertFalse(LlmConfig("u", "m", KEY).toString().contains(KEY))
        listOf(LlmError.MissingKey, LlmError.Auth(401), LlmError.ToolsUnsupported).forEach { assertFalse(it.message.contains(KEY)) }
    }

    @Test fun toolsUnsupportedMessageIsExplicit() {
        assertTrue(LlmError.ToolsUnsupported.message.contains("does not support tool calling"))
    }

    // ---- connection test ----
    private fun tester(t: HttpTransport, key: String? = KEY) = ConnectionTester(client(t, cfg(key = key))) { "vendor/model" }

    @Test fun connectionTestSucceedsWhenModelCallsPing() {
        val t = FakeTransport(ok(toolReply))
        assertEquals(ConnectionTestResult.Success("vendor/model"), runBlocking { tester(t).run() })
        val b = Json.parseToJsonElement(t.requests.single().body).jsonObject
        assertEquals("ping", b["tool_choice"]!!.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(32, b["max_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun connectionTestFlagsModelThatAnswersInTextOnly() {
        val r = runBlocking { tester(FakeTransport(ok(textReply))).run() }
        assertEquals(ConnectionTestResult.Failure(LlmError.ToolsUnsupported), r)
    }

    @Test fun connectionTestSurfacesAuthAndMissingKey() {
        assertEquals(ConnectionTestResult.Failure(LlmError.Auth(401)), runBlocking { tester(FakeTransport(HttpResponse(401, "{}"))).run() })
        assertEquals(ConnectionTestResult.Failure(LlmError.MissingKey), runBlocking { tester(FakeTransport(ok(textReply)), key = null).run() })
    }
}
