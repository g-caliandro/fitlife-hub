package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.agent.CopilotTool
import com.lifehub.app.copilot.agent.Param
import com.lifehub.app.copilot.agent.ParamType
import com.lifehub.app.copilot.agent.ToolArgs
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolKind
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.agent.ToolSchema
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.data.db.dao.BodyDao
import com.lifehub.app.data.repo.FoodRepository
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.data.repo.SessionRepository
import com.lifehub.app.domain.plannedOn
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Phase 2 read tools (design doc §5), adapted to the real data model. All numbers are computed by the app.
 * Keys in results are snake_case; nullable values are sent as JSON null ("not set" / "unknown").
 */
object ReadTools {
    const val MAX_PLANNED_DAYS = 31L
    const val MAX_WEIGHT_DAYS = 366L

    fun all(
        food: FoodRepository, settings: () -> Settings, goals: GoalRepository, plans: PlanRepository,
        sessions: SessionRepository, body: BodyDao, drafts: com.lifehub.app.copilot.plan.PlanDraftStore? = null,
    ): List<CopilotTool> = listOf(
        GetDailySummary(food, settings), GetNutritionTargets(settings), GetGoals(goals, plans),
        GetPlannedWorkouts(plans, sessions), GetWorkoutPlan(plans, goals, drafts), GetBodyWeight(body),
    )
}

private fun JsonObjectBuilder.putOrNull(key: String, v: Int?) = put(key, v?.let(::JsonPrimitive) ?: JsonNull)
private fun JsonObjectBuilder.putOrNull(key: String, v: Double?) = put(key, v?.let(::JsonPrimitive) ?: JsonNull)
private fun JsonObjectBuilder.putOrNull(key: String, v: String?) = put(key, v?.let(::JsonPrimitive) ?: JsonNull)
private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
private fun weekdayName(iso: Int) = java.time.DayOfWeek.of(iso).getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
private fun status(known: Boolean, complete: Boolean) = when { !known -> "unknown"; !complete -> "partial"; else -> "complete" }

/** Range check shared by the (from, to) tools; returns a readable error or null. */
private fun rangeError(from: LocalDate, to: LocalDate, maxDays: Long): String? = when {
    to.isBefore(from) -> "'to' ($to) is before 'from' ($from)."
    ChronoUnit.DAYS.between(from, to) + 1 > maxDays -> "The range is longer than $maxDays days; ask for a shorter one."
    else -> null
}

class GetDailySummary(private val food: FoodRepository, private val settings: () -> Settings) : CopilotTool {
    override val name = "get_daily_summary"
    override val description = "Food diary of one day: logged items, kcal/protein/carbs/fat totals, targets and what " +
        "remains (computed by the app, report these numbers as they are). Use it for questions like 'how much protein is left today'."
    override val kind = ToolKind.READ
    override val schema = ToolSchema(listOf(Param("date", ParamType.Date("Day as YYYY-MM-DD. Defaults to today."), required = false)))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val date = args.date("date") ?: ctx.today
        val day = food.observeNutrition(date, date).first().firstOrNull()
        val card = dailySummary(date, day, settings())
        val result = buildJsonObject {
            put("date", date.toString())
            put("weekday", weekdayName(date.dayOfWeek.value))
            putJsonObject("consumed") {
                put("kcal", card.kcal)
                putOrNull("protein_g", card.proteinG.takeIf { card.proteinKnown })
                putOrNull("carbs_g", card.carbsG.takeIf { card.carbsKnown })
                putOrNull("fat_g", card.fatG.takeIf { card.fatKnown })
            }
            putJsonObject("targets") {
                putOrNull("kcal", card.kcalTarget); putOrNull("protein_g", card.proteinTargetG)
                putOrNull("carbs_g", card.carbsTargetG); putOrNull("fat_g", card.fatTargetG)
            }
            putJsonObject("remaining") {
                putOrNull("kcal", card.kcalRemaining); putOrNull("protein_g", card.proteinRemainingG)
                putOrNull("carbs_g", card.carbsRemainingG); putOrNull("fat_g", card.fatRemainingG)
            }
            putJsonObject("macro_status") {
                put("protein", status(card.proteinKnown, card.proteinComplete))
                put("carbs", status(card.carbsKnown, card.carbsComplete))
                put("fat", status(card.fatKnown, card.fatComplete))
            }
            put("notes", "remaining = target - consumed; negative = over target; null = no target set or macro unknown. " +
                "'partial' = some items have that macro unknown, so consumed is a lower bound.")
            put("items", buildJsonArray {
                day?.entries.orEmpty().forEach { e ->
                    add(buildJsonObject {
                        put("name", e.name); putOrNull("brand", e.brand); put("grams", round1(e.grams))
                        put("kcal", e.kcal.roundToInt()); put("protein_g", round1(e.proteinG))
                        put("carbs_g", round1(e.carbsG)); put("fat_g", round1(e.fatG))
                    })
                }
                day?.quickAdds.orEmpty().forEach { q ->
                    add(buildJsonObject {
                        put("name", q.displayName); put("quick_add", true); put("kcal", q.kcal)
                        putOrNull("protein_g", q.proteinG); putOrNull("carbs_g", q.carbsG); putOrNull("fat_g", q.fatG)
                    })
                }
            })
        }
        return ToolOutcome.Ok(result, card)
    }
}

class GetNutritionTargets(private val settings: () -> Settings) : CopilotTool {
    override val name = "get_nutrition_targets"
    override val description = "The user's daily nutrition targets (kcal, protein, carbs, fat). null = not set."
    override val kind = ToolKind.READ
    override val schema = ToolSchema.EMPTY

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val s = settings()
        fun t(v: Int?) = v?.takeIf { it > 0 }
        return ToolOutcome.Ok(buildJsonObject {
            putOrNull("kcal", t(s.kcalTarget)); putOrNull("protein_g", t(s.proteinTargetG))
            putOrNull("carbs_g", t(s.carbsTargetG)); putOrNull("fat_g", t(s.fatTargetG))
        })
    }
}

class GetGoals(private val goals: GoalRepository, private val plans: PlanRepository) : CopilotTool {
    override val name = "get_goals"
    override val description = "The user's fitness goals (free text, e.g. 'Muscle up') with start date, deadline, state " +
        "and the workout plans linked to each. By default only active goals."
    override val kind = ToolKind.READ
    override val schema = ToolSchema(listOf(Param("include_inactive", ParamType.Bool("Also return achieved and archived goals."), required = false)))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val all = args.bool("include_inactive") == true
        val planNames = plans.observePlans().first().associate { it.plan.id to it.plan.name }
        val list = goals.observeGoals().first()
            .filter { all || it.goal.state == GoalState.ACTIVE }
            .sortedBy { it.goal.deadline }
        return ToolOutcome.Ok(buildJsonObject {
            put("goals", buildJsonArray {
                list.forEach { g ->
                    add(buildJsonObject {
                        put("id", g.goal.id); put("name", g.goal.name); putOrNull("notes", g.goal.notes)
                        put("state", g.goal.state.name.lowercase())
                        put("start_date", g.goal.startDate.toString()); put("deadline", g.goal.deadline.toString())
                        put("days_to_deadline", ChronoUnit.DAYS.between(ctx.today, g.goal.deadline))
                        putOrNull("achieved_on", g.goal.achievedAt?.toString())
                        put("linked_plans", buildJsonArray { g.links.mapNotNull { planNames[it.planId] }.forEach { add(JsonPrimitive(it)) } })
                    })
                }
            })
        })
    }
}

class GetPlannedWorkouts(private val plans: PlanRepository, private val sessions: SessionRepository) : CopilotTool {
    override val name = "get_planned_workouts"
    override val description = "Workouts scheduled day by day between two dates (max ${ReadTools.MAX_PLANNED_DAYS} days). " +
        "Plans repeat on fixed weekdays; an empty 'plans' list is a rest day. 'done' = a completed workout of that plan on that day."
    override val kind = ToolKind.READ
    override val schema = ToolSchema(listOf(
        Param("from", ParamType.Date("First day, YYYY-MM-DD.")),
        Param("to", ParamType.Date("Last day (inclusive), YYYY-MM-DD.")),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val from = args.date("from")!!; val to = args.date("to")!!
        rangeError(from, to, ReadTools.MAX_PLANNED_DAYS)?.let { return ToolOutcome.Error(it) }
        val all = plans.observePlans().first()
        val completed = sessions.observeCompleted().first().filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        return ToolOutcome.Ok(buildJsonObject {
            put("days", buildJsonArray {
                var d = from
                while (!d.isAfter(to)) {
                    val day = d
                    add(buildJsonObject {
                        put("date", day.toString()); put("weekday", weekdayName(day.dayOfWeek.value))
                        put("plans", buildJsonArray {
                            plannedOn(day, all).forEach { p ->
                                add(buildJsonObject {
                                    put("plan_id", p.plan.id); put("name", p.plan.name)
                                    putOrNull("day_type", p.plan.dayType?.name?.lowercase())
                                    put("exercises", p.exercises.size)
                                    put("done", completed.any { it.planId == p.plan.id && it.date == day })
                                })
                            }
                        })
                    })
                    d = d.plusDays(1)
                }
            })
        })
    }
}

class GetWorkoutPlan(
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val drafts: com.lifehub.app.copilot.plan.PlanDraftStore? = null,
) : CopilotTool {
    override val name = "get_workout_plan"
    override val description = "The active workout plans (several can be active, each repeats on its weekdays) with " +
        "exercises, sets, targets, weights and rest, plus linked goals, and the copilot's plan drafts with their status " +
        "(draft = waiting for approval; approved, rejected, superseded = decided). Targets are raised automatically after good sessions."
    override val kind = ToolKind.READ
    override val schema = ToolSchema.EMPTY

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val active = plans.observePlans().first().filter { !it.plan.isArchived }
        val goalsByPlan = goals.observeGoals().first().flatMap { g -> g.links.map { it.planId to g.goal.name } }
            .groupBy({ it.first }, { it.second })
        return ToolOutcome.Ok(buildJsonObject {
            put("plans", buildJsonArray { active.forEach { add(planJson(it, goalsByPlan[it.plan.id].orEmpty())) } })
            if (drafts != null) {
                // Open drafts in full; the most recent decided ones as a header only (never to be proposed again as they were).
                val all = drafts.all()
                val open = all.filter { it.status == com.lifehub.app.data.model.PlanDraftStatus.DRAFT }
                val decided = all.filter { it.status != com.lifehub.app.data.model.PlanDraftStatus.DRAFT }.take(MAX_DECIDED_DRAFTS)
                put("drafts", JsonArray(open.map { PlanTools.json(it, full = true) } + decided.map { PlanTools.json(it, full = false) }))
                put("drafts_note", "Drafts are NOT active plans. Rejected and superseded drafts are closed: never bring them back.")
            }
        })
    }

    private companion object { const val MAX_DECIDED_DRAFTS = 5 }

    private fun planJson(p: PlanWithExercises, goalNames: List<String>): JsonObject = buildJsonObject {
        put("plan_id", p.plan.id); put("name", p.plan.name)
        put("weekdays", JsonArray(p.plan.weekdays.sorted().map { JsonPrimitive(weekdayName(it)) }))
        putOrNull("day_type", p.plan.dayType?.name?.lowercase()); putOrNull("notes", p.plan.notes)
        put("goals", JsonArray(goalNames.map(::JsonPrimitive)))
        put("exercises", buildJsonArray {
            p.exercises.forEach { item ->
                val pe = item.planExercise
                add(buildJsonObject {
                    put("name", item.exercise.name); put("category", item.exercise.category.name.lowercase())
                    put("sets", pe.sets); put("target_type", pe.targetType.name.lowercase())
                    putOrNull("target_value", pe.targetValue); putOrNull("weight_kg", pe.weightKg)
                    put("rest_seconds", pe.restSeconds)
                })
            }
        })
    }
}

class GetBodyWeight(private val body: BodyDao) : CopilotTool {
    override val name = "get_body_weight"
    override val description = "Body weight entries (one per day, optional muscle and fat mass) between two dates " +
        "(max ${ReadTools.MAX_WEIGHT_DAYS} days), plus the latest entry ever logged."
    override val kind = ToolKind.READ
    override val schema = ToolSchema(listOf(
        Param("from", ParamType.Date("First day, YYYY-MM-DD.")),
        Param("to", ParamType.Date("Last day (inclusive), YYYY-MM-DD.")),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val from = args.date("from")!!; val to = args.date("to")!!
        rangeError(from, to, ReadTools.MAX_WEIGHT_DAYS)?.let { return ToolOutcome.Error(it) }
        val entries = body.observeBetween(from, to).first()
        val latest = body.latestDate()?.let { body.get(it) }
        fun entry(e: com.lifehub.app.data.model.BodyEntry) = buildJsonObject {
            put("date", e.date.toString()); put("weight_kg", e.weightKg)
            putOrNull("muscle_kg", e.muscleKg); putOrNull("fat_kg", e.fatKg)
        }
        return ToolOutcome.Ok(buildJsonObject {
            put("entries", JsonArray(entries.sortedBy { it.date }.map(::entry)))
            put("latest", latest?.let(::entry) ?: JsonNull)
        })
    }
}
