package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.PlanDraft
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.data.model.UserMemory
import kotlinx.coroutines.flow.Flow

@Dao interface PlanDraftDao {
    @Query("SELECT * FROM plan_drafts ORDER BY createdAt DESC") fun observeAll(): Flow<List<PlanDraft>>
    @Query("SELECT * FROM plan_drafts WHERE id = :id") fun observe(id: String): Flow<PlanDraft?>
    @Query("SELECT * FROM plan_drafts ORDER BY createdAt DESC") suspend fun all(): List<PlanDraft>
    @Query("SELECT * FROM plan_drafts WHERE id = :id") suspend fun get(id: String): PlanDraft?
    @Query("SELECT * FROM plan_drafts WHERE captureId = :captureId ORDER BY createdAt") suspend fun byCapture(captureId: String): List<PlanDraft>
    @Query("SELECT * FROM plan_drafts WHERE status = :status ORDER BY createdAt DESC") fun observeByStatus(status: PlanDraftStatus): Flow<List<PlanDraft>>
    @Upsert suspend fun upsert(draft: PlanDraft)
    @Query("DELETE FROM plan_drafts WHERE id = :id") suspend fun delete(id: String)
}

@Dao interface UserMemoryDao {
    @Query("SELECT * FROM user_memory ORDER BY `key`") fun observeAll(): Flow<List<UserMemory>>
    @Query("SELECT * FROM user_memory ORDER BY `key`") suspend fun all(): List<UserMemory>
    @Query("SELECT * FROM user_memory WHERE `key` = :key") suspend fun get(key: String): UserMemory?
    @Upsert suspend fun upsert(m: UserMemory)
    @Query("DELETE FROM user_memory WHERE `key` = :key") suspend fun delete(key: String)
    @Query("DELETE FROM user_memory") suspend fun deleteAll()
    @Insert suspend fun insertAll(items: List<UserMemory>)
}
