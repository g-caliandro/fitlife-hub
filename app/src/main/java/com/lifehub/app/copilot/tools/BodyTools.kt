package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.agent.CopilotTool
import com.lifehub.app.copilot.agent.Param
import com.lifehub.app.copilot.agent.ParamType
import com.lifehub.app.copilot.agent.ToolArgs
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolKind
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.agent.ToolSchema
import com.lifehub.app.copilot.agent.WeightLoggedCard
import com.lifehub.app.copilot.capture.BodyLogWriter
import com.lifehub.app.copilot.capture.CaptureStateMachine
import com.lifehub.app.copilot.capture.CaptureStore
import com.lifehub.app.copilot.capture.WeightLogResult
import com.lifehub.app.data.model.CaptureStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Phase 5 body-weight tool: log_body_weight (WRITE). */
object BodyTools {
    /** Plausible adult body weight range; anything outside is refused as a probable misunderstanding. */
    const val MIN_KG = 30.0
    const val MAX_KG = 300.0

    fun all(writer: BodyLogWriter, captures: CaptureStore): List<CopilotTool> = listOf(LogBodyWeightTool(writer, captures))
}

/**
 * `log_body_weight(weight_kg, date)`: writes now, Undo from the card or the notification (design doc §5 and §7, owner
 * decisions 5 and 12). Direct write returning the logged value; idempotent per capture and date; future dates and
 * implausible values are refused with a readable error.
 */
class LogBodyWeightTool(private val writer: BodyLogWriter, private val captures: CaptureStore) : CopilotTool {
    override val name = "log_body_weight"
    override val description = "Writes the user's body weight to the diary immediately (the user can undo it). One weight per day: " +
        "if the day already has one the weight is replaced (muscle and fat are kept). Pass the date as YYYY-MM-DD (omit for today); " +
        "never a future date. Only the weight in kilograms, no calculations."
    override val kind = ToolKind.WRITE
    override val schema = ToolSchema(listOf(
        Param("weight_kg", ParamType.Num("Body weight in kg, e.g. 74.2.", min = BodyTools.MIN_KG, max = BodyTools.MAX_KG)),
        Param("date", ParamType.Date("Day the weight refers to, YYYY-MM-DD (\"stamattina\" = today). Omit for today."), required = false),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val captureId = ctx.captureId ?: return ToolOutcome.Error("log_body_weight is not available in this conversation.")
        val capture = captures.get(captureId) ?: return ToolOutcome.Error("log_body_weight is not available in this conversation.")
        if (capture.status == CaptureStatus.CANCELLED) return ToolOutcome.Error("The user undid this request. Do not log it again; the user can ask again if they want.")
        if (!CaptureStateMachine.acceptsWrites(capture.status)) return ToolOutcome.Error("This request cannot write right now.")
        val date = args.date("date") ?: ctx.today
        if (date.isAfter(ctx.today)) return ToolOutcome.Error("$date is in the future: future dates are not allowed. Ask the user which day the weight is from.")
        val kg = Math.round(args.double("weight_kg")!! * 10) / 10.0  // the app stores one decimal, like its own form

        return when (val r = writer.log(captureId, date, kg, ctx.now)) {
            is WeightLogResult.AlreadyLogged -> ToolOutcome.Ok(buildJsonObject {
                put("already_logged", true); put("date", date.toString()); put("weight_kg", r.weightKg?.let(::JsonPrimitive) ?: JsonNull)
                put("note", "This request already logged the weight of $date (shown above). Do not log it again; reply in one short sentence.")
            })
            is WeightLogResult.Logged -> ToolOutcome.Ok(
                buildJsonObject {
                    put("logged", true); put("id", date.toString()); put("date", date.toString()); put("weight_kg", r.entry.weightKg)
                    put("previous_weight_kg", r.previous?.weightKg?.let(::JsonPrimitive) ?: JsonNull)
                    put("note", "Already in the diary; the card shows it. " +
                        (if (r.previous != null) "It replaced the weight already logged for that day. " else "") + "Reply in one short sentence.")
                },
                WeightLoggedCard(captureId, date.toString(), r.entry.weightKg, r.previous?.weightKg),
            )
        }
    }
}
