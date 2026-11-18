package com.lifehub.app.copilot.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Pure request building / response parsing for the OpenAI-compatible chat completions API (unit-tested with fixtures). */
object OpenAiCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun isOpenRouter(baseUrl: String): Boolean =
        runCatching { java.net.URI(baseUrl.trim()).host?.lowercase() }.getOrNull()
            ?.let { it == "openrouter.ai" || it.endsWith(".openrouter.ai") } == true

    /**
     * Anthropic models on OpenRouter get prompt caching: an automatic breakpoint that advances with the conversation (the
     * tool rounds of one turn re-read the previous round) plus one after the static part of the system prompt (tools +
     * base prompt, shared by every turn). Other models/gateways get the plain request (OpenAI and Gemini cache implicitly).
     */
    fun supportsCache(model: String, openRouter: Boolean): Boolean = openRouter && model.trim().startsWith("anthropic/")

    fun chatUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/') + "/chat/completions"

    fun buildRequest(
        model: String, messages: List<LlmMessage>, tools: List<LlmTool>, toolChoice: LlmToolChoice,
        maxTokens: Int?, openRouter: Boolean,
    ): String = buildJsonObject {
        val cache = supportsCache(model, openRouter)
        put("model", model)
        put("messages", buildJsonArray { messages.forEach { add(message(it, cache)) } })
        if (cache) putJsonObject("cache_control") { put("type", "ephemeral") }
        // OpenRouter: report cost in the response's usage.
        if (openRouter) putJsonObject("usage") { put("include", true) }
        if (tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                tools.forEach { t ->
                    add(buildJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", t.name); put("description", t.description); put("parameters", t.parameters)
                        }
                    })
                }
            })
            when (toolChoice) {
                LlmToolChoice.Auto -> put("tool_choice", "auto")
                LlmToolChoice.None -> put("tool_choice", "none")
                is LlmToolChoice.Function -> putJsonObject("tool_choice") {
                    put("type", "function"); putJsonObject("function") { put("name", toolChoice.name) }
                }
            }
            // OpenRouter only: route only to providers that honour `tools` (otherwise unsupported params are silently ignored).
            if (openRouter) putJsonObject("provider") { put("require_parameters", true) }
        }
        if (maxTokens != null) put("max_tokens", maxTokens)
    }.toString()

    private fun message(m: LlmMessage, cache: Boolean = false): JsonObject = buildJsonObject {
        when (m) {
            is LlmMessage.System -> {
                put("role", "system")
                if (cache && m.staticLength in 1 until m.content.length) put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text"); put("text", m.content.substring(0, m.staticLength))
                        putJsonObject("cache_control") { put("type", "ephemeral") }
                    })
                    add(buildJsonObject { put("type", "text"); put("text", m.content.substring(m.staticLength)) })
                }) else put("content", m.content)
            }
            is LlmMessage.User -> { put("role", "user"); put("content", m.content) }
            is LlmMessage.Tool -> { put("role", "tool"); put("tool_call_id", m.toolCallId); put("content", m.content) }
            is LlmMessage.Assistant -> {
                put("role", "assistant")
                if (m.content != null) put("content", m.content) else put("content", JsonNull)
                if (m.toolCalls.isNotEmpty()) put("tool_calls", buildJsonArray {
                    m.toolCalls.forEach { tc ->
                        add(buildJsonObject {
                            put("id", tc.id); put("type", "function")
                            putJsonObject("function") { put("name", tc.name); put("arguments", tc.arguments) }
                        })
                    }
                })
            }
        }
    }

    fun parse(httpCode: Int, body: String): LlmResult {
        val root = try { json.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null }
        val err = root?.get("error") as? JsonObject
        // OpenRouter can report generation errors inside a 200 response.
        if (err != null) return LlmResult.Failure(mapError(((err["code"] as? JsonPrimitive)?.intOrNull ?: httpCode).let { if (it in 200..299) 502 else it }, err))
        if (httpCode !in 200..299) return LlmResult.Failure(mapError(httpCode, null))
        if (root == null) return malformed("not a JSON object")
        val choice = ((root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject) ?: return malformed("no choices")
        val msg = choice["message"] as? JsonObject ?: return malformed("no message")
        val content = (msg["content"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        val calls = mutableListOf<LlmToolCall>()
        val rawCalls = msg["tool_calls"]
        if (rawCalls != null && rawCalls !is JsonNull) {
            val arr = rawCalls as? JsonArray ?: return malformed("tool_calls is not a list")
            for (el in arr) {
                val o = el as? JsonObject ?: return malformed("bad tool call")
                val fn = o["function"] as? JsonObject ?: return malformed("tool call without function")
                val id = (o["id"] as? JsonPrimitive)?.contentOrNull
                val name = (fn["name"] as? JsonPrimitive)?.contentOrNull
                if (id.isNullOrEmpty() || name.isNullOrEmpty()) return malformed("tool call without id or name")
                val args = when (val a = fn["arguments"]) {
                    null, is JsonNull -> "{}"
                    is JsonPrimitive -> a.contentOrNull ?: "{}"
                    else -> a.toString() // some gateways return an object instead of a string
                }
                calls += LlmToolCall(id, name, args.ifBlank { "{}" })
            }
        }
        val finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
        return LlmResult.Ok(LlmReply(LlmMessage.Assistant(content, calls), finish, usage(root["usage"] as? JsonObject), (root["id"] as? JsonPrimitive)?.contentOrNull, (root["model"] as? JsonPrimitive)?.contentOrNull))
    }

    private fun usage(u: JsonObject?): LlmUsage? {
        u ?: return null
        fun int(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.intOrNull
        val details = u["prompt_tokens_details"] as? JsonObject
        return LlmUsage(
            int(u, "prompt_tokens") ?: 0, int(u, "completion_tokens") ?: 0,
            int(details, "cached_tokens") ?: 0, int(details, "cache_write_tokens") ?: 0,
            (u["cost"] as? JsonPrimitive)?.doubleOrNull,
        )
    }

    private fun malformed(reason: String) = LlmResult.Failure(LlmError.Malformed(reason))

    private fun mapError(code: Int, err: JsonObject?): LlmError {
        val detail = (err?.get("message") as? JsonPrimitive)?.contentOrNull?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
        val mentionsTools = detail?.contains("tool", ignoreCase = true) == true || detail?.contains("function", ignoreCase = true) == true
        return when (code) {
            401, 403 -> LlmError.Auth(code)
            402 -> LlmError.PaymentRequired
            429 -> LlmError.RateLimited
            404 -> if (mentionsTools) LlmError.ToolsUnsupported else LlmError.ModelNotFound
            400, 422, 503 -> if (mentionsTools) LlmError.ToolsUnsupported else LlmError.Http(code, detail)
            else -> LlmError.Http(code, detail)
        }
    }
}
