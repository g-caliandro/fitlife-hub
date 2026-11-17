package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.agent.CopilotTool
import com.lifehub.app.copilot.agent.MealSuggestionCard
import com.lifehub.app.copilot.agent.Param
import com.lifehub.app.copilot.agent.ParamType
import com.lifehub.app.copilot.agent.SuggestionItemView
import com.lifehub.app.copilot.agent.ToolArgs
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolKind
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.agent.ToolSchema
import com.lifehub.app.copilot.capture.CaptureStore
import com.lifehub.app.copilot.capture.FoodLogWriter
import com.lifehub.app.copilot.capture.LogItem
import com.lifehub.app.copilot.food.FoodRefs
import com.lifehub.app.copilot.food.Quantity
import com.lifehub.app.copilot.food.QuantityNormalizer
import com.lifehub.app.copilot.memory.UserMemoryStore
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.data.repo.FoodRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID
import kotlin.math.roundToInt

/**
 * `suggest_meal(title, items)` (READ with an optional app action, design doc use case 7): the model proposes a dish as
 * resolved food refs + grams; the app computes kcal and macros from the values per 100 g exactly as the meal writer does
 * (the model never passes macros), adds the coverage of the day's remaining amounts and draws a "Meal suggestion" card.
 * Nothing is written: "Log as meal" on the card does that ([MealSuggestions.logAsMeal]). Foods in the user's disliked
 * list (user memory) and model estimates are refused.
 */
class SuggestMealTool(
    private val lookup: FoodRefLookup,
    private val food: FoodRepository,
    private val memory: UserMemoryStore,
    private val settings: () -> Settings,
) : CopilotTool {
    override val name = "suggest_meal"
    override val description = "Shows the user a meal suggestion card (e.g. for 'cosa preparo per cena?'). First read what is " +
        "left today with get_daily_summary, resolve each food with resolve_food, then call this with the refs and grams. " +
        "The app computes kcal, macros and how much of the remaining they cover: never pass macros. It does NOT log " +
        "anything; the card has a 'Log as meal' button. Avoid the user's disliked foods."
    override val kind = ToolKind.READ
    override val schema = ToolSchema(listOf(
        Param("title", ParamType.Str("Dish name in Italian, e.g. 'Salmone con riso e zucchine'.", minLength = 1, maxLength = 80)),
        Param("items", ParamType.Arr(ParamType.Obj(listOf(
            Param("ref", ParamType.Str("The ref returned by resolve_food.", minLength = 1, maxLength = 160)),
            Param("grams", ParamType.Num("Grams of this food.", min = 1.0, max = 2000.0)),
        )), minItems = 1, maxItems = 8)),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val disliked = UserMemoryStore.dislikedFoods(memory.all())
        val errors = mutableListOf<String>()
        val items = args.array("items")!!.mapIndexedNotNull { i, el ->
            val a = ToolArgs(el as JsonObject)
            val ref = a.string("ref")!!.trim()
            if (FoodRefs.kind(ref) == "estimate") { errors += "'items[$i]': estimates can't be suggested; use foods found by resolve_food."; return@mapIndexedNotNull null }
            val c = when (val r = lookup.lookup(ref, ctx.captureId)) {
                is FoodRefLookup.Result.Found -> r.food
                else -> { errors += "'items[$i].ref' is unknown: call resolve_food and use the ref it returns."; return@mapIndexedNotNull null }
            }
            UserMemoryStore.dislikedMatch(c.displayName + " " + c.aliases.joinToString(" "), disliked)?.let {
                errors += "'items[$i]' (${c.name}) is in the user's disliked foods ('$it'): suggest something else."
                return@mapIndexedNotNull null
            }
            val grams = QuantityNormalizer.round1(a.double("grams")!!)
            // Same formula as FoodLogWriter (Food.macrosFor): value per 100 g x grams / 100.
            val f = grams / 100.0
            SuggestionItemView(c.name, c.brand, grams, c.kcal100 * f, c.protein100 * f, c.carbs100 * f, c.fat100 * f, c.source.wire, c)
        }
        if (errors.isNotEmpty()) return ToolOutcome.Error(errors.take(6).joinToString(" "))

        val date = ctx.today
        val summary = dailySummary(date, food.observeNutrition(date, date).first().firstOrNull(), settings())
        val card = MealSuggestionCard(
            UUID.randomUUID().toString(), args.string("title")!!.trim(), date.toString(), items,
            items.sumOf { it.kcal }, items.sumOf { it.proteinG }, items.sumOf { it.carbsG }, items.sumOf { it.fatG },
            summary.kcalRemaining, summary.proteinRemainingG, summary.carbsRemainingG, summary.fatRemainingG,
        )
        val result = buildJsonObject {
            put("status", "shown_not_logged")
            put("items", JsonArray(items.map { s ->
                buildJsonObject {
                    put("name", s.name); put("grams", s.grams); put("kcal", s.kcal.roundToInt())
                    put("protein_g", round1(s.proteinG)); put("carbs_g", round1(s.carbsG)); put("fat_g", round1(s.fatG))
                }
            }))
            putJsonObject("total") {
                put("kcal", card.totalKcal.roundToInt()); put("protein_g", round1(card.totalProteinG))
                put("carbs_g", round1(card.totalCarbsG)); put("fat_g", round1(card.totalFatG))
            }
            putJsonObject("remaining_after") {
                put("kcal", MealCoverage.after(card.kcalRemaining, card.totalKcal)); put("protein_g", MealCoverage.after(card.proteinRemainingG, card.totalProteinG))
                put("carbs_g", MealCoverage.after(card.carbsRemainingG, card.totalCarbsG)); put("fat_g", MealCoverage.after(card.fatRemainingG, card.totalFatG))
            }
            put("note", "Not logged. The card shows the details and a 'Log as meal' button: reply in one or two short sentences, " +
                "without repeating the numbers item by item.")
        }
        return ToolOutcome.Ok(result, card)
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}

/** Coverage of the day's remaining amounts by a suggested meal (pure; the card and the tool result share it). */
object MealCoverage {
    /** Remaining after the meal (negative = over), JSON null when there is no target. */
    fun after(remaining: Int?, meal: Double): JsonPrimitive = remaining?.let { JsonPrimitive(it - meal.roundToInt()) } ?: JsonNull

    /** Percent of [remaining] the meal covers; null without a target or when nothing is left. */
    fun percent(remaining: Int?, meal: Double): Int? = remaining?.takeIf { it > 0 }?.let { (meal * 100 / it).roundToInt() }
}

sealed interface LogAsMealResult {
    data class Logged(val captureId: String, val entries: Int) : LogAsMealResult
    data class AlreadyLogged(val captureId: String, val status: CaptureStatus) : LogAsMealResult
    data class Failed(val captureId: String) : LogAsMealResult
}

/**
 * "Log as meal" on a suggestion card (an app action, never a model tool): writes the suggested foods and grams now through
 * the same capture + [FoodLogWriter] path as `log_meal`, so the diary gets the values the card showed and Undo
 * ([com.lifehub.app.copilot.capture.CaptureProcessor.undo]) removes exactly what it wrote. One capture per suggestion
 * ([MealSuggestionCard.captureId]): a second tap, or a tap after Undo, logs nothing.
 */
class MealSuggestions(
    private val captures: CaptureStore,
    private val writer: FoodLogWriter,
    private val time: TimeSource,
) {
    suspend fun logAsMeal(card: MealSuggestionCard, threadId: String?): LogAsMealResult {
        val id = card.captureId
        val existing = captures.get(id)
        if (existing != null && existing.status != CaptureStatus.FAILED) return LogAsMealResult.AlreadyLogged(id, existing.status)
        if (existing == null) captures.create(threadId, ChatOrigin.CHAT, "Log as meal: ${card.title}", time.zone, time.now(), id = id)
        captures.move(id, CaptureStatus.PROCESSING, time.now())
        val now = time.now()
        return try {
            val items = card.items.mapIndexed { i, s -> LogItem(s.food, Quantity.Resolved(s.grams), "sugg#$i", s.name) }
            val written = writer.log(id, now.atZone(time.zone).toLocalDate(), now, items, now)
            captures.move(id, CaptureStatus.DONE, time.now(), outcome = "Logged: ${card.title}")
            LogAsMealResult.Logged(id, written.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // The write is one transaction: nothing was written. FAILED lets the user tap again.
            runCatching { captures.move(id, CaptureStatus.FAILED, time.now(), errorCode = "LogAsMeal", errorMessage = "The meal could not be logged.") }
            LogAsMealResult.Failed(id)
        }
    }
}
