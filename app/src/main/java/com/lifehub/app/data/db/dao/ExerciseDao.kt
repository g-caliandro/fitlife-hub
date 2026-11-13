package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY category, name") fun observeAll(): Flow<List<Exercise>>
    @Query("SELECT * FROM exercises WHERE id = :id") suspend fun get(id: String): Exercise?
    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1") suspend fun findByName(name: String): Exercise?   // NOCASE via column collation
    @Upsert suspend fun upsert(exercise: Exercise)
    @Query("DELETE FROM exercises WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT (SELECT COUNT(*) FROM plan_exercises WHERE exerciseId = :id) + (SELECT COUNT(*) FROM session_sets WHERE exerciseId = :id)")
    suspend fun referenceCount(id: String): Int
    @Query("SELECT COUNT(*) FROM exercises") suspend fun count(): Int
    @Query("SELECT * FROM exercises") suspend fun getAll(): List<Exercise>
    @Insert suspend fun insertAll(items: List<Exercise>)
    @Query("DELETE FROM exercises") suspend fun deleteAll()
}
