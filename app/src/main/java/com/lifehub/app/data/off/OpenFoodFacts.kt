package com.lifehub.app.data.off

import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import com.lifehub.app.domain.autoKcal

/** Pure mapping of Open Food Facts JSON to [Food] (unit-tested with fixtures). */
object OffMapper {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    const val FIELDS = "code,product_name,brands,nutriments,serving_size,image_front_small_url,image_small_url,image_front_url,image_url"

    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** "30 g", "1 slice (30g)", "250ml" -> grams (ml treated as g). */
    fun parseServing(text: String?): Double? {
        if (text == null) return null
        val m = Regex("""(\d+(?:[.,]\d+)?)\s*(?:g|gr|ml)\b""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return m.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    }

    /** Smallest front photo first (list thumbnails); only http(s) URLs. */
    fun imageUrl(p: JsonObject): String? =
        listOf("image_front_small_url", "image_small_url", "image_front_url", "image_url").firstNotNullOfOrNull { k -> p.str(k)?.takeIf { it.startsWith("http") } }

    /** Maps one OFF product object. Null when it has no name or no energy/macro data. */
    fun toFood(p: JsonObject, newId: () -> String = { UUID.randomUUID().toString() }): Food? {
        val name = p.str("product_name") ?: return null
        val n = (p["nutriments"] as? JsonObject) ?: return null
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 } ?: n.num("energy-kj_100g")?.let { it / 4.184 }
        val protein = n.num("proteins_100g")
        val carbs = n.num("carbohydrates_100g")
        val fat = n.num("fat_100g")
        if (kcal == null && protein == null && carbs == null && fat == null) return null
        val pr = protein ?: 0.0; val ca = carbs ?: 0.0; val fa = fat ?: 0.0
        return Food(
            id = newId(), source = FoodSource.OFF, barcode = p.str("code"), name = name,
            brand = p.str("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            kcal100 = kcal ?: autoKcal(pr, ca, fa), protein100 = pr, carbs100 = ca, fat100 = fa,
            servingSizeG = parseServing(p.str("serving_size")), favorite = false, lastUsedAt = null,
            imageUrl = imageUrl(p),
        )
    }

    fun parseSearch(body: String, newId: () -> String = { UUID.randomUUID().toString() }): List<Food> {
        val arr = json.parseToJsonElement(body).jsonObject["products"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.let { o -> toFood(o, newId) } }
    }

    /** Barcode response: {"status":1,"product":{...}}; null if not found / incomplete. */
    fun parseProduct(body: String, barcode: String, newId: () -> String = { UUID.randomUUID().toString() }): Food? {
        val root = json.parseToJsonElement(body).jsonObject
        val status = (root["status"] as? JsonPrimitive)?.doubleOrNull
        val product = root["product"] as? JsonObject ?: return null
        if (status == 0.0) return null
        val withCode = if (product["code"] == null) JsonObject(product + ("code" to JsonPrimitive(barcode))) else product
        return toFood(withCode, newId)
    }
}

sealed interface OffResult<out T> {
    data class Ok<T>(val value: T) : OffResult<T>
    data class Error(val message: String) : OffResult<Nothing>
}

interface OffApi {
    suspend fun search(query: String): OffResult<List<Food>>
    /** Ok(null) = barcode unknown to Open Food Facts. */
    suspend fun byBarcode(code: String): OffResult<Food?>
}

class OpenFoodFactsClient(private val base: String = "https://world.openfoodfacts.org") : OffApi {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "LifeHub/0.3 (Android)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode == 404) return "{\"status\":0}"
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    override suspend fun search(query: String): OffResult<List<Food>> = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            OffResult.Ok(OffMapper.parseSearch(get("$base/cgi/search.pl?search_terms=$q&search_simple=1&json=1&page_size=25&fields=${OffMapper.FIELDS}")))
        } catch (e: Exception) { OffResult.Error("Couldn't reach Open Food Facts. Check your connection.") }
    }

    override suspend fun byBarcode(code: String): OffResult<Food?> = withContext(Dispatchers.IO) {
        try {
            val c = URLEncoder.encode(code.trim(), "UTF-8")
            OffResult.Ok(OffMapper.parseProduct(get("$base/api/v2/product/$c.json?fields=${OffMapper.FIELDS}"), code.trim()))
        } catch (e: Exception) { OffResult.Error("Couldn't reach Open Food Facts. Check your connection.") }
    }
}
