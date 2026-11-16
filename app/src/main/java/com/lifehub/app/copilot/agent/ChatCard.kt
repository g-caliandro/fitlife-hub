package com.lifehub.app.copilot.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Native cards drawn by the app from tool results (never by the model). Persisted on the TOOL chat message as
 * ([CardCodec.type], JSON payload) so a reopened thread redraws the card exactly as it was shown.
 * New card kinds (meal logged, weight logged, pending input, plan draft, product) add a subclass + a [CardCodec] entry.
 */
sealed interface ChatCard

/**
 * "Daily summary": what was eaten on [date] vs targets. Values are the diary's rounded day totals
 * (`DayNutrition.asMacroDay()`); `remaining*` = target - consumed (negative = over), null when no target is set or
 * the macro is unknown for every item of the day. `*Known` = false: the macro is unknown ("—" in the diary);
 * `*Complete` = false: some items lack it, so the total is a lower bound.
 */
@Serializable
data class DailySummaryCard(
    val date: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val kcalTarget: Int? = null,
    val proteinTargetG: Int? = null,
    val carbsTargetG: Int? = null,
    val fatTargetG: Int? = null,
    val kcalRemaining: Int? = null,
    val proteinRemainingG: Int? = null,
    val carbsRemainingG: Int? = null,
    val fatRemainingG: Int? = null,
    val proteinKnown: Boolean = true,
    val carbsKnown: Boolean = true,
    val fatKnown: Boolean = true,
    val proteinComplete: Boolean = true,
    val carbsComplete: Boolean = true,
    val fatComplete: Boolean = true,
    val items: Int = 0,
) : ChatCard

/**
 * One logged item of a "Meal logged" card: the EXACT values stored on the diary entry [entryId] (same doubles; the view
 * rounds like the diary does), plus provenance: [source] wire name (history / base_table / open_food_facts /
 * llm_estimate) and the portion or household measure used ("0.5 x package (400 g)", "1 x cucchiaio (9 g)").
 */
@Serializable
data class MealItemView(
    val entryId: String,
    val name: String,
    val brand: String? = null,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: String,
    val portionLabel: String? = null,
    val servings: Double? = null,
    val portionGrams: Double? = null,
    val usedDefaultPortion: Boolean = false,
    val incomplete: Boolean = false,
    val stateMismatch: Boolean = false,
)

/**
 * "Meal logged": the items one `log_meal` call wrote for capture [captureId] on [date] at [time], their totals and the
 * day's remaining amounts after the write (null = no target / unknown). Undo acts on the whole capture.
 */
@Serializable
data class MealLoggedCard(
    val captureId: String,
    val date: String,
    val time: String? = null,
    val items: List<MealItemView>,
    val totalKcal: Double,
    val totalProteinG: Double,
    val totalCarbsG: Double,
    val totalFatG: Double,
    val kcalRemaining: Int? = null,
    val proteinRemainingG: Int? = null,
    val carbsRemainingG: Int? = null,
    val fatRemainingG: Int? = null,
) : ChatCard

@Serializable
data class PendingOptionView(val label: String, val isEstimate: Boolean = false, val detail: String? = null)

/** "Pending request": the question and its options; its live state (open / answered / discarded) comes from the store. */
@Serializable
data class PendingInputCard(
    val pendingInputId: String,
    val captureId: String,
    val kind: String,
    val prompt: String,
    val options: List<PendingOptionView> = emptyList(),
    val itemName: String? = null,
) : ChatCard

/**
 * "Weight logged": [weightKg] on [date]; [previousWeightKg] = the weight that day had before (replaced), null for a new
 * day. Undo acts on the whole capture ([captureId]) and restores the previous row exactly.
 */
@Serializable
data class WeightLoggedCard(
    val captureId: String,
    val date: String,
    val weightKg: Double,
    val previousWeightKg: Double? = null,
) : ChatCard

/** "Product": name, brand, values per 100 g and known portions (no actions). */
@Serializable
data class ProductCard(
    val name: String,
    val brand: String? = null,
    val source: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val portions: List<String> = emptyList(),
    val incomplete: Boolean = false,
) : ChatCard

/** Card <-> (type, JSON) for [com.lifehub.app.data.model.ChatMessage.cardType]/[com.lifehub.app.data.model.ChatMessage.cardJson]. */
object CardCodec {
    const val DAILY_SUMMARY = "daily_summary"
    const val MEAL_LOGGED = "meal_logged"
    const val PENDING_INPUT = "pending_input"
    const val PRODUCT = "product"
    const val WEIGHT_LOGGED = "weight_logged"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(card: ChatCard): Pair<String, String> = when (card) {
        is DailySummaryCard -> DAILY_SUMMARY to json.encodeToString(DailySummaryCard.serializer(), card)
        is MealLoggedCard -> MEAL_LOGGED to json.encodeToString(MealLoggedCard.serializer(), card)
        is PendingInputCard -> PENDING_INPUT to json.encodeToString(PendingInputCard.serializer(), card)
        is ProductCard -> PRODUCT to json.encodeToString(ProductCard.serializer(), card)
        is WeightLoggedCard -> WEIGHT_LOGGED to json.encodeToString(WeightLoggedCard.serializer(), card)
    }

    /** Unknown types or unreadable payloads (e.g. written by a newer version) decode to null and are not drawn. */
    fun decode(type: String?, payload: String?): ChatCard? {
        if (type == null || payload == null) return null
        return runCatching {
            when (type) {
                DAILY_SUMMARY -> json.decodeFromString(DailySummaryCard.serializer(), payload)
                MEAL_LOGGED -> json.decodeFromString(MealLoggedCard.serializer(), payload)
                PENDING_INPUT -> json.decodeFromString(PendingInputCard.serializer(), payload)
                PRODUCT -> json.decodeFromString(ProductCard.serializer(), payload)
                WEIGHT_LOGGED -> json.decodeFromString(WeightLoggedCard.serializer(), payload)
                else -> null
            }
        }.getOrNull()
    }
}
