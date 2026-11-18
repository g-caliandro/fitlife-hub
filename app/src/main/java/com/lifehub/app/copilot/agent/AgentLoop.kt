package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.debug.CopilotDebugLog
import com.lifehub.app.copilot.llm.LlmClient
import com.lifehub.app.copilot.llm.LlmError
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.copilot.llm.LlmToolChoice
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.repo.ChatStore
import com.lifehub.app.data.repo.NewChatMessage
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Tunables of [AgentLoop] (configurable in code, design doc §10). */
data class AgentConfig(
    /** Max LLM rounds that may execute tools in one turn; a further tool request ends the turn with [TurnError.IterationLimit]. */
    val maxToolIterations: Int = 5,
    /** Max thread messages sent to the model (the current turn is never cut). */
    val historyMaxMessages: Int = 20,
    /** Previous turns whose tool calls/results are still sent; older ones keep only user/assistant text. */
    val historyToolTurns: Int = 1,
    /** Whole-turn timeout (all LLM calls + tools). The HTTP client has its own per-request timeouts. */
    val turnTimeoutMs: Long = 120_000,
    val maxTokens: Int = 700,
)

/**
 * Per-turn options. Extension points for later phases:
 * - [toolFilter]: quick capture (phase 4) hides `save_user_memory`.
 * - [captureId]: set when the turn runs for a capture (phase 3+); passed to tools for idempotent writes and stored
 *   on the persisted messages.
 * - [extraContext]: one-off system-prompt sections (e.g. the product attached to the message, phase 5).
 */
data class TurnOptions(
    val toolFilter: (CopilotTool) -> Boolean = { true },
    val captureId: String? = null,
    val extraContext: List<String> = emptyList(),
)

sealed class TurnError(val message: String) {
    /** [error] carries the typed cause (missing key, network, auth, ...); [message] is safe to show. */
    class Llm(val error: LlmError) : TurnError(error.message)
    data object Timeout : TurnError("The copilot took too long to answer. Try again.")
    data object IterationLimit : TurnError("The copilot needed too many steps for this request. Try asking in a simpler way.")
}

sealed interface TurnResult {
    data class Answered(val text: String) : TurnResult
    data class Failed(val error: TurnError) : TurnResult
    /** An INPUT_REQUEST tool saved [pendingInputIds]; the turn stops until the user answers (a new turn resumes it). */
    data class AwaitingInput(val pendingInputIds: List<String>) : TurnResult
}

/**
 * Orchestrates one copilot turn (design doc §4): system prompt + clock + context sections, a limited window of the
 * thread ([HistoryWindow]) and the tool definitions go to the model; tool calls are validated and executed by the app
 * ([ToolRegistry]) and their results go back, until the model answers with text, the iteration limit is hit, the LLM
 * fails, or the turn times out. Every step is persisted as it happens (user message, assistant tool calls, tool
 * results with their card, final answer), so [run] can continue an interrupted/failed turn from the stored thread.
 * Failures are returned, never thrown.
 */
class AgentLoop(
    private val llm: LlmClient,
    private val registry: ToolRegistry,
    private val chat: ChatStore,
    private val time: TimeSource,
    private val config: AgentConfig = AgentConfig(),
    private val contextProviders: List<ContextProvider> = emptyList(),
    /** Debug trace of turns, model calls (usage, cost) and tool calls; null = none. */
    private val debug: CopilotDebugLog? = null,
) {
    /** Stores [text] as a USER message, then runs the turn. */
    suspend fun send(threadId: String, text: String, options: TurnOptions = TurnOptions()): TurnResult {
        chat.append(threadId, NewChatMessage(ChatRole.USER, text.trim(), captureId = options.captureId), time.now())
        return run(threadId, options)
    }

    /** Runs (or retries) the turn for the stored thread, whose last turn starts at a USER message. */
    suspend fun run(threadId: String, options: TurnOptions = TurnOptions()): TurnResult =
        withTimeoutOrNull(config.turnTimeoutMs) { loop(threadId, options) }
            ?: TurnResult.Failed(TurnError.Timeout).also { debug?.record("turn_end") { put("thread", threadId); put("capture", options.captureId); put("result", "timeout") } }

    private suspend fun loop(threadId: String, options: TurnOptions): TurnResult {
        val now = time.now()
        val ctx = ToolContext(now, time.zone, threadId, options.captureId)
        val sections = contextProviders.mapNotNull { it.section(ctx) } + options.extraContext
        // The base prompt comes first and never changes: it is the cacheable part (the clock and sections follow it).
        val system = LlmMessage.System(AgentContext.systemPrompt(now, time.zone, sections), staticLength = AgentContext.BASE_PROMPT.length)
        val tools = registry.definitions(options.toolFilter)
        val history = HistoryWindow.build(chat.messages(threadId), config.historyMaxMessages, config.historyToolTurns)
        val turn = mutableListOf<LlmMessage>()
        var iterations = 0

        val started = System.nanoTime()
        var llmCalls = 0; var promptTokens = 0; var completionTokens = 0; var cachedTokens = 0; var costUsd: Double? = null
        fun finish(r: TurnResult): TurnResult {
            debug?.record("turn_end") {
                put("thread", threadId); put("capture", options.captureId)
                put("result", when (r) { is TurnResult.Answered -> "answered"; is TurnResult.AwaitingInput -> "awaiting_input"; is TurnResult.Failed -> "failed" })
                (r as? TurnResult.Failed)?.let { put("error", it.error.message) }
                put("llm_calls", llmCalls); put("prompt_tokens", promptTokens); put("completion_tokens", completionTokens); put("cached_tokens", cachedTokens)
                costUsd?.let { put("cost_usd", it) }
                put("ms", (System.nanoTime() - started) / 1_000_000)
            }
            return r
        }
        debug?.record("turn") {
            put("thread", threadId); put("capture", options.captureId)
            put("system", CopilotDebugLog.clip(system.content, 20_000))
            put("tools", JsonArray(tools.map { JsonPrimitive(it.name) }))
            put("history", JsonArray(history.map(::logMessage)))
        }
        while (true) {
            val callStart = System.nanoTime()
            val r = llm.chat(listOf(system) + history + turn, tools, LlmToolChoice.Auto, config.maxTokens)
            val callMs = (System.nanoTime() - callStart) / 1_000_000
            llmCalls++
            val ok = when (r) {
                is LlmResult.Failure -> {
                    debug?.record("llm_error") { put("thread", threadId); put("capture", options.captureId); put("round", iterations + 1); put("error", r.error.message); put("ms", callMs) }
                    return finish(TurnResult.Failed(TurnError.Llm(r.error)))
                }
                is LlmResult.Ok -> r.reply
            }
            ok.usage?.let { u ->
                promptTokens += u.promptTokens; completionTokens += u.completionTokens; cachedTokens += u.cachedTokens
                u.costUsd?.let { costUsd = (costUsd ?: 0.0) + it }
            }
            val reply = ok.message
            debug?.record("llm") {
                put("thread", threadId); put("capture", options.captureId); put("round", iterations + 1); put("ms", callMs)
                put("model", ok.model); put("generation_id", ok.generationId); put("finish", ok.finishReason)
                ok.usage?.let { u ->
                    putJsonObject("usage") {
                        put("prompt", u.promptTokens); put("completion", u.completionTokens); put("cached", u.cachedTokens); put("cache_write", u.cacheWriteTokens)
                        u.costUsd?.let { put("cost_usd", it) }
                    }
                }
                put("text", CopilotDebugLog.clip(reply.content))
                put("tool_calls", JsonArray(reply.toolCalls.map { c -> CopilotDebugLog.obj { put("id", c.id); put("name", c.name); put("arguments", CopilotDebugLog.clip(c.arguments)) } }))
            }
            if (reply.toolCalls.isEmpty()) {
                val text = reply.content?.trim().orEmpty()
                if (text.isEmpty()) return finish(TurnResult.Failed(TurnError.Llm(LlmError.Malformed("empty reply"))))
                chat.append(threadId, NewChatMessage(ChatRole.ASSISTANT, text, captureId = options.captureId), time.now())
                return finish(TurnResult.Answered(text))
            }
            if (iterations >= config.maxToolIterations) return finish(TurnResult.Failed(TurnError.IterationLimit))
            iterations++
            chat.append(
                threadId,
                NewChatMessage(ChatRole.ASSISTANT, reply.content?.takeIf { it.isNotBlank() }, toolCallsJson = ToolCallsCodec.encode(reply.toolCalls), captureId = options.captureId),
                time.now(),
            )
            turn += reply
            val suspended = mutableListOf<String>()
            for (call in reply.toolCalls) {
                val toolStart = System.nanoTime()
                val exec = registry.execute(call, ctx.copy(now = time.now()), options.toolFilter)
                debug?.record("tool") {
                    put("thread", threadId); put("capture", options.captureId); put("round", iterations); put("name", call.name)
                    put("arguments", CopilotDebugLog.clip(call.arguments)); put("result", CopilotDebugLog.clip(exec.content))
                    put("card", exec.card != null); put("ms", (System.nanoTime() - toolStart) / 1_000_000)
                }
                val card = exec.card?.let(CardCodec::encode)
                chat.append(
                    threadId,
                    NewChatMessage(
                        ChatRole.TOOL, exec.content, toolCallId = call.id, toolName = call.name,
                        cardType = card?.first, cardJson = card?.second, captureId = options.captureId,
                    ),
                    time.now(),
                )
                turn += LlmMessage.Tool(call.id, exec.content)
                (exec.outcome as? ToolOutcome.Suspend)?.let { suspended += it.pendingInputId }
            }
            // Every call of the round is executed and persisted first (resolved items are logged even when another
            // item of the same sentence needs an answer), then the turn stops.
            if (suspended.isNotEmpty()) return finish(TurnResult.AwaitingInput(suspended))
        }
    }

    private fun logMessage(m: LlmMessage): JsonObject = CopilotDebugLog.obj {
        when (m) {
            is LlmMessage.System -> { put("role", "system"); put("content", CopilotDebugLog.clip(m.content)) }
            is LlmMessage.User -> { put("role", "user"); put("content", CopilotDebugLog.clip(m.content)) }
            is LlmMessage.Assistant -> {
                put("role", "assistant"); put("content", CopilotDebugLog.clip(m.content))
                if (m.toolCalls.isNotEmpty()) put("tool_calls", JsonArray(m.toolCalls.map { c -> CopilotDebugLog.obj { put("name", c.name); put("arguments", CopilotDebugLog.clip(c.arguments)) } }))
            }
            is LlmMessage.Tool -> { put("role", "tool"); put("content", CopilotDebugLog.clip(m.content)) }
        }
    }
}
