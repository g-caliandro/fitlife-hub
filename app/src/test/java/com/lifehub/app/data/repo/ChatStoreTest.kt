package com.lifehub.app.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.copilot.agent.CardCodec
import com.lifehub.app.copilot.agent.DailySummaryCard
import com.lifehub.app.copilot.agent.ToolCallsCodec
import com.lifehub.app.copilot.llm.LlmToolCall
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.feature.copilot.ChatItem
import com.lifehub.app.feature.copilot.chatItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ChatStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = LifeHubDatabase.inMemory(ctx)
    private val store = ChatStore(db)
    private val t0 = Instant.parse("2026-11-16T10:00:00Z")

    @After fun tearDown() = db.close()

    private val card = DailySummaryCard(
        date = "2026-11-16", kcal = 573, proteinG = 51, carbsG = 75, fatG = 6,
        kcalTarget = 2200, proteinTargetG = 150, fatTargetG = 70,
        kcalRemaining = 1627, proteinRemainingG = 99, fatRemainingG = 64,
        carbsKnown = true, proteinComplete = false, items = 3,
    )

    @Test fun messagesPersistInOrderWithToolCallsAndCard() { runBlocking {
        val t = store.createThread("Proteine", ChatOrigin.CHAT, t0)
        val calls = listOf(LlmToolCall("c1", "get_daily_summary", """{"date":"2026-11-16"}"""))
        val (type, json) = CardCodec.encode(card)
        store.append(t.id, NewChatMessage(ChatRole.USER, "Quante proteine mi mancano oggi?"), t0.plusSeconds(1))
        store.append(t.id, NewChatMessage(ChatRole.ASSISTANT, null, toolCallsJson = ToolCallsCodec.encode(calls)), t0.plusSeconds(2))
        store.append(t.id, NewChatMessage(ChatRole.TOOL, """{"remaining":{"protein_g":99}}""", toolCallId = "c1", toolName = "get_daily_summary", cardType = type, cardJson = json), t0.plusSeconds(3))
        store.append(t.id, NewChatMessage(ChatRole.ASSISTANT, "Ti mancano 99 g."), t0.plusSeconds(4))

        // Reopen through a fresh store on the same DB (as after an app restart).
        val reopened = ChatStore(db)
        val msgs = reopened.observeMessages(t.id).first()
        assertEquals(listOf(0, 1, 2, 3), msgs.map { it.seq })
        assertEquals(calls, ToolCallsCodec.decode(msgs[1].toolCallsJson))
        assertNull(msgs[1].text)
        assertEquals("c1", msgs[2].toolCallId)
        assertEquals(card, CardCodec.decode(msgs[2].cardType, msgs[2].cardJson)) // card round trip
        assertEquals(t0.plusSeconds(4), reopened.thread(t.id)!!.updatedAt)

        // What the chat screen redraws: user bubble, card, assistant bubble (the bare tool call is hidden).
        val items = chatItems(msgs)
        assertEquals(listOf(ChatItem.User::class, ChatItem.Card::class, ChatItem.Assistant::class), items.map { it::class })
        assertEquals(card, (items[1] as ChatItem.Card).card)
    } }

    @Test fun threadsAreIsolatedAndLatestIsMostRecentlyUpdated() { runBlocking {
        val a = store.createThread("A", ChatOrigin.CHAT, t0)
        val b = store.createThread("B", ChatOrigin.CHAT, t0.plusSeconds(10))
        val q = store.createThread("Quick", ChatOrigin.QUICK, t0.plusSeconds(20))
        store.append(a.id, NewChatMessage(ChatRole.USER, "in A"), t0.plusSeconds(30))
        store.append(b.id, NewChatMessage(ChatRole.USER, "in B"), t0.plusSeconds(15))
        assertEquals(a.id, store.latestThread(ChatOrigin.CHAT)!!.id)
        assertEquals(q.id, store.latestThread(ChatOrigin.QUICK)!!.id)
        assertEquals(listOf("in A"), store.messages(a.id).map { it.text })
        assertEquals(0, store.messages(b.id).single().seq) // seq is per thread
        assertEquals(listOf(a.id, q.id, b.id), store.observeThreads().first().map { it.id })
        store.deleteThread(a.id)
        assertEquals(0, store.messages(a.id).size)
        assertEquals(1, store.messages(b.id).size)
    } }

    @Test fun unknownCardTypeIsIgnored() {
        assertNull(CardCodec.decode("plan_draft", "{}"))
        assertNull(CardCodec.decode(CardCodec.DAILY_SUMMARY, "not json"))
    }
}
