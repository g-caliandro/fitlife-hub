package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.llm.LlmTool
import com.lifehub.app.copilot.llm.LlmToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Design doc §5 classification. Drives what the loop and the UI may do with a tool (e.g. quick capture filters). */
enum class ToolKind {
    /** Returns data immediately, no side effects. */
    READ,
    /** Writes to the DB immediately, in a transaction, idempotent per capture (phase 3+). */
    WRITE,
    /** Saves a draft that needs the user's approval (phase 6). */
    DRAFT,
    /** Suspends the turn with a saved pending request (phase 3+). */
    INPUT_REQUEST,
}

/**
 * Per-turn context handed to every tool. [captureId] is set when the turn runs for a capture (phase 3+): write tools
 * key their idempotency on it. [threadId] is null for turns that are not attached to a chat thread.
 */
data class ToolContext(
    val now: Instant,
    val zone: ZoneId,
    val threadId: String? = null,
    val captureId: String? = null,
) {
    val today: LocalDate get() = now.atZone(zone).toLocalDate()
}

/** What a tool returns. Everything except [Ok.card] is sent back to the model as JSON. */
sealed interface ToolOutcome {
    /** [result] goes to the model; [card] (optional) is drawn natively in the chat from the same data. */
    data class Ok(val result: JsonObject, val card: ChatCard? = null) : ToolOutcome
    /** A readable problem the model can act on (bad date range, unknown id, ...). Never a crash. */
    data class Error(val message: String) : ToolOutcome
    /**
     * INPUT_REQUEST tools: a pending request [pendingInputId] was saved; [result] still goes to the model (as the tool
     * result) but [AgentLoop] ends the turn after this round instead of calling the model again.
     */
    data class Suspend(val result: JsonObject, val pendingInputId: String, val card: ChatCard? = null) : ToolOutcome
}

/** One tool: name, model-facing description, strict argument schema, classification and app-side execution. */
interface CopilotTool {
    val name: String
    val description: String
    val kind: ToolKind
    val schema: ToolSchema
    /**
     * Only offered in the chat, never in a quick (voice) capture: e.g. `save_user_memory` (design doc §10, phase 6).
     * See [ToolPolicy].
     */
    val chatOnly: Boolean get() = false
    /** Called only with arguments that passed [schema] validation. */
    suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome
}

/** Result of running one model tool call through the registry. [content] is the JSON string for the TOOL message. */
data class ToolExecution(val call: LlmToolCall, val tool: CopilotTool?, val outcome: ToolOutcome) {
    val content: String get() = when (outcome) {
        is ToolOutcome.Ok -> outcome.result.toString()
        is ToolOutcome.Suspend -> outcome.result.toString()
        is ToolOutcome.Error -> errorJson(outcome.message).toString()
    }
    val card: ChatCard? get() = when (outcome) {
        is ToolOutcome.Ok -> outcome.card
        is ToolOutcome.Suspend -> outcome.card
        is ToolOutcome.Error -> null
    }
}

/** Which tools a capture may use, by where it came from (design doc §10: quick capture has no chat-only tools). */
object ToolPolicy {
    fun forOrigin(origin: com.lifehub.app.data.model.ChatOrigin): (CopilotTool) -> Boolean = when (origin) {
        com.lifehub.app.data.model.ChatOrigin.CHAT -> { _ -> true }
        com.lifehub.app.data.model.ChatOrigin.QUICK -> { t -> !t.chatOnly }
    }
}

internal fun errorJson(message: String): JsonObject = buildJsonObject { put("error", message) }

/**
 * All tools the copilot can call. Validates arguments before executing and turns every failure (unknown tool,
 * malformed JSON, schema violation, exception inside a tool) into a [ToolOutcome.Error] for the model.
 * Later phases register their WRITE/DRAFT/INPUT_REQUEST tools in the same list (see AppContainer).
 */
class ToolRegistry(tools: List<CopilotTool>) {
    val tools: List<CopilotTool> = tools.toList()
    private val byName: Map<String, CopilotTool> = tools.associateBy { it.name }

    init {
        require(byName.size == tools.size) { "duplicate tool names" }
        tools.forEach { require(NAME.matches(it.name)) { "invalid tool name: ${it.name}" } }
    }

    operator fun get(name: String): CopilotTool? = byName[name]

    /** Model-facing definitions of the tools accepted by [filter] (e.g. quick capture hides save_user_memory). */
    fun definitions(filter: (CopilotTool) -> Boolean = { true }): List<LlmTool> =
        tools.filter(filter).map { LlmTool(it.name, it.description, it.schema.toJsonSchema()) }

    suspend fun execute(call: LlmToolCall, ctx: ToolContext, filter: (CopilotTool) -> Boolean = { true }): ToolExecution {
        val tool = byName[call.name]?.takeIf(filter)
            ?: return ToolExecution(call, null, ToolOutcome.Error("Unknown tool '${call.name.take(64)}'. Available: ${tools.filter(filter).joinToString { it.name }}."))
        val args = when (val v = tool.schema.validate(call.arguments)) {
            is ValidationResult.Invalid -> return ToolExecution(call, tool, ToolOutcome.Error("Invalid arguments for ${tool.name}: ${v.message}"))
            is ValidationResult.Valid -> v.args
        }
        val outcome = try {
            tool.execute(args, ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolOutcome.Error("The app failed to run ${tool.name}. Do not retry; tell the user it did not work.")
        }
        return ToolExecution(call, tool, outcome)
    }

    private companion object { val NAME = Regex("^[a-z][a-z0-9_]{0,63}$") }
}
