package com.lifehub.app.copilot.llm

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

sealed interface ConnectionTestResult {
    data class Success(val model: String) : ConnectionTestResult
    data class Failure(val error: LlmError) : ConnectionTestResult
}

/**
 * Minimal call that checks key, model and tool calling in one request: a forced call to a dummy `ping` tool with a tiny
 * token budget. A reply with no tool call means the provider ignored `tools` => [LlmError.ToolsUnsupported].
 */
class ConnectionTester(private val llm: LlmClient, private val model: () -> String) {
    suspend fun run(): ConnectionTestResult {
        val ping = LlmTool(
            "ping", "Connectivity check. Always call this tool.",
            buildJsonObject { put("type", "object"); putJsonObject("properties") {} },
        )
        return when (val r = llm.chat(
            listOf(LlmMessage.User("Connection test. Call the ping tool.")),
            tools = listOf(ping), toolChoice = LlmToolChoice.Function("ping"), maxTokens = 32,
        )) {
            is LlmResult.Failure -> ConnectionTestResult.Failure(r.error)
            is LlmResult.Ok ->
                if (r.reply.message.toolCalls.any { it.name == "ping" }) ConnectionTestResult.Success(model())
                else ConnectionTestResult.Failure(LlmError.ToolsUnsupported)
        }
    }
}
