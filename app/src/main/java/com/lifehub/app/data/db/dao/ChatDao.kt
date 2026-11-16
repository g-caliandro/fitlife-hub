package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.ChatMessage
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatThread
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC") fun observeThreads(): Flow<List<ChatThread>>
    @Query("SELECT * FROM chat_threads WHERE id = :id") suspend fun getThread(id: String): ChatThread?
    @Query("SELECT * FROM chat_threads WHERE origin = :origin ORDER BY updatedAt DESC LIMIT 1") suspend fun latestThread(origin: ChatOrigin): ChatThread?
    @Upsert suspend fun upsertThread(thread: ChatThread)
    @Query("UPDATE chat_threads SET updatedAt = :at WHERE id = :id") suspend fun touchThread(id: String, at: Instant)
    @Query("DELETE FROM chat_threads WHERE id = :id") suspend fun deleteThread(id: String)
    @Query("UPDATE chat_threads SET title = :title WHERE id = :id") suspend fun setTitle(id: String, title: String)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY seq") fun observeMessages(threadId: String): Flow<List<ChatMessage>>
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY seq") suspend fun getMessages(threadId: String): List<ChatMessage>
    @Query("SELECT MAX(seq) FROM chat_messages WHERE threadId = :threadId") suspend fun maxSeq(threadId: String): Int?
    @Insert suspend fun insertMessage(message: ChatMessage)
}
