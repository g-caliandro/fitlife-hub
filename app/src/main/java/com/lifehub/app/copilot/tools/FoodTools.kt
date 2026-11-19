package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.agent.CopilotTool
import com.lifehub.app.copilot.agent.MealItemView
import com.lifehub.app.copilot.agent.MealLoggedCard
import com.lifehub.app.copilot.agent.Param
import com.lifehub.app.copilot.agent.ParamType
import com.lifehub.app.copilot.agent.PendingInputCard
import com.lifehub.app.copilot.agent.PendingOptionView
import com.lifehub.app.copilot.agent.ProductCard
import com.lifehub.app.copilot.agent.ToolArgs
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolKind
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.agent.ToolSchema
import com.lifehub.app.copilot.capture.CaptureStateMachine
import com.lifehub.app.copilot.capture.CaptureStore
import com.lifehub.app.copilot.capture.FoodLogWriter
import com.lifehub.app.copilot.capture.LogItem
import com.lifehub.app.copilot.capture.LogResult
import com.lifehub.app.copilot.capture.PendingCodec
import com.lifehub.app.copilot.capture.PendingInputStore
import com.lifehub.app.copilot.capture.PendingItem
import com.lifehub.app.copilot.capture.PendingOption
import com.lifehub.app.copilot.capture.Per100
import com.lifehub.app.copilot.food.Candidates
import com.lifehub.app.copilot.food.FoodCandidate
import com.lifehub.app.copilot.food.FoodCatalog
import com.lifehub.app.copilot.food.FoodRefs
import com.lifehub.app.copilot.food.FoodResolver
import com.lifehub.app.copilot.food.OffCandidateCache
import com.lifehub.app.copilot.food.PortionInfo
import com.lifehub.app.copilot.food.Quantity
import com.lifehub.app.copilot.food.QuantityNormalizer
import com.lifehub.app.copilot.food.QuantitySpec
import com.lifehub.app.copilot.food.ResolveStatus
import com.lifehub.app.copilot.food.TextMatch
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.data.model.FoodState
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.repo.BarcodeLookup
import com.lifehub.app.data.repo.ProductLookup
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.data.repo.FoodRepository
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.math.roundToInt

/** Phase 3 food tools: resolve_food (READ), log_meal (WRITE), request_user_input (INPUT_REQUEST). */
object FoodTools {
    /** A meal time up to this far after "now" is accepted (clock skew / rounding); later is a future date (decision 12). */
    const val FUTURE_SLACK_SECONDS = 10 * 60L

    fun all(
        resolver: FoodResolver, lookup: FoodRefLookup, catalog: FoodCatalog, writer: FoodLogWriter,
        captures: CaptureStore, pending: PendingInputStore, food: FoodRepository,
        barcodes: BarcodeLookup? = null, offCache: OffCandidateCache? = null, settings: () -> Settings,
    ): List<CopilotTool> = listOfNotNull(
        ResolveFoodTool(resolver, catalog),
        barcodes?.let { GetProductByBarcodeTool(it, catalog, offCache) },
        LogMealTool(lookup, catalog, writer, captures, pending, food, settings),
        RequestUserInputTool(lookup, captures, pending),
    )
}

/** Resolves a `ref` returned by resolve_food (or stored in a pending option) to the food values to log. */
class FoodRefLookup(private val catalog: FoodCatalog, private val offCache: OffCandidateCache, private val pending: PendingInputStore) {
    sealed interface Result {
        data class Found(val food: FoodCandidate) : Result
        /** An estimate the user has not chosen (or a request not answered with it): never written. */
        data object EstimateNotAccepted : Result
        data object Unknown : Result
    }

    suspend fun lookup(ref: String, captureId: String?): Result {
        if (!FoodRefs.PATTERN.matches(ref)) return Result.Unknown
        val v = FoodRefs.value(ref)
        val live: FoodCandidate? = when (FoodRefs.kind(ref)) {
            "food" -> catalog.food(v)?.let { Candidates.fromFood(it, catalog.portions(it.id)) }
            // A base food the user already logged once is their own food now (same id every time, keeps its portions).
            "base" -> catalog.foodByBaseId(v)?.let { Candidates.fromFood(it, catalog.portions(it.id)).copy(ref = ref) }
                ?: catalog.baseFood(v)?.let(Candidates::fromBase)
            "off" -> catalog.foodByBarcode(v)?.let { f ->
                // A cached Open Food Facts product keeps its provenance (a scanned product is not "history").
                Candidates.fromFood(f, catalog.portions(f.id)).copy(ref = ref, source = if (f.source == FoodSource.OFF) EntrySource.OPEN_FOOD_FACTS else EntrySource.HISTORY)
            } ?: offCache.get(v)
            "estimate" -> return estimate(ref)
            else -> null
        }
        if (live != null) return Result.Found(live)
        // Options of this capture's requests keep a snapshot (survives a restart that emptied the OFF cache).
        val requests = captureId?.let { pending.byCapture(it) }.orEmpty()
        val snapshot = requests.flatMap(PendingCodec::options).firstOrNull { it.ref == ref }?.food
            ?: requests.firstOrNull { it.foodRef == ref }?.let { PendingCodec.item(it)?.food }
        return snapshot?.let { Result.Found(it) } ?: Result.Unknown
    }

    private suspend fun estimate(ref: String): Result {
        val parts = FoodRefs.value(ref).split(':')
        val p = pending.get(parts.getOrNull(0) ?: return Result.Unknown) ?: return Result.Unknown
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return Result.Unknown
        val option = PendingCodec.options(p).getOrNull(index) ?: return Result.Unknown
        val e = option.estimate ?: return Result.Unknown
        if (p.status != PendingStatus.ANSWERED || PendingCodec.answer(p)?.option != index) return Result.EstimateNotAccepted
        val item = PendingCodec.item(p)
        return Result.Found(FoodCandidate(
            ref = ref, name = item?.name?.trim()?.replaceFirstChar { it.uppercase() } ?: option.label, source = EntrySource.LLM_ESTIMATE,
            state = item?.state, kcal100 = e.kcal, protein100 = e.protein, carbs100 = e.carbs, fat100 = e.fat,
            foodId = "est-${p.id}-$index", aliases = listOfNotNull(item?.name),
        ))
    }
}

private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
private fun JsonObjectBuilder.putOrNull(key: String, v: Number?) = put(key, v?.let(::JsonPrimitive) ?: JsonNull)
private fun JsonObjectBuilder.putOrNull(key: String, v: String?) = put(key, v?.let(::JsonPrimitive) ?: JsonNull)
private fun stateArg(s: String?): FoodState? = when (s) { "raw" -> FoodState.RAW; "cooked" -> FoodState.COOKED; else -> null }
internal fun portionText(p: PortionInfo) = "${p.label} ${fmt(p.grams)} g" + if (p.isDefault) " (default)" else ""
internal fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else round1(v).toString()

internal fun candidateJson(c: FoodCandidate, measures: List<String>) = buildJsonObject {
    put("ref", c.ref); put("name", c.name); putOrNull("brand", c.brand); put("source", c.source.wire)
    putOrNull("state", c.state?.name?.lowercase())
    putJsonObject("per_100g") {
        put("kcal", round1(c.kcal100)); put("protein_g", round1(c.protein100)); put("carbs_g", round1(c.carbs100)); put("fat_g", round1(c.fat100))
    }
    put("portions", JsonArray(c.portions.map { JsonPrimitive(portionText(it)) }))
    if (measures.isNotEmpty()) put("household_measures", JsonArray(measures.map(::JsonPrimitive)))
    if (c.incomplete) put("incomplete", true)
    if (c.stateMismatch) put("state_mismatch", true)
}

class ResolveFoodTool(private val resolver: FoodResolver, private val catalog: FoodCatalog) : CopilotTool {
    override val name = "resolve_food"
    override val description = "Finds a food the user named, in this order: their history and favourites, the base food " +
        "table (unpackaged foods, raw or cooked), Open Food Facts. Returns 'resolved' with one food, 'ambiguous' with " +
        "candidates, or 'not_found'. Call it once per food before log_meal; pass the name without quantities."
    override val kind = ToolKind.READ
    override val schema = ToolSchema(listOf(
        Param("name", ParamType.Str("Food name as said, brand included, e.g. 'riso basmati', 'merluzzo Findus gratinato'.", minLength = 1, maxLength = 120)),
        Param("state", ParamType.Str("'cooked' only if the user said the food was weighed cooked; default raw.", enum = listOf("raw", "cooked")), required = false),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val r = resolver.resolve(args.string("name")!!, stateArg(args.string("state")))
        val measures = catalog.measures()
        fun mJson(c: FoodCandidate) = Candidates.measuresFor(c, measures).filter { it.specific }.map { "${it.label} ${fmt(it.grams)} g" }
        val result = buildJsonObject {
            put("status", r.status.name.lowercase())
            when (r.status) {
                ResolveStatus.RESOLVED -> {
                    val f = r.food!!
                    put("food", candidateJson(f, mJson(f)))
                    put("note", buildString {
                        append("Log it with log_meal using this ref.")
                        if (f.incomplete) append(" Some macros are missing on Open Food Facts: the log will be marked incomplete.")
                        if (f.stateMismatch) append(" Only the ${f.state?.name?.lowercase()} values exist for this food: say so in your reply.")
                    })
                }
                ResolveStatus.AMBIGUOUS -> {
                    put("candidates", JsonArray(r.candidates.map { candidateJson(it, mJson(it)) }))
                    put("note", "Several possible foods and none is certain: do not pick one yourself. Log the other items, " +
                        "then call request_user_input with kind 'choose_food', the best candidates as options (their refs) and this item.")
                }
                ResolveStatus.NOT_FOUND -> put("note", "Not found in the history, the base table or Open Food Facts. Do NOT log it. " +
                    "Call request_user_input with kind 'use_estimate', one option with your own estimate per 100 g, and this item.")
            }
            if (r.offUnavailable) put("open_food_facts", "unavailable right now")
        }
        val card = r.food?.takeIf { it.source == EntrySource.OPEN_FOOD_FACTS }?.let {
            ProductCard(it.name, it.brand, it.source.wire, it.kcal100, it.protein100, it.carbs100, it.fat100, it.portions.map(::portionText), it.incomplete)
        }
        return ToolOutcome.Ok(result, card)
    }
}

/**
 * `get_product_by_barcode(barcode)` (READ): local cache first, else Open Food Facts, with package weight and serving as
 * portions. Writes nothing: the product is logged later with `log_meal` and the `ref` returned here. Missing macros are
 * flagged `incomplete` (owner decision 11), never reported as real zeros.
 */
class GetProductByBarcodeTool(
    private val barcodes: BarcodeLookup,
    private val catalog: FoodCatalog,
    private val offCache: OffCandidateCache?,
) : CopilotTool {
    override val name = "get_product_by_barcode"
    override val description = "Looks up a packaged product by its barcode (EAN/UPC digits): local cache first, then Open Food Facts. " +
        "Returns its values per 100 g, its portions (package weight, serving) and a ref for log_meal. Use it when the user " +
        "gives a barcode; a product the user scanned in the chat is already described in the conversation."
    override val kind = ToolKind.READ
    override val schema = ToolSchema(listOf(Param("barcode", ParamType.Str("The barcode digits, 8 to 14 digits.", minLength = 8, maxLength = 14))))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val code = args.string("barcode")!!.trim()
        if (!code.all { it.isDigit() }) return ToolOutcome.Error("'barcode' must contain only digits.")
        return when (val r = barcodes.findProduct(code)) {
            is ProductLookup.Failed -> ToolOutcome.Error("Open Food Facts is not reachable right now (${r.message}) and the product is not in the local cache. Tell the user and do not guess the product.")
            ProductLookup.NotFound -> ToolOutcome.Ok(buildJsonObject {
                put("status", "not_found")
                put("note", "No product with this barcode in the local cache or Open Food Facts. Tell the user; do NOT log or invent it.")
            })
            is ProductLookup.Found -> {
                val c = Candidates.fromScanned(r.product)
                offCache?.put(c)
                val measures = Candidates.measuresFor(c, catalog.measures()).filter { it.specific }.map { "${it.label} ${fmt(it.grams)} g" }
                ToolOutcome.Ok(
                    buildJsonObject {
                        put("status", "found"); put("cached", r.product.cached)
                        put("food", candidateJson(c, measures))
                        put("note", buildString {
                            append("Log it with log_meal using this ref.")
                            if (c.incomplete) append(" Some macros are missing on Open Food Facts: the log will be marked incomplete.")
                            if (c.portions.none { it.kind == com.lifehub.app.data.model.PortionKind.PACKAGE }) append(" The package weight is unknown: for 'half a pack' ask with request_user_input kind package_weight.")
                        })
                    },
                    ProductCard(c.name, c.brand, c.source.wire, c.kcal100, c.protein100, c.carbs100, c.fat100, c.portions.map(::portionText), c.incomplete),
                )
            }
        }
    }
}

/** Common item quantity params (log_meal items and request_user_input item). Exactly one way of saying the amount. */
private val quantityParams = listOf(
    Param("grams", ParamType.Num("Grams as said by the user (after any self-correction).", min = 0.1, max = 5000.0), required = false),
    Param("servings", ParamType.Num("Number of portions, e.g. 0.5 for 'mezza scatola'/'mezzo', 2 for 'due'. The app multiplies by the portion grams.", min = 0.01, max = 50.0), required = false),
    Param("portion", ParamType.Str("Portion the servings refer to, e.g. 'scatola' / 'package', 'porzione'. Omit for the default portion.", maxLength = 40), required = false),
    Param("measure", ParamType.Str("Household measure: cucchiaio, cucchiaino, bicchiere, tazza, fetta, pezzo, ... ('cucchiaio colmo' only if said).", maxLength = 40), required = false),
    Param("count", ParamType.Num("How many household measures (default 1).", min = 0.25, max = 50.0), required = false),
)

/** Parses the quantity fields of an item; returns the spec or a readable error. */
internal fun quantitySpec(a: ToolArgs, path: String): Pair<QuantitySpec?, String?> {
    val grams = a.double("grams"); val servings = a.double("servings"); val measure = a.string("measure")?.trim()?.takeIf { it.isNotEmpty() }
    val portion = a.string("portion")?.trim()?.takeIf { it.isNotEmpty() }; val count = a.double("count")
    val ways = listOfNotNull(grams, servings, measure).size
    return when {
        ways > 1 -> null to "'$path': give only one of grams, servings or measure."
        portion != null && servings == null -> null to "'$path': 'portion' needs 'servings'."
        count != null && measure == null -> null to "'$path': 'count' needs 'measure'."
        grams != null -> QuantitySpec.Grams(grams) to null
        servings != null -> QuantitySpec.Servings(servings, portion) to null
        measure != null -> QuantitySpec.Measure(measure, count ?: 1.0) to null
        else -> QuantitySpec.Unspecified to null
    }
}

class LogMealTool(
    private val lookup: FoodRefLookup,
    private val catalog: FoodCatalog,
    private val writer: FoodLogWriter,
    private val captures: CaptureStore,
    private val pending: PendingInputStore,
    private val food: FoodRepository,
    private val settings: () -> Settings,
) : CopilotTool {
    override val name = "log_meal"
    override val description = "Writes the WHOLE meal to the food diary in one call, only when everything the user said is clear " +
        "(the user can undo it). One item per food, with the ref from resolve_food and the amount: grams, or servings (+ portion), " +
        "or a household measure (+ count), or nothing for one default portion. The app computes grams, kcal and macros: never pass " +
        "them. All or nothing: if any item cannot be written, nothing is written and the problems come back in 'not_logged'."
    override val kind = ToolKind.WRITE
    override val heldWhileAsking = true
    override val schema = ToolSchema(listOf(
        Param("items", ParamType.Arr(ParamType.Obj(listOf(
            Param("ref", ParamType.Str("The ref returned by resolve_food.", minLength = 1, maxLength = 160)),
            Param("name", ParamType.Str("The food as the user said it.", maxLength = 120), required = false),
        ) + quantityParams), minItems = 1, maxItems = 20)),
        Param("datetime", ParamType.Str("Local date and time of the meal, YYYY-MM-DDTHH:MM. Omit when it is now. Never in the future.", maxLength = 25), required = false),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val captureId = ctx.captureId ?: return ToolOutcome.Error("log_meal is not available in this conversation.")
        val capture = captures.get(captureId) ?: return ToolOutcome.Error("log_meal is not available in this conversation.")
        if (capture.status == CaptureStatus.CANCELLED) return ToolOutcome.Error("The user undid this meal log. Do not log it again; the user can ask again if they want.")
        if (!CaptureStateMachine.acceptsWrites(capture.status)) return ToolOutcome.Error("This request cannot write right now.")
        if (pending.byCapture(captureId).any { it.status == PendingStatus.OPEN })
            return ToolOutcome.Error("Questions to the user are still unanswered: nothing is written until everything is clear. Do not call log_meal now.")

        val requested = args.string("datetime")?.let { s ->
            try { LocalDateTime.parse(s.trim().take(16)).atZone(ctx.zone).toInstant() } catch (_: DateTimeParseException) {
                return ToolOutcome.Error("'datetime' must be a local date and time like 2026-11-16T13:00.")
            }
        }
        if (requested != null && requested.isAfter(ctx.now.plusSeconds(FoodTools.FUTURE_SLACK_SECONDS)))
            return ToolOutcome.Error("The meal time ${args.string("datetime")} is in the future: future dates are not allowed. Ask the user when they ate.")
        // Items added after a pending answer join the meal of the first log of this capture (same date and time).
        val mealAt = capture.mealAt ?: requested ?: ctx.now
        val keptEarlierTime = capture.mealAt != null && requested != null && requested != capture.mealAt
        val date = mealAt.atZone(ctx.zone).toLocalDate()

        val raw = args.array("items")!!.map { ToolArgs(it as JsonObject) }
        val specs = raw.mapIndexed { i, a -> quantitySpec(a, "items[$i]") }
        specs.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.let { return ToolOutcome.Error(it.joinToString(" ")) }

        val measures = catalog.measures()
        val toWrite = mutableListOf<LogItem>()
        val notLogged = mutableListOf<JsonObject>()
        val seen = mutableMapOf<String, Int>()
        raw.forEachIndexed { i, a ->
            val ref = a.string("ref")!!.trim()
            val said = a.string("name")
            fun skip(reason: String, message: String, ask: String?) = notLogged.add(buildJsonObject {
                put("ref", ref); putOrNull("name", said); put("reason", reason); put("message", message); putOrNull("ask_with", ask)
            })
            val c = when (val r = lookup.lookup(ref, captureId)) {
                is FoodRefLookup.Result.Found -> r.food
                FoodRefLookup.Result.EstimateNotAccepted -> { skip("estimate_not_chosen", "The user has not chosen this estimate: it can't be logged.", null); return@forEachIndexed }
                FoodRefLookup.Result.Unknown -> { skip("unknown_ref", "Unknown ref: call resolve_food and use the ref it returns.", null); return@forEachIndexed }
            }
            when (val q = QuantityNormalizer.normalize(specs[i].first!!, c.portions, Candidates.measuresFor(c, measures))) {
                is Quantity.Missing -> skip(q.reason.name.lowercase(), q.message, if (q.reason == Quantity.Reason.NO_PACKAGE_WEIGHT) "package_weight" else "quantity")
                is Quantity.Resolved -> {
                    val identity = c.barcode?.let { "bc:$it" } ?: c.baseFoodId?.let { "base:$it" } ?: c.foodId?.let { "food:$it" } ?: c.ref
                    val base = "$identity|${q.grams}"
                    val n = seen.merge(base, 1, Int::plus)!!
                    toWrite += LogItem(c, q, "$base#$n", said)
                }
            }
        }

        // All or nothing: one item that cannot be written (unknown, missing quantity, estimate not chosen) holds the whole meal.
        val results = if (toWrite.isEmpty() || notLogged.isNotEmpty()) emptyList() else writer.log(captureId, date, mealAt, toWrite, ctx.now)
        val logged = results.filterIsInstance<LogResult.Logged>()
        val day = food.observeNutrition(date, date).first().firstOrNull()
        val summary = dailySummary(date, day, settings())
        val time = mealAt.atZone(ctx.zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val result = buildJsonObject {
            put("logged", JsonArray(logged.map { l ->
                val e = l.entry
                buildJsonObject {
                    put("name", e.name); putOrNull("brand", e.brand); put("grams", round1(e.grams)); put("kcal", e.kcal.roundToInt())
                    put("protein_g", round1(e.proteinG)); put("carbs_g", round1(e.carbsG)); put("fat_g", round1(e.fatG))
                    put("source", l.item.food.source.wire)
                    putOrNull("portion", portionUsed(l.item.quantity))
                    if (l.item.quantity.usedDefaultPortion) put("default_portion_used", true)
                    if (l.item.food.incomplete) put("incomplete", true)
                }
            }))
            val again = results.count { it is LogResult.AlreadyLogged }
            if (again > 0) put("already_logged", again)
            if (notLogged.isNotEmpty()) put("not_logged", JsonArray(notLogged))
            putJsonObject("meal") { put("date", date.toString()); put("time", time) }
            putJsonObject("day_remaining") {
                putOrNull("kcal", summary.kcalRemaining); putOrNull("protein_g", summary.proteinRemainingG)
                putOrNull("carbs_g", summary.carbsRemainingG); putOrNull("fat_g", summary.fatRemainingG)
            }
            put("note", buildString {
                if (notLogged.isNotEmpty()) append("NOTHING was written (all or nothing): the meal is held until every item is clear.")
                else append("Already in the diary; the card shows the details. Reply in one short sentence.")
                if (again > 0) append(" 'already_logged' items were written earlier for this same request: do not log them again.")
                if (keptEarlierTime) append(" The meal keeps the date and time of the first log of this request.")
                if (notLogged.isNotEmpty()) append(" For each not_logged item with 'ask_with', call request_user_input with that kind, all in the same round, without log_meal; fix the others (unknown ref: resolve_food again) and log the whole meal once.")
            })
        }
        val card = if (logged.isEmpty()) null else MealLoggedCard(
            captureId = captureId, date = date.toString(), time = time,
            items = logged.map { l ->
                val e = l.entry
                MealItemView(e.id, e.name, e.brand, e.grams, e.kcal, e.proteinG, e.carbsG, e.fatG, l.item.food.source.wire,
                    e.portionLabel, e.servings, e.portionGrams, l.item.quantity.usedDefaultPortion, l.item.food.incomplete, l.item.food.stateMismatch, e.imageUrl)
            },
            totalKcal = logged.sumOf { it.entry.kcal }, totalProteinG = logged.sumOf { it.entry.proteinG },
            totalCarbsG = logged.sumOf { it.entry.carbsG }, totalFatG = logged.sumOf { it.entry.fatG },
            kcalRemaining = summary.kcalRemaining, proteinRemainingG = summary.proteinRemainingG,
            carbsRemainingG = summary.carbsRemainingG, fatRemainingG = summary.fatRemainingG, day = summary,
        )
        return ToolOutcome.Ok(result, card)
    }

    private fun portionUsed(q: Quantity.Resolved): String? = when {
        q.portion != null -> "${fmt(q.servings ?: 1.0)} x ${q.portion.label} (${fmt(q.portion.grams)} g)"
        q.measure != null -> "${fmt(q.count ?: 1.0)} x ${q.measure.label} (${fmt(q.measure.grams)} g)"
        else -> null
    }
}

class RequestUserInputTool(
    private val lookup: FoodRefLookup,
    private val captures: CaptureStore,
    private val pending: PendingInputStore,
) : CopilotTool {
    override val name = "request_user_input"
    override val description = "Saves a question for the user and ends your turn; the user answers later (chat, inbox or " +
        "notification) and you continue then. Use it for: choose_food (several candidates), use_estimate (food not found: " +
        "offer your estimate per 100 g, it is shown as an estimate), quantity (amount missing), package_weight (package " +
        "weight unknown; pass food_ref). Ask every open question of the meal in the same round and do NOT call log_meal: nothing is written until all are answered."
    override val kind = ToolKind.INPUT_REQUEST
    override val schema = ToolSchema(listOf(
        Param("kind", ParamType.Str("Type of question.", enum = listOf("choose_food", "use_estimate", "quantity", "package_weight"))),
        Param("prompt", ParamType.Str("Short question in Italian, e.g. 'Quale merluzzo gratinato?'.", minLength = 1, maxLength = 300)),
        Param("options", ParamType.Arr(ParamType.Obj(listOf(
            Param("label", ParamType.Str("Short label shown on the button.", minLength = 1, maxLength = 80)),
            Param("ref", ParamType.Str("A ref from resolve_food.", maxLength = 160), required = false),
            Param("estimate", ParamType.Obj(listOf(
                Param("kcal_100g", ParamType.Num(min = 0.0, max = 900.0)),
                Param("protein_100g", ParamType.Num(min = 0.0, max = 100.0)),
                Param("carbs_100g", ParamType.Num(min = 0.0, max = 100.0)),
                Param("fat_100g", ParamType.Num(min = 0.0, max = 100.0)),
            ), "Your estimate per 100 g (use_estimate only)."), required = false),
        )), maxItems = 4), required = false),
        Param("item", ParamType.Obj(listOf(
            Param("name", ParamType.Str("The food as the user said it.", minLength = 1, maxLength = 120)),
            Param("state", ParamType.Str(enum = listOf("raw", "cooked")), required = false),
        ) + quantityParams, "The meal item to log once answered."), required = false),
        Param("food_ref", ParamType.Str("package_weight: the ref of the product.", maxLength = 160), required = false),
    ))

    override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
        val captureId = ctx.captureId ?: return ToolOutcome.Error("request_user_input is not available in this conversation.")
        val capture = captures.get(captureId) ?: return ToolOutcome.Error("request_user_input is not available in this conversation.")
        if (capture.status == CaptureStatus.CANCELLED) return ToolOutcome.Error("The user undid this request. Do not ask anything about it.")
        val kind = PendingKind.valueOf(args.string("kind")!!.uppercase())
        val prompt = args.string("prompt")!!.trim()
        val itemArgs = args.obj("item")
        itemArgs?.let { quantitySpec(it, "item").second?.let { e -> return ToolOutcome.Error(e) } }
        val item = itemArgs?.let {
            PendingItem(it.string("name")!!.trim(), it.double("grams"), it.double("servings"), it.string("portion"), it.string("measure"), it.double("count"), stateArg(it.string("state")))
        }
        val foodRef = args.string("food_ref")?.trim()?.takeIf { it.isNotEmpty() }
        val rawOptions = args.array("options").orEmpty().map { ToolArgs(it as JsonObject) }

        val errors = mutableListOf<String>()
        if (kind != PendingKind.PACKAGE_WEIGHT && item == null) errors += "'item' is required for ${kind.name.lowercase()}."
        if (kind == PendingKind.PACKAGE_WEIGHT && foodRef == null) errors += "'food_ref' is required for package_weight."
        if (kind == PendingKind.CHOOSE_FOOD && rawOptions.isEmpty()) errors += "choose_food needs at least one option."
        if (kind == PendingKind.USE_ESTIMATE && rawOptions.none { it.obj("estimate") != null }) errors += "use_estimate needs an option with 'estimate'."
        val options = rawOptions.mapIndexedNotNull { i, o ->
            val ref = o.string("ref")?.trim()?.takeIf { it.isNotEmpty() }
            val est = o.obj("estimate")
            when {
                (ref == null) == (est == null) -> { errors += "'options[$i]' needs exactly one of 'ref' or 'estimate'."; null }
                est != null -> {
                    if (kind != PendingKind.USE_ESTIMATE && kind != PendingKind.CHOOSE_FOOD) { errors += "'options[$i]': estimates only in use_estimate or choose_food."; return@mapIndexedNotNull null }
                    val p = Per100(est.double("kcal_100g")!!, est.double("protein_100g")!!, est.double("carbs_100g")!!, est.double("fat_100g")!!)
                    if (p.protein + p.carbs + p.fat > 105) { errors += "'options[$i].estimate': protein + carbs + fat exceed 100 g per 100 g."; return@mapIndexedNotNull null }
                    PendingOption(o.string("label")!!.trim(), estimate = p)
                }
                else -> when (val r = lookup.lookup(ref!!, captureId)) {
                    is FoodRefLookup.Result.Found -> PendingOption(o.string("label")!!.trim(), ref = ref, food = r.food)
                    else -> { errors += "'options[$i].ref' is unknown: use a ref returned by resolve_food."; null }
                }
            }
        }
        val foodSnapshot = foodRef?.let {
            when (val r = lookup.lookup(it, captureId)) {
                is FoodRefLookup.Result.Found -> r.food
                else -> { errors += "'food_ref' is unknown: use a ref returned by resolve_food."; null }
            }
        }
        if (errors.isNotEmpty()) return ToolOutcome.Error(errors.joinToString(" "))

        val fullItem = when {
            foodSnapshot == null -> item
            item == null -> PendingItem(foodSnapshot.displayName, food = foodSnapshot)
            else -> item.copy(food = foodSnapshot)
        }
        // Idempotent per capture: the same question for the same item is never stored twice.
        val key = TextMatch.tokens(fullItem?.name)
        pending.byCapture(captureId).firstOrNull { p ->
            p.kind == kind && p.foodRef == foodRef && TextMatch.tokens(PendingCodec.item(p)?.name) == key
        }?.let { p ->
            return when (p.status) {
                PendingStatus.OPEN -> ToolOutcome.Suspend(waiting(p.id), p.id)
                PendingStatus.ANSWERED -> ToolOutcome.Error("The user already answered this question (request ${p.id}); use that answer instead of asking again.")
                PendingStatus.DISCARDED -> ToolOutcome.Error("The user discarded this question: do not ask again and do not log this item.")
            }
        }

        val id = UUID.randomUUID().toString()
        // Estimate options get a ref only now (it needs the request id); log_meal accepts it only once the user chose it.
        val stored = options.mapIndexed { i, o -> if (o.isEstimate) o.copy(ref = FoodRefs.estimate(id, i)) else o }
        val p = pending.create(captureId, ctx.threadId, kind, prompt, stored, fullItem, foodRef, ctx.now, id)
        val card = com.lifehub.app.copilot.capture.PendingCards.from(p)
        return ToolOutcome.Suspend(waiting(p.id), p.id, card)
    }

    private fun waiting(id: String) = buildJsonObject {
        put("status", "waiting_for_user"); put("pending_input_id", id)
        put("note", "Saved. Your turn ends here: do not log this item now and do not repeat the question.")
    }
}
