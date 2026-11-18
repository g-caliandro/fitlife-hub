package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.ChatMessage
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.ChatThread
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/** A message to append; [ChatStore.append] assigns id, seq and createdAt. Field meaning: see [ChatMessage]. */
data class NewChatMessage(
    val role: ChatRole,
    val text: String?,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val cardType: String? = null,
    val cardJson: String? = null,
    val captureId: String? = null,
)

/** Copilot threads and messages (design doc "ChatStore"). Multi-thread storage; the phase 2 UI shows one thread. */
class ChatStore(private val db: LifeHubDatabase) {
    private val dao = db.chatDao()

    fun observeThreads() = dao.observeThreads()
    fun observeMessages(threadId: String) = dao.observeMessages(threadId)
    suspend fun thread(id: String) = dao.getThread(id)
    suspend fun messages(threadId: String) = dao.getMessages(threadId)
    /** Most recently updated thread of [origin], or null. */
    suspend fun latestThread(origin: ChatOrigin = ChatOrigin.CHAT) = dao.latestThread(origin)

    suspend fun createThread(title: String, origin: ChatOrigin, now: Instant): ChatThread {
        val t = ChatThread(UUID.randomUUID().toString(), title, origin, now, now)
        dao.upsertThread(t)
        return t
    }

    /** Appends at the end of the thread (seq = last + 1, in one transaction) and bumps the thread's updatedAt. */
    suspend fun append(threadId: String, message: NewChatMessage, now: Instant): ChatMessage = db.withTransaction {
        val m = ChatMessage(
            id = UUID.randomUUID().toString(), threadId = threadId, seq = (dao.maxSeq(threadId) ?: -1) + 1,
            role = message.role, text = message.text, toolCallsJson = message.toolCallsJson,
            toolCallId = message.toolCallId, toolName = message.toolName,
            cardType = message.cardType, cardJson = message.cardJson, captureId = message.captureId, createdAt = now,
        )
        dao.insertMessage(m)
        dao.touchThread(threadId, now)
        m
    }

    suspend fun deleteThread(id: String) = dao.deleteThread(id)
    suspend fun setTitle(threadId: String, title: String) = dao.setTitle(threadId, title)
}
