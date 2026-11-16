package com.lifehub.app.data.repo

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.domain.DayNutrition
import com.lifehub.app.domain.macrosFor
import com.lifehub.app.domain.mergeNutrition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface FoodRepository {
    fun observeRecent(limit: Int = 50): Flow<List<Food>>
    fun observeMine(): Flow<List<Food>>
    fun observeFood(id: String): Flow<Food?>
    suspend fun getFood(id: String): Food?
    suspend fun getByBarcode(barcode: String): Food?
    /** Saves a custom food or caches an OFF product (de-duplicated by barcode). Returns the stored food. */
    suspend fun saveFood(food: Food): Food
    suspend fun deleteFood(id: String)
    suspend fun setFavorite(id: String, favorite: Boolean)

    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>>
    /** Per-date nutrition (food entries + quick add) for dates with any data. */
    fun observeNutrition(from: LocalDate, to: LocalDate): Flow<List<DayNutrition>>
    suspend fun getEntry(id: String): FoodEntry?
    /** Logs [grams] of [food] on [date] (caches the food + bumps lastUsedAt). */
    suspend fun addEntry(food: Food, date: LocalDate, grams: Double, now: Instant): FoodEntry
    suspend fun updateEntryGrams(id: String, grams: Double, food: Food?)
    suspend fun restoreEntry(entry: FoodEntry)
    suspend fun deleteEntry(id: String)
}

class FoodRepositoryImpl(private val db: LifeHubDatabase) : FoodRepository {
    private val foods = db.foodDao()
    private val entries = db.foodEntryDao()

    override fun observeRecent(limit: Int) = foods.observeRecent(limit)
    override fun observeMine() = foods.observeMine()
    override fun observeFood(id: String) = foods.observe(id)
    override suspend fun getFood(id: String) = foods.get(id)
    override suspend fun getByBarcode(barcode: String) = foods.getByBarcode(barcode)

    override suspend fun saveFood(food: Food): Food {
        val existing = food.barcode?.let { foods.getByBarcode(it) }
        val toSave = if (existing != null && food.source == com.lifehub.app.data.model.FoodSource.OFF && existing.id != food.id)
            food.copy(id = existing.id, favorite = existing.favorite, lastUsedAt = existing.lastUsedAt, imageUrl = food.imageUrl ?: existing.imageUrl) else food
        foods.upsert(toSave)
        return toSave
    }
    override suspend fun deleteFood(id: String) = foods.delete(id)
    override suspend fun setFavorite(id: String, favorite: Boolean) {
        foods.get(id)?.let { foods.upsert(it.copy(favorite = favorite)) }
    }

    override fun observeEntries(from: LocalDate, to: LocalDate) = entries.observeBetween(from, to)
    override fun observeNutrition(from: LocalDate, to: LocalDate): Flow<List<DayNutrition>> =
        combine(db.quickAddDao().observeBetween(from, to), entries.observeBetween(from, to)) { m, e -> mergeNutrition(m, e) }

    override suspend fun getEntry(id: String) = entries.get(id)

    override suspend fun addEntry(food: Food, date: LocalDate, grams: Double, now: Instant): FoodEntry {
        val stored = saveFood(food).copy(lastUsedAt = now)
        foods.upsert(stored)
        val t = stored.macrosFor(grams)
        val e = FoodEntry(UUID.randomUUID().toString(), date, stored.id, stored.name, stored.brand, grams, t.kcal, t.proteinG, t.carbsG, t.fatG, now, stored.imageUrl)
        entries.upsert(e)
        return e
    }

    override suspend fun updateEntryGrams(id: String, grams: Double, food: Food?) {
        val e = entries.get(id) ?: return
        val per = food?.macrosFor(grams) ?: run {
            val f = grams / e.grams.coerceAtLeast(0.0001)
            com.lifehub.app.domain.Totals(e.kcal * f, e.proteinG * f, e.carbsG * f, e.fatG * f)
        }
        entries.upsert(e.copy(grams = grams, kcal = per.kcal, proteinG = per.proteinG, carbsG = per.carbsG, fatG = per.fatG))
    }

    override suspend fun restoreEntry(entry: FoodEntry) = entries.upsert(entry)
    override suspend fun deleteEntry(id: String) = entries.delete(id)
}
