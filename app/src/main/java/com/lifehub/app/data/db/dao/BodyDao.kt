package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface BodyDao {
    @Query("SELECT * FROM body_entries ORDER BY date DESC") fun observeAll(): Flow<List<BodyEntry>>
    @Query("SELECT * FROM body_entries WHERE date BETWEEN :from AND :to ORDER BY date") fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<BodyEntry>>
    @Query("SELECT * FROM body_entries WHERE date = :date") fun observe(date: LocalDate): Flow<BodyEntry?>
    @Query("SELECT * FROM body_entries WHERE date = :date") suspend fun get(date: LocalDate): BodyEntry?
    @Query("SELECT MAX(date) FROM body_entries") suspend fun latestDate(): LocalDate?
    @Upsert suspend fun upsert(entry: BodyEntry)
    @Query("DELETE FROM body_entries WHERE date = :date") suspend fun delete(date: LocalDate)
    @Query("SELECT * FROM body_entries ORDER BY date") suspend fun getAll(): List<BodyEntry>
    @Insert suspend fun insertAll(items: List<BodyEntry>)
    @Query("DELETE FROM body_entries") suspend fun deleteAll()
}
