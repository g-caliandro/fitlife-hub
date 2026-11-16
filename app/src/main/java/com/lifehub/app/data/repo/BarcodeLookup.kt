package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.data.model.PortionKind
import com.lifehub.app.data.off.OffApi
import com.lifehub.app.data.off.OffBarcodeSource
import com.lifehub.app.data.off.OffProduct
import com.lifehub.app.data.off.OffResult
import java.util.UUID

/** Result of the scanner screen's lookup: the product is a cached [Food] (what the food detail screen opens). */
sealed interface FoodLookup {
    data class Found(val food: Food) : FoodLookup
    data object NotFound : FoodLookup
    data class Failed(val message: String) : FoodLookup
}

/**
 * A barcode product with what the copilot needs: the [food] row (already in the local cache when [cached], else not yet
 * stored: its id is fresh) and its known [portions] (package weight, serving; empty when OFF has none).
 */
data class ScannedProduct(val food: Food, val portions: List<FoodPortion>, val cached: Boolean)

sealed interface ProductLookup {
    data class Found(val product: ScannedProduct) : ProductLookup
    data object NotFound : ProductLookup
    data class Failed(val message: String) : ProductLookup
}

/**
 * Barcode lookup, extracted from the scanner screen so the app and the copilot share it:
 * - [findFood]: exactly what the scanner always did (local cache, else Open Food Facts, cached with [FoodRepository.saveFood]);
 * - [findProduct]: the copilot's lookup, local cache first, else Open Food Facts with package weight and serving; products
 *   with missing macros come back flagged incomplete (decision 11) instead of "not found". Writes nothing;
 * - [cache]: stores a product found by [findProduct] (food + portions, one transaction) so it can be logged later.
 */
class BarcodeLookup(
    private val food: FoodRepository,
    private val off: OffApi,
    private val products: OffBarcodeSource,
    private val db: LifeHubDatabase,
    private val time: TimeSource,
) {
    suspend fun findFood(code: String): FoodLookup {
        food.getByBarcode(code)?.let { return FoodLookup.Found(it) }
        return when (val r = off.byBarcode(code)) {
            is OffResult.Ok -> r.value?.let { FoodLookup.Found(food.saveFood(it)) } ?: FoodLookup.NotFound
            is OffResult.Error -> FoodLookup.Failed(r.message)
        }
    }

    suspend fun findProduct(code: String): ProductLookup {
        val barcode = code.trim()
        food.getByBarcode(barcode)?.let { return ProductLookup.Found(ScannedProduct(it, db.portionDao().portions(it.id), cached = true)) }
        return when (val r = products.product(barcode)) {
            is OffResult.Error -> ProductLookup.Failed(r.message)
            is OffResult.Ok -> r.value?.let { ProductLookup.Found(toScanned(it)) } ?: ProductLookup.NotFound
        }
    }

    /** Stores [p] if it is not cached yet; returns the stored food (the existing one when the barcode is already cached). */
    suspend fun cache(p: ScannedProduct): Food {
        if (p.cached) return p.food
        return db.withTransaction {
            val existing = p.food.barcode?.let { food.getByBarcode(it) }
            if (existing != null) existing
            else {
                food.saveFood(p.food)
                p.portions.forEach { db.portionDao().upsertPortion(it) }
                p.food
            }
        }
    }

    private fun toScanned(p: OffProduct): ScannedProduct {
        val id = UUID.randomUUID().toString()
        val now = time.now()
        val kcal = p.kcal100 ?: (4 * (p.protein100 ?: 0.0) + 4 * (p.carbs100 ?: 0.0) + 9 * (p.fat100 ?: 0.0))
        val f = Food(
            id = id, source = FoodSource.OFF, barcode = p.code, name = p.name, brand = p.brand,
            kcal100 = kcal, protein100 = p.protein100 ?: 0.0, carbs100 = p.carbs100 ?: 0.0, fat100 = p.fat100 ?: 0.0,
            servingSizeG = null, favorite = false, lastUsedAt = null, imageUrl = p.imageUrl, incomplete = p.incomplete,
        )
        // Same rule as the resolver's OFF candidates: the label serving is the default portion, else the package.
        val portions = buildList {
            p.servingG?.let { add(FoodPortion(UUID.randomUUID().toString(), id, "serving", it, true, PortionKind.SERVING, now)) }
            p.packageG?.let { add(FoodPortion(UUID.randomUUID().toString(), id, "package", it, p.servingG == null, PortionKind.PACKAGE, now)) }
        }
        return ScannedProduct(f, portions, cached = false)
    }
}
