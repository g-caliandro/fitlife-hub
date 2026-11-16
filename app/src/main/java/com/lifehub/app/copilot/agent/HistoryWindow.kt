package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmToolCall
import com.lifehub.app.data.model.ChatMessage
import com.lifehub.app.data.model.ChatRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Tool calls of an ASSISTANT chat message <-> `toolCallsJson` (`[{"id","name","arguments"}]`). */
object ToolCallsCodec {
    fun encode(calls: List<LlmToolCall>): String? = if (calls.isEmpty()) null else buildJsonArray {
        calls.forEach { add(buildJsonObject { put("id", it.id); put("name", it.name); put("arguments", it.arguments) }) }
    }.toString()

    fun decode(json: String?): List<LlmToolCall> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            (kotlinx.serialization.json.Json.parseToJsonElement(json) as JsonArray).mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
                LlmToolCall(s("id") ?: return@mapNotNull null, s("name") ?: return@mapNotNull null, s("arguments") ?: "{}")
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * The limited slice of a thread sent to the model (design doc §10).
 * - The thread is split into turns (a turn starts at a USER message).
 * - The current (last) turn and the [toolTurns] turns before it keep their tool calls and results; older turns keep
 *   only the user text and the assistant's final text (old tool results are omitted).
 * - At most [maxMessages] messages are kept (oldest dropped), but the current turn is never cut, and the window always
 *   starts at a USER message so no tool result is orphaned.
 * - An assistant tool-call message whose results are not all stored (turn interrupted) is dropped with its results.
 */
object HistoryWindow {
    fun build(messages: List<ChatMessage>, maxMessages: Int, toolTurns: Int): List<LlmMessage> {
        val turns = mutableListOf<MutableList<ChatMessage>>()
        messages.sortedBy { it.seq }.forEach { m ->
            if (m.role == ChatRole.USER || turns.isEmpty()) turns += mutableListOf(m) else turns.last() += m
        }
        val converted = turns.mapIndexed { i, turn ->
            val full = i >= turns.size - 1 - toolTurns
            convert(turn, keepTools = full)
        }
        val current = converted.lastOrNull().orEmpty()
        val flat = converted.flatten()
        var window = flat.takeLast(maxOf(maxMessages, current.size))
        val firstUser = window.indexOfFirst { it is LlmMessage.User }
        window = if (firstUser < 0) emptyList() else window.drop(firstUser)
        return window
    }

    private fun convert(turn: List<ChatMessage>, keepTools: Boolean): List<LlmMessage> {
        val results = turn.filter { it.role == ChatRole.TOOL && it.toolCallId != null }.associateBy { it.toolCallId!! }
        val out = mutableListOf<LlmMessage>()
        for (m in turn) when (m.role) {
            ChatRole.USER -> m.text?.let { out += LlmMessage.User(it) }
            ChatRole.TOOL -> Unit // emitted right after the assistant message that requested it
            ChatRole.ASSISTANT -> {
                val calls = ToolCallsCodec.decode(m.toolCallsJson)
                if (calls.isEmpty()) {
                    if (!m.text.isNullOrBlank()) out += LlmMessage.Assistant(m.text)
                } else if (keepTools && calls.all { it.id in results }) {
                    out += LlmMessage.Assistant(m.text?.takeIf { it.isNotBlank() }, calls)
                    calls.forEach { c -> out += LlmMessage.Tool(c.id, results.getValue(c.id).text ?: "{}") }
                }
            }
        }
        return out
    }
}
