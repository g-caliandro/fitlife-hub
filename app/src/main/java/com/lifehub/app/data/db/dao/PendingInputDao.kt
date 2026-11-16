package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lifehub.app.data.model.PendingInput
import kotlinx.coroutines.flow.Flow

@Dao interface PendingInputDao {
    @Query("SELECT * FROM pending_inputs WHERE id = :id") suspend fun get(id: String): PendingInput?
    @Query("SELECT * FROM pending_inputs WHERE captureId = :captureId ORDER BY createdAt, rowid") suspend fun byCapture(captureId: String): List<PendingInput>
    @Query("SELECT * FROM pending_inputs WHERE status = 'OPEN' ORDER BY createdAt") fun observeOpen(): Flow<List<PendingInput>>
    @Query("SELECT COUNT(*) FROM pending_inputs WHERE status = 'OPEN'") fun observeOpenCount(): Flow<Int>
    @Query("SELECT * FROM pending_inputs WHERE threadId = :threadId ORDER BY createdAt") fun observeByThread(threadId: String): Flow<List<PendingInput>>
    @Query("UPDATE pending_inputs SET notifiedAt = :at WHERE id = :id AND notifiedAt IS NULL") suspend fun markNotified(id: String, at: java.time.Instant): Int
    @Insert suspend fun insert(p: PendingInput)
    @Update suspend fun update(p: PendingInput)
}
