package com.lifehub.app.data.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Barcode -> Open Food Facts product with package weight and serving (copilot, phase 5). Separate from [OffApi.byBarcode]
 * (which the scanner flow keeps using unchanged): this one returns products with missing macros as incomplete
 * (owner decision 11) and reads `product_quantity` / `serving_quantity`. Ok(null) = the barcode is unknown to OFF.
 */
fun interface OffBarcodeSource {
    suspend fun product(code: String): OffResult<OffProduct?>
}

/** Pure parsing of the v3 product response (unit-tested with fixtures). */
object OffBarcodeMapper {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** `{"code":"…","product":{…},"status":"success"}`; null when there is no usable product object. */
    fun parse(body: String, barcode: String): OffProduct? {
        val root = json.parseToJsonElement(body).jsonObject
        val product = root["product"] as? JsonObject ?: return null
        val withCode = if (product["code"] == null) JsonObject(product + ("code" to kotlinx.serialization.json.JsonPrimitive(barcode))) else product
        return OffSearchMapper.toProduct(withCode)
    }
}

/**
 * `GET {base}/api/v3/product/{barcode}.json?fields=…` (verified 2026-11-16 against world.openfoodfacts.org: the response
 * carries `product` with `product_quantity`, `serving_quantity` and `nutriments`). One request per scan; OFF allows 15
 * product reads per minute per IP and a person scanning never gets near that.
 */
class OffBarcodeClient(private val base: String = "https://world.openfoodfacts.org") : OffBarcodeSource {
    override suspend fun product(code: String): OffResult<OffProduct?> = withContext(Dispatchers.IO) {
        try {
            val c = code.trim()
            val conn = URL("$base/api/v3/product/${URLEncoder.encode(c, "UTF-8")}.json?fields=${OffSearchMapper.FIELDS}").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000; conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "LifeHub/0.4 (Android; personal app)")
                conn.setRequestProperty("Accept", "application/json")
                when (conn.responseCode) {
                    404 -> OffResult.Ok(null)
                    in 200..299 -> OffResult.Ok(OffBarcodeMapper.parse(conn.inputStream.bufferedReader().use { it.readText() }, c))
                    else -> OffResult.Error("Open Food Facts answered HTTP ${conn.responseCode}.")
                }
            } finally { conn.disconnect() }
        } catch (e: Exception) { OffResult.Error("Couldn't reach Open Food Facts. Check your connection.") }
    }
}
