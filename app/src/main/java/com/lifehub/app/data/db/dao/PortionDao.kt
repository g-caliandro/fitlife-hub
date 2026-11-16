package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.BaseFood
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.HouseholdMeasure
import kotlinx.coroutines.flow.Flow

/** Food portions + household measures (v9). */
@Dao interface PortionDao {
    @Query("SELECT * FROM food_portions WHERE foodId = :foodId ORDER BY isDefault DESC, grams") suspend fun portions(foodId: String): List<FoodPortion>
    @Query("SELECT * FROM food_portions WHERE foodId = :foodId ORDER BY isDefault DESC, grams") fun observePortions(foodId: String): Flow<List<FoodPortion>>
    @Query("SELECT * FROM food_portions ORDER BY foodId, grams, id") suspend fun allPortions(): List<FoodPortion>
    @Query("SELECT * FROM food_portions WHERE id = :id") suspend fun portion(id: String): FoodPortion?
    @Upsert suspend fun upsertPortion(p: FoodPortion)
    @Query("DELETE FROM food_portions WHERE id = :id") suspend fun deletePortion(id: String)
    @Insert suspend fun insertPortions(items: List<FoodPortion>)
    @Query("DELETE FROM food_portions") suspend fun deleteAllPortions()

    @Query("SELECT * FROM household_measures ORDER BY (foodId IS NOT NULL OR baseFoodId IS NOT NULL), label, id") suspend fun measures(): List<HouseholdMeasure>
    @Query("SELECT * FROM household_measures ORDER BY (foodId IS NOT NULL OR baseFoodId IS NOT NULL), label, id") fun observeMeasures(): Flow<List<HouseholdMeasure>>
    @Upsert suspend fun upsertMeasure(m: HouseholdMeasure)
    @Query("DELETE FROM household_measures WHERE id = :id") suspend fun deleteMeasure(id: String)
    @Insert suspend fun insertMeasures(items: List<HouseholdMeasure>)
    @Query("DELETE FROM household_measures") suspend fun deleteAllMeasures()
}

@Dao interface BaseFoodDao {
    @Query("SELECT * FROM base_foods ORDER BY id") suspend fun all(): List<BaseFood>
    @Query("SELECT * FROM base_foods WHERE id = :id") suspend fun get(id: String): BaseFood?
    @Query("SELECT COUNT(*) FROM base_foods") suspend fun count(): Int
    @Insert suspend fun insertAll(items: List<BaseFood>)
    @Query("DELETE FROM base_foods") suspend fun deleteAll()
}
