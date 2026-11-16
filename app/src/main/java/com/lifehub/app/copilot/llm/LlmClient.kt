package com.lifehub.app.copilot.llm

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * OpenAI-compatible chat client over [HttpTransport]. Nothing about requests, responses or keys is logged.
 *
 * Retry policy (bounded, [maxRetries] = 1): only failures where the request provably did not run or the server said it
 * is temporarily unavailable (connect failure, 502/503/504/408). Read timeouts and 429 are NOT retried automatically
 * (the call may already have been processed and billed); callers decide.
 */
class OpenAiCompatibleLlmClient(
    private val config: () -> LlmConfig,
    private val transport: HttpTransport,
    private val maxRetries: Int = 1,
    private val retryDelayMs: Long = 800,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000,
) : LlmClient {

    override suspend fun chat(
        messages: List<LlmMessage>, tools: List<LlmTool>, toolChoice: LlmToolChoice, maxTokens: Int?,
    ): LlmResult {
        val cfg = config()
        val key = cfg.apiKey?.trim().orEmpty()
        if (key.isEmpty()) return LlmResult.Failure(LlmError.MissingKey)
        val base = cfg.baseUrl.trim()
        if (!(base.startsWith("https://") || base.startsWith("http://")) || runCatching { java.net.URI(base).host }.getOrNull().isNullOrEmpty())
            return LlmResult.Failure(LlmError.InvalidConfig("base URL must start with https://"))
        if (cfg.model.isBlank()) return LlmResult.Failure(LlmError.InvalidConfig("model is empty"))

        val request = HttpRequest(
            url = OpenAiCodec.chatUrl(base),
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            ),
            body = OpenAiCodec.buildRequest(cfg.model.trim(), messages, tools, toolChoice, maxTokens, OpenAiCodec.isOpenRouter(base)),
            connectTimeoutMs = connectTimeoutMs, readTimeoutMs = readTimeoutMs,
        )
        var attempt = 0
        while (true) {
            val outcome: LlmResult? = try {
                val r = transport.post(request)
                if (r.code in RETRYABLE && attempt < maxRetries) null else OpenAiCodec.parse(r.code, r.body)
            } catch (e: SocketTimeoutException) {
                return LlmResult.Failure(LlmError.Network(timeout = true))
            } catch (e: ConnectFailedException) {
                if (attempt < maxRetries) null else return LlmResult.Failure(LlmError.Network(timeout = e.cause is SocketTimeoutException))
            } catch (e: IOException) {
                return LlmResult.Failure(LlmError.Network(timeout = false))
            }
            if (outcome != null) return outcome
            attempt++
            delay(retryDelayMs)
        }
    }

    private companion object { val RETRYABLE = setOf(408, 502, 503, 504) }
}
