package com.lifehub.app.data.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * An Open Food Facts product as the copilot needs it. Macros are nullable: a missing value is NOT zero
 * (owner decision 11, [incomplete]). [packageG] = `product_quantity` when its unit is grams; [servingG] =
 * `serving_quantity` when its unit is grams (both absent when unknown: the copilot then asks the package weight once).
 */
data class OffProduct(
    val code: String,
    val name: String,
    val brand: String?,
    val kcal100: Double?,
    val protein100: Double?,
    val carbs100: Double?,
    val fat100: Double?,
    val packageG: Double?,
    val servingG: Double?,
    val imageUrl: String? = null,
) {
    val incomplete: Boolean get() = kcal100 == null || protein100 == null || carbs100 == null || fat100 == null
}

/** Full-text product search used by the copilot's FoodResolver (step 3). */
fun interface OffProductSearch {
    suspend fun search(query: String): OffResult<List<OffProduct>>
}

/** Pure parsing of Search-a-licious hits (unit-tested with fixtures). */
object OffSearchMapper {
    const val FIELDS = "code,product_name,product_name_it,brands,product_quantity,product_quantity_unit,serving_quantity,serving_quantity_unit,nutriments,image_front_small_url"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** Grams only: a quantity in ml (or any other unit) is not treated as grams without a density. */
    private fun grams(value: Double?, unit: String?): Double? =
        value?.takeIf { it > 0 && (unit == null || unit.trim().lowercase() in setOf("g", "gr", "grams", "gram")) }

    fun toProduct(p: JsonObject): OffProduct? {
        val code = p.str("code") ?: return null
        val name = p.str("product_name_it") ?: p.str("product_name") ?: return null
        val brand = when (val b = p["brands"]) {
            is JsonArray -> (b.firstOrNull() as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> b.contentOrNull?.split(',')?.firstOrNull()
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
        val n = p["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy-kj_100g")?.let { it / 4.184 }
        return OffProduct(
            code = code, name = name, brand = brand,
            kcal100 = kcal, protein100 = n.num("proteins_100g"), carbs100 = n.num("carbohydrates_100g"), fat100 = n.num("fat_100g"),
            packageG = grams(p.num("product_quantity"), p.str("product_quantity_unit")),
            servingG = grams(p.num("serving_quantity"), p.str("serving_quantity_unit")),
            imageUrl = p.str("image_front_small_url")?.takeIf { it.startsWith("http") },
        )
    }

    fun parseHits(body: String): List<OffProduct> {
        val hits = json.parseToJsonElement(body).jsonObject["hits"] as? JsonArray ?: return emptyList()
        return hits.mapNotNull { (it as? JsonObject)?.let(::toProduct) }
    }
}

/**
 * Search-a-licious (https://search.openfoodfacts.org/search, verified 2026-11-16: the v2/v3 product API has no full-text
 * search). One request per resolve_food call, never per keystroke. The documented OFF limit for searches is 10
 * requests/min per IP: this client refuses locally beyond [maxPerMinute] instead of risking a ban.
 */
class SearchaliciousClient(
    private val base: String = "https://search.openfoodfacts.org",
    private val maxPerMinute: Int = 10,
    private val clock: () -> Long = System::currentTimeMillis,
) : OffProductSearch {
    private val recent = ArrayDeque<Long>()

    /** Takes one slot of the per-minute budget; false = over the limit (no request is made). */
    @Synchronized internal fun acquire(): Boolean {
        val now = clock()
        while (recent.isNotEmpty() && now - recent.first() >= 60_000) recent.removeFirst()
        if (recent.size >= maxPerMinute) return false
        recent.addLast(now)
        return true
    }

    override suspend fun search(query: String): OffResult<List<OffProduct>> {
        if (!acquire()) return OffResult.Error("Too many Open Food Facts searches in the last minute.")
        return withContext(Dispatchers.IO) {
            try {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                val c = URL("$base/search?q=$q&langs=it,en&page_size=10&fields=${OffSearchMapper.FIELDS}").openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 8_000; c.readTimeout = 10_000
                    c.setRequestProperty("User-Agent", "LifeHub/0.4 (Android; personal app)")
                    c.setRequestProperty("Accept", "application/json")
                    if (c.responseCode !in 200..299) return@withContext OffResult.Error("Open Food Facts search failed (HTTP ${c.responseCode}).")
                    OffResult.Ok(OffSearchMapper.parseHits(c.inputStream.bufferedReader().use { it.readText() }))
                } finally { c.disconnect() }
            } catch (e: Exception) {
                OffResult.Error("Couldn't reach Open Food Facts.")
            }
        }
    }
}
