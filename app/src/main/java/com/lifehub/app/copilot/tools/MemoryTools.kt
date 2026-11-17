package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.agent.CopilotTool
import com.lifehub.app.copilot.agent.Param
import com.lifehub.app.copilot.agent.ParamType
import com.lifehub.app.copilot.agent.ToolArgs
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolKind
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.agent.ToolSchema
import com.lifehub.app.copilot.memory.MemorySaveResult
import com.lifehub.app.copilot.memory.UserMemoryStore
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `save_user_memory(key, value)` (WRITE, design doc §5/§10): stores one stable fact immediately. CHAT ONLY: quick (voice)
 * captures never get it ([chatOnly], enforced by [com.lifehub.app.copilot.agent.ToolPolicy]). Removal happens only by hand
 * in Copilot settings > Memory. Same key = the fact is updated (idempotent).
 */
class SaveUserMemoryTool(private val store: UserMemoryStore) : CopilotTool {
    override val name = "save_user_memory"
    override val description = "Saves one short, stable fact about the user for future conversations (goals, available equipment, " +
        "training days, preferences, disliked foods, current level on key exercises). Same key = update. Use snake_case keys " +
        "such as goals, equipment, training_days, preferences, disliked_foods (comma separated list), level_pull_up. Save only " +
        "what the user said and wants remembered; never numbers from the diary."
    override val kind = ToolKind.WRITE
    override val chatOnly = true
    override val schema = ToolSchema(listOf(
        Param("key", ParamType.Str("snake_case key, e.g. 'disliked_foods'.", minLength = 1, maxLength = UserMemoryStore.MAX_KEY)),
        Param("value", ParamType.Str("The fact, short, in Italian.", minLength = 1, maxLength = UserMemoryStore.MAX_VALUE)),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome =
        when (val r = store.save(args.string("key")!!, args.string("value")!!, ctx.now)) {
            is MemorySaveResult.Invalid -> ToolOutcome.Error(r.message)
            is MemorySaveResult.Saved -> ToolOutcome.Ok(buildJsonObject {
                put("saved", true); put("key", r.entry.key); put("value", r.entry.value)
                put("replaced", r.replaced?.let(::JsonPrimitive) ?: JsonNull)
                put("note", "Saved. The user can see, edit or delete it in Copilot settings > Memory.")
            })
        }
}
