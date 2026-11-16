package com.lifehub.app.copilot.agent

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
) {
    /** Stores [text] as a USER message, then runs the turn. */
    suspend fun send(threadId: String, text: String, options: TurnOptions = TurnOptions()): TurnResult {
        chat.append(threadId, NewChatMessage(ChatRole.USER, text.trim(), captureId = options.captureId), time.now())
        return run(threadId, options)
    }

    /** Runs (or retries) the turn for the stored thread, whose last turn starts at a USER message. */
    suspend fun run(threadId: String, options: TurnOptions = TurnOptions()): TurnResult =
        withTimeoutOrNull(config.turnTimeoutMs) { loop(threadId, options) } ?: TurnResult.Failed(TurnError.Timeout)

    private suspend fun loop(threadId: String, options: TurnOptions): TurnResult {
        val now = time.now()
        val ctx = ToolContext(now, time.zone, threadId, options.captureId)
        val sections = contextProviders.mapNotNull { it.section(ctx) } + options.extraContext
        val system = LlmMessage.System(AgentContext.systemPrompt(now, time.zone, sections))
        val tools = registry.definitions(options.toolFilter)
        val history = HistoryWindow.build(chat.messages(threadId), config.historyMaxMessages, config.historyToolTurns)
        val turn = mutableListOf<LlmMessage>()
        var iterations = 0
        while (true) {
            val reply = when (val r = llm.chat(listOf(system) + history + turn, tools, LlmToolChoice.Auto, config.maxTokens)) {
                is LlmResult.Failure -> return TurnResult.Failed(TurnError.Llm(r.error))
                is LlmResult.Ok -> r.reply.message
            }
            if (reply.toolCalls.isEmpty()) {
                val text = reply.content?.trim().orEmpty()
                if (text.isEmpty()) return TurnResult.Failed(TurnError.Llm(LlmError.Malformed("empty reply")))
                chat.append(threadId, NewChatMessage(ChatRole.ASSISTANT, text, captureId = options.captureId), time.now())
                return TurnResult.Answered(text)
            }
            if (iterations >= config.maxToolIterations) return TurnResult.Failed(TurnError.IterationLimit)
            iterations++
            chat.append(
                threadId,
                NewChatMessage(ChatRole.ASSISTANT, reply.content?.takeIf { it.isNotBlank() }, toolCallsJson = ToolCallsCodec.encode(reply.toolCalls), captureId = options.captureId),
                time.now(),
            )
            turn += reply
            val suspended = mutableListOf<String>()
            for (call in reply.toolCalls) {
                val exec = registry.execute(call, ctx.copy(now = time.now()), options.toolFilter)
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
            if (suspended.isNotEmpty()) return TurnResult.AwaitingInput(suspended)
        }
    }
}
