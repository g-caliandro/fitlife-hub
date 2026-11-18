package com.lifehub.app.copilot.llm

import kotlinx.serialization.json.JsonObject

/** Chat messages in the OpenAI-compatible shape. */
sealed interface LlmMessage {
    /** The first [staticLength] chars of [content] never change between turns (cache breakpoint after them); 0 = unknown. */
    data class System(val content: String, val staticLength: Int = 0) : LlmMessage
    data class User(val content: String) : LlmMessage
    /** [content] may be null when the model only requests tools. */
    data class Assistant(val content: String?, val toolCalls: List<LlmToolCall> = emptyList()) : LlmMessage
    data class Tool(val toolCallId: String, val content: String) : LlmMessage
}

/** [arguments] is the raw JSON string exactly as the model produced it (validated by the tool layer, not here). */
data class LlmToolCall(val id: String, val name: String, val arguments: String)

/** [parameters] is a JSON Schema object. */
data class LlmTool(val name: String, val description: String, val parameters: JsonObject)

sealed interface LlmToolChoice {
    data object Auto : LlmToolChoice
    data object None : LlmToolChoice
    data class Function(val name: String) : LlmToolChoice
}

/** Token usage of one call as reported by the provider ([cachedTokens] = read from the prompt cache, [costUsd] when the gateway reports it). */
data class LlmUsage(
    val promptTokens: Int, val completionTokens: Int, val cachedTokens: Int = 0, val cacheWriteTokens: Int = 0, val costUsd: Double? = null,
)

/** [generationId] (OpenRouter: look it up in Activity) and [model] (what actually answered) are for the debug log only. */
data class LlmReply(
    val message: LlmMessage.Assistant, val finishReason: String?,
    val usage: LlmUsage? = null, val generationId: String? = null, val model: String? = null,
)

/** Typed, user-readable failures. [message] is safe to show in the UI. */
sealed class LlmError(val message: String) {
    data object MissingKey : LlmError("No LLM API key set. Add it in Copilot settings.")
    data class InvalidConfig(val reason: String) : LlmError("Invalid LLM settings: $reason")
    data class Network(val timeout: Boolean) : LlmError(
        if (timeout) "The LLM server took too long to answer. Try again." else "Couldn't reach the LLM server. Check your connection and the base URL."
    )
    data class Auth(val httpCode: Int) : LlmError("The API key was rejected ($httpCode). Check the key.")
    data object PaymentRequired : LlmError("The provider reports insufficient credits.")
    data object RateLimited : LlmError("Rate limit reached. Try again in a moment.")
    data object ModelNotFound : LlmError("Model not found or unavailable. Check the model name.")
    data object ToolsUnsupported : LlmError("This model does not support tool calling, which the copilot needs. Choose a model that does.")
    data class Http(val httpCode: Int, val detail: String?) : LlmError("The LLM server returned an error ($httpCode)" + (detail?.let { ": $it" } ?: "."))
    data class Malformed(val reason: String) : LlmError("Unexpected response from the LLM server ($reason).")
}

sealed interface LlmResult {
    data class Ok(val reply: LlmReply) : LlmResult
    data class Failure(val error: LlmError) : LlmResult
}

/** What the client needs at call time; read fresh from settings + secret store on every call. */
class LlmConfig(val baseUrl: String, val model: String, val apiKey: String?) {
    override fun toString() = "LlmConfig(baseUrl=$baseUrl, model=$model, apiKey=<redacted>)"
}

interface LlmClient {
    suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<LlmTool> = emptyList(),
        toolChoice: LlmToolChoice = LlmToolChoice.Auto,
        maxTokens: Int? = null,
    ): LlmResult
}
