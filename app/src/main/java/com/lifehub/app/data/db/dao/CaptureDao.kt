package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.CaptureWrite
import kotlinx.coroutines.flow.Flow

@Dao interface CaptureDao {
    @Query("SELECT * FROM captures WHERE id = :id") suspend fun get(id: String): Capture?
    @Query("SELECT * FROM captures WHERE id = :id") fun observe(id: String): Flow<Capture?>
    @Query("SELECT * FROM captures WHERE threadId = :threadId ORDER BY createdAt") fun observeByThread(threadId: String): Flow<List<Capture>>
    @Query("SELECT * FROM captures WHERE status IN (:statuses) ORDER BY createdAt") suspend fun byStatus(statuses: List<CaptureStatus>): List<Capture>
    @Query("SELECT * FROM captures WHERE status IN (:statuses) ORDER BY createdAt DESC") fun observeByStatus(statuses: List<CaptureStatus>): Flow<List<Capture>>
    @Upsert suspend fun upsert(capture: Capture)
    @Query("DELETE FROM captures") suspend fun deleteAll()

    @Query("SELECT * FROM capture_writes WHERE captureId = :captureId ORDER BY createdAt, rowid") suspend fun writes(captureId: String): List<CaptureWrite>
    @Query("SELECT * FROM capture_writes WHERE captureId = :captureId AND idemKey = :idemKey LIMIT 1") suspend fun writeByKey(captureId: String, idemKey: String): CaptureWrite?
    @Query("SELECT * FROM capture_writes WHERE captureId = :captureId AND tableName = :table AND recordId = :recordId LIMIT 1")
    suspend fun write(captureId: String, table: String, recordId: String): CaptureWrite?
    @Insert suspend fun insertWrite(write: CaptureWrite)
}
