package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY isArchived ASC, name COLLATE NOCASE ASC") fun observeAll(): Flow<List<Plan>>
    @Query("SELECT * FROM plans WHERE id = :id") fun observe(id: String): Flow<Plan?>
    @Query("SELECT * FROM plans WHERE id = :id") suspend fun get(id: String): Plan?
    @Upsert suspend fun upsert(plan: Plan)
    @Query("DELETE FROM plans WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM plan_exercises ORDER BY planId, orderIndex") fun observeAllExercises(): Flow<List<PlanExercise>>
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex") fun observeExercises(planId: String): Flow<List<PlanExercise>>
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex") suspend fun getExercises(planId: String): List<PlanExercise>
    @Query("DELETE FROM plan_exercises WHERE planId = :planId") suspend fun deleteExercises(planId: String)
    @Insert suspend fun insertExercises(items: List<PlanExercise>)
    @Upsert suspend fun upsertExercises(items: List<PlanExercise>)

    @Query("SELECT * FROM plans") suspend fun getAll(): List<Plan>
    @Query("SELECT * FROM plan_exercises") suspend fun getAllExercises(): List<PlanExercise>
    @Insert suspend fun insertAll(items: List<Plan>)
    @Query("DELETE FROM plans") suspend fun deleteAll()
    @Query("DELETE FROM plan_exercises") suspend fun deleteAllExercises()
}
