package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface FoodDao {
    @Query("SELECT * FROM foods WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<Food>>
    @Query("SELECT * FROM foods WHERE favorite = 1 OR source = 'CUSTOM' ORDER BY favorite DESC, name COLLATE NOCASE") fun observeMine(): Flow<List<Food>>
    @Query("SELECT * FROM foods WHERE id = :id") suspend fun get(id: String): Food?
    @Query("SELECT * FROM foods WHERE id = :id") fun observe(id: String): Flow<Food?>
    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1") suspend fun getByBarcode(barcode: String): Food?
    @Upsert suspend fun upsert(food: Food)
    @Query("DELETE FROM foods WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM foods ORDER BY name") suspend fun getAll(): List<Food>
    /** Copilot resolver step 1: logged at least once, favourites, own foods and accepted estimates. */
    @Query("SELECT * FROM foods WHERE lastUsedAt IS NOT NULL OR favorite = 1 OR source IN ('CUSTOM', 'ESTIMATE')") suspend fun history(): List<Food>
    @Query("SELECT * FROM foods WHERE baseFoodId = :baseId ORDER BY lastUsedAt DESC LIMIT 1") suspend fun byBaseId(baseId: String): Food?
    @Insert suspend fun insert(food: Food)
    @Query("UPDATE foods SET lastUsedAt = :at WHERE id = :id") suspend fun setLastUsed(id: String, at: java.time.Instant?)
    @Insert suspend fun insertAll(items: List<Food>)
    @Query("DELETE FROM foods") suspend fun deleteAll()
}

@Dao interface FoodEntryDao {
    @Query("SELECT * FROM food_entries WHERE date BETWEEN :from AND :to ORDER BY date, createdAt") fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>>
    @Query("SELECT * FROM food_entries WHERE id = :id") suspend fun get(id: String): FoodEntry?
    @Upsert suspend fun upsert(entry: FoodEntry)
    @Query("DELETE FROM food_entries WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM food_entries ORDER BY date, createdAt") suspend fun getAll(): List<FoodEntry>
    @Insert suspend fun insert(entry: FoodEntry)
    @Query("SELECT COUNT(*) FROM food_entries WHERE foodId = :foodId") suspend fun countForFood(foodId: String): Int
    @Query("SELECT * FROM food_entries WHERE captureId = :captureId ORDER BY createdAt, rowid") suspend fun byCapture(captureId: String): List<FoodEntry>
    @Insert suspend fun insertAll(items: List<FoodEntry>)
    @Query("DELETE FROM food_entries") suspend fun deleteAll()
}
