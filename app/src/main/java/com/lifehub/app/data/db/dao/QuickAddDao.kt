package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.QuickAddEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Quick-add entries (several per day). */
@Dao interface QuickAddDao {
    @Query("SELECT * FROM quick_add_entries WHERE date BETWEEN :from AND :to ORDER BY date, createdAt, id")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<QuickAddEntry>>
    @Query("SELECT * FROM quick_add_entries WHERE date = :date ORDER BY createdAt, id") suspend fun getDay(date: LocalDate): List<QuickAddEntry>
    @Query("SELECT * FROM quick_add_entries WHERE id = :id") suspend fun get(id: String): QuickAddEntry?
    /** Most recent quick add strictly before [date] (source of "Copy ..." in the form). */
    @Query("SELECT * FROM quick_add_entries WHERE date < :date ORDER BY date DESC, createdAt DESC LIMIT 1")
    suspend fun latestBefore(date: LocalDate): QuickAddEntry?
    @Upsert suspend fun upsert(e: QuickAddEntry)
    @Query("DELETE FROM quick_add_entries WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM quick_add_entries ORDER BY date, createdAt, id") suspend fun getAll(): List<QuickAddEntry>
    @Insert suspend fun insertAll(items: List<QuickAddEntry>)
    @Query("DELETE FROM quick_add_entries") suspend fun deleteAll()
}
