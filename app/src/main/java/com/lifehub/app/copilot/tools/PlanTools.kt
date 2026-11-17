package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.agent.CopilotTool
import com.lifehub.app.copilot.agent.DraftExerciseView
import com.lifehub.app.copilot.agent.DraftPlanView
import com.lifehub.app.copilot.agent.Param
import com.lifehub.app.copilot.agent.ParamType
import com.lifehub.app.copilot.agent.PlanDraftCard
import com.lifehub.app.copilot.agent.ToolArgs
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolKind
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.agent.ToolSchema
import com.lifehub.app.copilot.plan.DraftCodec
import com.lifehub.app.copilot.plan.DraftExercise
import com.lifehub.app.copilot.plan.DraftGoal
import com.lifehub.app.copilot.plan.DraftPayload
import com.lifehub.app.copilot.plan.DraftPlan
import com.lifehub.app.copilot.plan.PlanDraftStore
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.ExerciseCategory
import com.lifehub.app.data.model.PlanDraft
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.data.repo.GoalRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Phase 6 plan tools: propose_workout_plan (DRAFT). Drafts are read back by get_workout_plan. */
object PlanTools {
    val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    fun all(drafts: PlanDraftStore, goals: GoalRepository, findExercise: suspend (String) -> Exercise?): List<CopilotTool> =
        listOf(ProposeWorkoutPlanTool(drafts, goals, findExercise))

    private fun weekdayShort(iso: Int) = DayOfWeek.of(iso).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** "4 × 8", "3 × MAX", "2 × 30 s", "3 × 8 +10 kg" (same wording as the plan screens). */
    fun summary(e: DraftExercise): String {
        val target = when (e.targetType) {
            TargetType.MAX -> "MAX"
            TargetType.SECONDS -> "${e.targetValue ?: 0} s"
            TargetType.REPS -> "${e.targetValue ?: 0}"
        }
        val w = e.weightKg?.takeIf { it > 0 }?.let { " +${fmt(it)} kg" }.orEmpty()
        return "${e.sets} × $target$w"
    }

    fun card(d: PlanDraft, p: DraftPayload = DraftCodec.decode(d)) = PlanDraftCard(
        d.id, d.title, p.goal?.name, p.goal?.weeks,
        p.plans.map { pl ->
            DraftPlanView(pl.name, pl.weekdays.sorted().joinToString(" · ", transform = ::weekdayShort),
                pl.exercises.map { DraftExerciseView(it.name, summary(it), it.isNew) })
        },
    )

    /** Model-facing JSON of a draft; [full] = with its plans and exercises (open drafts), else only the header. */
    fun json(d: PlanDraft, full: Boolean): JsonObject = buildJsonObject {
        val p = DraftCodec.decode(d)
        put("draft_id", d.id); put("status", d.status.name.lowercase()); put("title", d.title)
        put("created", d.createdAt.toString().take(10))
        put("goal", p.goal?.let { g -> buildJsonObject { put("name", g.name); put("weeks", g.weeks?.let(::JsonPrimitive) ?: JsonNull) } } ?: JsonNull)
        if (full) put("plans", JsonArray(p.plans.map { pl ->
            buildJsonObject {
                put("name", pl.name)
                put("weekdays", JsonArray(pl.weekdays.sorted().map { JsonPrimitive(WEEKDAYS[it - 1]) }))
                put("exercises", JsonArray(pl.exercises.map { e ->
                    buildJsonObject { put("name", e.name); put("sets_targets", summary(e)); put("rest_seconds", e.restSeconds); if (e.isNew) put("new_exercise", true) }
                }))
            }
        }))
    }
}

/**
 * `propose_workout_plan(plan)` (DRAFT, design doc §5/§7, owner decisions 3-4): ALWAYS saves a draft in `plan_drafts`,
 * never touches the plans table and never replaces an active plan. The user approves (or rejects) it from the card or the
 * workouts screen. Exercises are matched by name (case-insensitive) to the user's library; an unknown one needs its
 * category and becomes a custom exercise only on Approve (the app has no built-in catalogue).
 * Idempotent per capture: a re-run of the same message returns the draft already saved.
 */
class ProposeWorkoutPlanTool(
    private val drafts: PlanDraftStore,
    private val goals: GoalRepository,
    private val findExercise: suspend (String) -> Exercise?,
) : CopilotTool {
    override val name = "propose_workout_plan"
    override val description = "Saves a workout plan as a DRAFT for the user to approve; it never becomes active by itself and " +
        "never replaces the active plans. A plan = optional goal (name + duration in weeks, or the id of an existing goal) and " +
        "1-7 weekday plans, each one session repeated on its weekdays with exercises, sets and targets. There are no " +
        "different weeks: targets are raised automatically after good sessions. Use the user's exercise names from " +
        "get_workout_plan; a new exercise needs its category. To revise an open draft pass replaces_draft_id."
    override val kind = ToolKind.DRAFT
    override val schema = ToolSchema(listOf(
        Param("title", ParamType.Str("Short title, e.g. 'Verso il muscle up'.", minLength = 1, maxLength = 60)),
        Param("goal", ParamType.Obj(listOf(
            Param("name", ParamType.Str("Goal, e.g. 'Muscle up'.", minLength = 1, maxLength = 80), required = false),
            Param("weeks", ParamType.Integer("Duration: the deadline is this many weeks after approval.", min = 1, max = 104), required = false),
            Param("goal_id", ParamType.Str("Id of an existing goal from get_goals, instead of a new one.", maxLength = 64), required = false),
        ), "Optional goal the plans work towards."), required = false),
        Param("plans", ParamType.Arr(ParamType.Obj(listOf(
            Param("name", ParamType.Str("Plan name, e.g. 'Pull A'.", minLength = 1, maxLength = 60)),
            Param("weekdays", ParamType.Arr(ParamType.Str(enum = PlanTools.WEEKDAYS), "Days the session repeats on.", minItems = 1, maxItems = 7)),
            Param("day_type", ParamType.Str(enum = listOf("pull", "push", "legs", "core")), required = false),
            Param("notes", ParamType.Str(maxLength = 200), required = false),
            Param("exercises", ParamType.Arr(ParamType.Obj(listOf(
                Param("name", ParamType.Str("Exercise name; reuse the user's names.", minLength = 1, maxLength = 60)),
                Param("category", ParamType.Str("Needed only for an exercise the user does not have yet.", enum = listOf("pull", "push", "core", "legs")), required = false),
                Param("sets", ParamType.Integer(min = 1, max = 20)),
                Param("target_type", ParamType.Str("reps = fixed reps, max = as many as possible, seconds = timed hold.", enum = listOf("reps", "max", "seconds"))),
                Param("target_value", ParamType.Integer("Reps or seconds; omit for max.", min = 1, max = 600), required = false),
                Param("weight_kg", ParamType.Num("Added load in kg.", min = 0.0, max = 300.0), required = false),
                Param("rest_seconds", ParamType.Integer("Rest between sets (default 90).", min = 0, max = 600), required = false),
            )), minItems = 1, maxItems = 15)),
        )), minItems = 1, maxItems = 7)),
        Param("replaces_draft_id", ParamType.Str("An open draft this one replaces (it becomes superseded).", maxLength = 64), required = false),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        ctx.captureId?.let { cap ->
            drafts.byCapture(cap).lastOrNull()?.let { d ->
                return ToolOutcome.Ok(saved(d, alreadySaved = true), PlanTools.card(d))
            }
        }
        val errors = mutableListOf<String>()
        val goal = args.obj("goal")?.let { g ->
            val id = g.string("goal_id")?.trim()?.takeIf { it.isNotEmpty() }
            if (id != null) {
                val existing = goals.get(id)
                if (existing == null) { errors += "'goal.goal_id' is unknown: use an id from get_goals or give name and weeks."; null }
                else DraftGoal(existing.name, null, existing.id)
            } else {
                val name = g.string("name")?.trim()
                val weeks = g.int("weeks")
                if (name.isNullOrEmpty() || weeks == null) { errors += "'goal' needs 'name' and 'weeks' (or 'goal_id')."; null }
                else DraftGoal(name, weeks)
            }
        }
        val plans = args.array("plans")!!.mapIndexed { i, el ->
            val p = ToolArgs(el as JsonObject)
            val exercises = p.array("exercises")!!.mapIndexedNotNull { j, xe ->
                val x = ToolArgs(xe as JsonObject)
                val path = "plans[$i].exercises[$j]"
                val name = x.string("name")!!.trim()
                val type = TargetType.valueOf(x.string("target_type")!!.uppercase())
                val value = x.int("target_value")
                if (type != TargetType.MAX && value == null) { errors += "'$path.target_value' is required for ${type.name.lowercase()}."; return@mapIndexedNotNull null }
                val known = findExercise(name)
                val category = known?.category ?: x.string("category")?.let { ExerciseCategory.valueOf(it.uppercase()) }
                if (category == null) {
                    errors += "'$path': the user has no exercise called '$name'. Use one of their exercises (get_workout_plan) or give 'category' to add it as a new exercise."
                    return@mapIndexedNotNull null
                }
                DraftExercise(known?.name ?: name, category, known?.id, x.int("sets")!!, type,
                    if (type == TargetType.MAX) null else value, x.double("weight_kg")?.takeIf { it > 0 }, x.int("rest_seconds") ?: 90)
            }
            DraftPlan(
                p.string("name")!!.trim(),
                p.array("weekdays")!!.map { PlanTools.WEEKDAYS.indexOf((it as JsonPrimitive).content) + 1 }.distinct().sorted(),
                p.string("day_type")?.let { DayType.valueOf(it.uppercase()) }, p.string("notes")?.trim()?.takeIf { it.isNotEmpty() }, exercises,
            )
        }
        val replaces = args.string("replaces_draft_id")?.trim()?.takeIf { it.isNotEmpty() }
        replaces?.let { id ->
            val old = drafts.get(id)
            when {
                old == null -> errors += "'replaces_draft_id' is unknown."
                old.status != PlanDraftStatus.DRAFT -> errors += "Draft $id is already ${old.status.name.lowercase()}: it cannot be replaced or brought back. Omit replaces_draft_id to save a new draft."
            }
        }
        if (errors.isNotEmpty()) return ToolOutcome.Error(errors.take(6).joinToString(" "))

        val d = drafts.propose(args.string("title")!!.trim(), DraftPayload(goal, plans), ctx.threadId, ctx.captureId, ctx.now, replaces)
        return ToolOutcome.Ok(saved(d, alreadySaved = false), PlanTools.card(d))
    }

    private fun saved(d: PlanDraft, alreadySaved: Boolean) = buildJsonObject {
        put("draft_id", d.id); put("status", d.status.name.lowercase())
        val newExercises = DraftCodec.decode(d).plans.flatMap { it.exercises }.filter { it.isNew }.map { it.name }.distinct()
        if (newExercises.isNotEmpty()) put("new_exercises", JsonArray(newExercises.map(::JsonPrimitive)))
        put("note", buildString {
            if (alreadySaved) append("This request already saved this draft; it was not saved again. ")
            append("Saved as a DRAFT only: it is NOT active and no plan changed. The user can Approve or Reject it from the card " +
                "or the workouts screen. Reply in one short sentence; do not list the exercises (the card shows them).")
        })
    }
}
