package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface MacroDao {
    @Query("SELECT * FROM macro_days WHERE date BETWEEN :from AND :to ORDER BY date") fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<MacroDay>>
    @Query("SELECT * FROM macro_days WHERE date = :date") fun observe(date: LocalDate): Flow<MacroDay?>
    @Query("SELECT * FROM macro_days WHERE date = :date") suspend fun get(date: LocalDate): MacroDay?
    @Query("SELECT * FROM macro_days WHERE date < :date ORDER BY date DESC LIMIT 1") suspend fun latestBefore(date: LocalDate): MacroDay?
    @Upsert suspend fun upsert(day: MacroDay)
    @Query("DELETE FROM macro_days WHERE date = :date") suspend fun delete(date: LocalDate)
    @Query("SELECT * FROM macro_days ORDER BY date") suspend fun getAll(): List<MacroDay>
    @Insert suspend fun insertAll(items: List<MacroDay>)
    @Query("DELETE FROM macro_days") suspend fun deleteAll()
}
