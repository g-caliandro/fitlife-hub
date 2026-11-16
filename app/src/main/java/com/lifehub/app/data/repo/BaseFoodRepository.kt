package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.BaseFood
import com.lifehub.app.data.model.FoodState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `assets/copilot/base_foods.json` (built by tools/copilot/build_base_foods.py from USDA SR Legacy, CC0). */
@Serializable
data class BaseFoodAsset(val version: Int, val source: String, val foods: List<BaseFoodDto>) {
    @Serializable
    data class BaseFoodDto(
        val id: String, val name: String, val aliases: List<String> = emptyList(), val state: FoodState? = null,
        val kcal100: Double, val protein100: Double, val carbs100: Double, val fat100: Double, val sourceRef: String? = null,
    )
}

/**
 * Read-only base food table (resolver step 2). The bundled asset is the truth: [ensureSeeded] copies it into
 * `base_foods` when the table differs from it (first start, or an app update with a new asset), in one transaction.
 * Reference data only: never exported, never changed by captures.
 */
class BaseFoodRepository(private val db: LifeHubDatabase, private val readAsset: () -> String) {
    private val dao = db.baseFoodDao()
    @Volatile private var seeded = false

    fun parse(text: String): List<BaseFood> =
        Json { ignoreUnknownKeys = true }.decodeFromString(BaseFoodAsset.serializer(), text).foods.map {
            BaseFood(it.id, it.name, it.aliases.joinToString("|").ifEmpty { null }, it.state, it.kcal100, it.protein100, it.carbs100, it.fat100, it.sourceRef)
        }.sortedBy { it.id }

    suspend fun ensureSeeded() {
        if (seeded) return
        val asset = parse(readAsset())
        db.withTransaction {
            if (dao.all() != asset) { dao.deleteAll(); dao.insertAll(asset) }
        }
        seeded = true
    }

    suspend fun all(): List<BaseFood> { ensureSeeded(); return dao.all() }
    suspend fun get(id: String): BaseFood? { ensureSeeded(); return dao.get(id) }
}
