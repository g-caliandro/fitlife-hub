package com.lifehub.app.copilot.food

import com.lifehub.app.data.model.BaseFood
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.FoodState
import com.lifehub.app.data.model.HouseholdMeasure
import com.lifehub.app.data.model.PortionKind
import com.lifehub.app.data.off.OffProduct
import com.lifehub.app.data.off.OffProductSearch
import com.lifehub.app.data.off.OffResult
import kotlinx.serialization.Serializable

/**
 * One food the copilot can log: values per 100 g, known portions and where it comes from. [ref] is the opaque id the
 * model passes back to `log_meal` (see [FoodRefs]). Serializable: stored as a snapshot in pending-request options, so a
 * request answered after an app restart still logs the exact values the user saw.
 */
@Serializable
data class FoodCandidate(
    val ref: String,
    val name: String,
    val brand: String? = null,
    val source: EntrySource,
    val state: FoodState? = null,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val portions: List<PortionInfo> = emptyList(),
    /** Open Food Facts product with missing macros (decision 11): missing values are 0 here, never shown as real. */
    val incomplete: Boolean = false,
    val foodId: String? = null,
    val baseFoodId: String? = null,
    val barcode: String? = null,
    val imageUrl: String? = null,
    val aliases: List<String> = emptyList(),
    val lastUsedAtMs: Long? = null,
    /** The user said cooked (or raw) but only the other state exists: values are of [state], the card says so. */
    val stateMismatch: Boolean = false,
) {
    val displayName: String get() = if (brand.isNullOrBlank()) name else "$name ($brand)"
}

/** `food:<id>` (user food), `base:<id>` (base table), `off:<barcode>` (Open Food Facts), `estimate:<pendingId>:<option>`. */
object FoodRefs {
    fun food(id: String) = "food:$id"
    fun base(id: String) = "base:$id"
    fun off(code: String) = "off:$code"
    fun estimate(pendingId: String, option: Int) = "estimate:$pendingId:$option"
    val PATTERN = Regex("^(food|base|off|estimate):[A-Za-z0-9._:-]{1,120}$")

    fun kind(ref: String): String = ref.substringBefore(':')
    fun value(ref: String): String = ref.substringAfter(':')
}

/** Read access to everything the resolver looks at (Room-backed in the app, see [RoomFoodCatalog]). */
interface FoodCatalog {
    /** Step 1 foods: logged at least once, favourites, the user's own and accepted estimates. */
    suspend fun historyFoods(): List<Food>
    suspend fun food(id: String): Food?
    suspend fun foodByBarcode(code: String): Food?
    suspend fun foodByBaseId(baseId: String): Food?
    suspend fun portions(foodId: String): List<FoodPortion>
    suspend fun baseFoods(): List<BaseFood>
    suspend fun baseFood(id: String): BaseFood?
    suspend fun measures(): List<HouseholdMeasure>
}

/** Candidate builders shared by the resolver and the ref lookup. */
object Candidates {
    /** Stored portions + the legacy `servingSizeG` as a "serving" (default only if no stored portion is the default). */
    fun portionsOf(food: Food, stored: List<FoodPortion>): List<PortionInfo> {
        val list = stored.map { PortionInfo(it.label, it.grams, it.kind, it.isDefault) }.toMutableList()
        val serving = food.servingSizeG?.takeIf { it > 0 }
        if (serving != null && list.none { it.kind == PortionKind.SERVING }) list += PortionInfo("serving", serving, PortionKind.SERVING, isDefault = list.none { it.isDefault })
        return list
    }

    fun fromFood(food: Food, stored: List<FoodPortion>): FoodCandidate = FoodCandidate(
        ref = FoodRefs.food(food.id), name = food.name, brand = food.brand, source = EntrySource.HISTORY, state = food.state,
        kcal100 = food.kcal100, protein100 = food.protein100, carbs100 = food.carbs100, fat100 = food.fat100,
        portions = portionsOf(food, stored), incomplete = food.incomplete, foodId = food.id, baseFoodId = food.baseFoodId,
        barcode = food.barcode, imageUrl = food.imageUrl, aliases = TextMatch.splitAliases(food.aliases),
        lastUsedAtMs = food.lastUsedAt?.toEpochMilli(),
    )

    fun fromBase(b: BaseFood): FoodCandidate = FoodCandidate(
        ref = FoodRefs.base(b.id), name = b.name, source = EntrySource.BASE_TABLE, state = b.state,
        kcal100 = b.kcal100, protein100 = b.protein100, carbs100 = b.carbs100, fat100 = b.fat100,
        baseFoodId = b.id, aliases = TextMatch.splitAliases(b.aliases),
    )

    /** Package weight -> "package" portion; label serving -> "serving" (the default when present, else the package). */
    fun fromOff(p: OffProduct): FoodCandidate {
        val portions = buildList {
            p.servingG?.let { add(PortionInfo("serving", it, PortionKind.SERVING, isDefault = true)) }
            p.packageG?.let { add(PortionInfo("package", it, PortionKind.PACKAGE, isDefault = p.servingG == null)) }
        }
        val kcal = p.kcal100 ?: (4 * (p.protein100 ?: 0.0) + 4 * (p.carbs100 ?: 0.0) + 9 * (p.fat100 ?: 0.0))
        return FoodCandidate(
            ref = FoodRefs.off(p.code), name = p.name, brand = p.brand, source = EntrySource.OPEN_FOOD_FACTS,
            kcal100 = kcal, protein100 = p.protein100 ?: 0.0, carbs100 = p.carbs100 ?: 0.0, fat100 = p.fat100 ?: 0.0,
            portions = portions, incomplete = p.incomplete, barcode = p.code, imageUrl = p.imageUrl,
        )
    }

    /**
     * A barcode product (cached or not yet): `off:<barcode>` for Open Food Facts products (the ref `log_meal` resolves from
     * the cache or [OffCandidateCache]), `food:<id>` for the user's own foods that carry a barcode.
     */
    fun fromScanned(p: com.lifehub.app.data.repo.ScannedProduct): FoodCandidate {
        val f = p.food
        val c = fromFood(f, p.portions)
        return if (f.source == com.lifehub.app.data.model.FoodSource.OFF && f.barcode != null) c.copy(ref = FoodRefs.off(f.barcode), source = EntrySource.OPEN_FOOD_FACTS) else c
    }

    /** Household measures that apply to [c]: its own, its base food's, then the generic ones. */
    fun measuresFor(c: FoodCandidate, all: List<HouseholdMeasure>): List<MeasureInfo> = all.mapNotNull { m ->
        when {
            m.foodId != null -> if (m.foodId == c.foodId) MeasureInfo(m.label, m.grams, true) else null
            m.baseFoodId != null -> if (m.baseFoodId == c.baseFoodId) MeasureInfo(m.label, m.grams, true) else null
            else -> MeasureInfo(m.label, m.grams, false)
        }
    }
}

/** Open Food Facts candidates seen in this process, so `log_meal` can use what `resolve_food` returned without a new call. */
class OffCandidateCache(private val max: Int = 200) {
    private val map = object : LinkedHashMap<String, FoodCandidate>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FoodCandidate>?) = size > max
    }
    @Synchronized fun put(c: FoodCandidate) { c.barcode?.let { map[it] = c } }
    @Synchronized fun get(code: String): FoodCandidate? = map[code]
}

enum class ResolveStatus { RESOLVED, AMBIGUOUS, NOT_FOUND }

/** [food] is set only when RESOLVED. [candidates] = plausible alternatives (AMBIGUOUS), best first. */
data class Resolution(
    val status: ResolveStatus,
    val food: FoodCandidate? = null,
    val candidates: List<FoodCandidate> = emptyList(),
    val offUnavailable: Boolean = false,
)

/**
 * Spoken food name -> food (design doc §6), four steps in order, stopping at the first reliable result:
 * 1. history (logged foods, favourites, own foods, accepted estimates): a STRONG match only; among strong matches the
 *    most recently used wins (decision 10). Weak matches never win here, they only become choices for the user;
 * 2. base table: the best strong match in the requested state (raw by default); several equally good matches are
 *    ambiguous; if only the other state exists it is used and flagged ([FoodCandidate.stateMismatch]);
 * 3. Open Food Facts text search: resolved only when exactly one product is a strong match AND has all macros
 *    (incomplete products, decision 11, are only offered as choices);
 * 4. nothing reliable: AMBIGUOUS with the plausible candidates, or NOT_FOUND. The model's own estimate is NOT a step of
 *    this class: it can only be offered in a pending request and is never logged unless the user picks it.
 */
class FoodResolver(
    private val catalog: FoodCatalog,
    private val off: OffProductSearch?,
    private val offCache: OffCandidateCache,
    private val maxCandidates: Int = 5,
) {
    suspend fun resolve(name: String, state: FoodState? = null): Resolution {
        val wanted = state ?: if (TextMatch.saysCooked(name)) FoodState.COOKED else FoodState.RAW
        fun fits(s: FoodState?) = s == null || s == wanted
        val plausible = mutableListOf<FoodCandidate>()

        // 1. History.
        val history = catalog.historyFoods().map { it to TextMatch.score(name, it.name, it.brand, TextMatch.splitAliases(it.aliases)) }
        history.filter { (f, m) -> m.strong && fits(f.state) }
            .maxWithOrNull(compareBy<Pair<Food, TextMatch.Match>>({ it.first.lastUsedAt != null }, { it.first.lastUsedAt }, { it.first.favorite }))
            ?.let { (f, _) -> return Resolution(ResolveStatus.RESOLVED, Candidates.fromFood(f, catalog.portions(f.id))) }
        history.filter { it.second.plausible }
            .sortedWith(compareByDescending<Pair<Food, TextMatch.Match>> { it.second }.thenByDescending { it.first.lastUsedAt })
            .forEach { (f, _) -> plausible += Candidates.fromFood(f, catalog.portions(f.id)) }

        // 2. Base table.
        val base = catalog.baseFoods().map { it to TextMatch.score(name, it.name, null, TextMatch.splitAliases(it.aliases)) }
        val strong = base.filter { it.second.strong }
        if (strong.isNotEmpty()) {
            val inState = strong.filter { fits(it.first.state) }
            val pool = inState.ifEmpty { strong }
            val best = pool.maxOf { it.second }
            val top = pool.filter { it.second.compareTo(best) == 0 }
            if (top.size == 1) {
                val c = Candidates.fromBase(top.single().first)
                return Resolution(ResolveStatus.RESOLVED, if (inState.isEmpty()) c.copy(stateMismatch = true) else c)
            }
            plausible += top.map { Candidates.fromBase(it.first) }
        }
        base.filter { it.second.plausible && it !in strong }.sortedByDescending { it.second }
            .forEach { plausible += Candidates.fromBase(it.first) }

        // 3. Open Food Facts.
        var offUnavailable = false
        if (off != null && TextMatch.tokens(name).isNotEmpty()) {
            when (val r = off.search(name)) {
                is OffResult.Error -> offUnavailable = true
                is OffResult.Ok -> {
                    val scored = r.value.map { Candidates.fromOff(it) }.onEach(offCache::put)
                        .map { it to TextMatch.score(name, it.name, it.brand) }
                    val reliable = scored.filter { it.second.strong && !it.first.incomplete }
                    if (reliable.size == 1) return Resolution(ResolveStatus.RESOLVED, reliable.single().first)
                    scored.filter { it.second.plausible }.sortedByDescending { it.second }.forEach { plausible += it.first }
                }
            }
        }

        // 4. Nothing reliable.
        val choices = plausible.distinctBy { it.ref }.take(maxCandidates)
        return if (choices.isEmpty()) Resolution(ResolveStatus.NOT_FOUND, offUnavailable = offUnavailable)
        else Resolution(ResolveStatus.AMBIGUOUS, candidates = choices, offUnavailable = offUnavailable)
    }
}
