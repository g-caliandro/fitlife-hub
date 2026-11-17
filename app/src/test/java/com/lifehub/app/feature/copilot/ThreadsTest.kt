package com.lifehub.app.feature.copilot

import android.os.Looper
import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.capture.CaptureRunner
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.data.repo.NewChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Conversations list (design doc §10): rows with title, last update and open-request badge; open, delete, new. */
@RunWith(RobolectricTestRunner::class)
class ThreadsTest {
    private val f = CopilotFixture(seed = false)
    private val scope = CoroutineScope(Dispatchers.Main)
    @After fun tearDown() { scope.cancel(); f.close() }

    private fun awaitUntil(what: String, cond: () -> Boolean) {
        repeat(400) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(25)
        }
        throw AssertionError("timeout waiting for $what")
    }

    @Test fun rowsAreMostRecentFirstWithTheirOpenRequests() = runBlocking {
        val a = f.chat.createThread("Pranzo", ChatOrigin.CHAT, f.now.minusSeconds(3600)).id
        val b = f.chat.createThread("Voce", ChatOrigin.QUICK, f.now).id
        val cap = f.captures.create(b, ChatOrigin.QUICK, "x", f.zone, f.now)
        f.pending.create(cap.id, b, PendingKind.QUANTITY, "Quanto?", emptyList(), null, null, f.now)
        f.pending.create(cap.id, b, PendingKind.QUANTITY, "E il riso?", emptyList(), null, null, f.now, id = "p2")
        f.pending.discard("p2", f.now)
        val rows = threadRows(f.chat.observeThreads().first(), f.pending.observeOpen().first())
        assertEquals(listOf(b, a), rows.map { it.id })
        assertEquals(listOf(1, 0), rows.map { it.openRequests })
        assertEquals(ChatOrigin.QUICK, rows[0].origin)
        assertEquals("Today · 13:05", threadTime(rows[1].updatedAt, f.zone, f.today))
        assertEquals("Yesterday · 14:05", threadTime(f.now.minusSeconds(86_400), f.zone, f.today))
    }

    @Test fun deletingAThreadDiscardsItsOpenRequestsAndRemovesItsMessages() = runBlocking {
        val t = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        f.chat.append(t, NewChatMessage(ChatRole.USER, "ciao"), f.now)
        val cap = f.captures.create(t, ChatOrigin.CHAT, "x", f.zone, f.now)
        val p = f.pending.create(cap.id, t, PendingKind.QUANTITY, "Quanto?", emptyList(), null, null, f.now)
        val processor = f.processor(StepLlmClient())
        ThreadDeleter(f.chat, f.pending) { processor.discard(it) }.delete(t)
        assertNull(f.chat.thread(t))
        assertTrue(f.chat.messages(t).isEmpty())
        assertEquals(PendingStatus.DISCARDED, f.pending.get(p.id)!!.status)
        assertEquals(0, f.pending.observeOpenCount().first())
    }

    @Test fun titleIsTheFirstMessageWithoutAModelCall() {
        assertEquals("Quante proteine mi mancano oggi?", CaptureRunner.threadTitle("  Quante proteine\n mi mancano oggi?  "))
        val long = "parola ".repeat(20)
        assertTrue(CaptureRunner.threadTitle(long).length <= 60 && CaptureRunner.threadTitle(long).endsWith("…"))
    }

    @Test fun chatOpensAPickedThreadStartsANewOneAndLeavesADeletedOne() {
        val (old, other) = runBlocking {
            val o = f.chat.createThread("Vecchia", ChatOrigin.CHAT, f.now.minusSeconds(60)).id
            f.chat.append(o, NewChatMessage(ChatRole.USER, "messaggio vecchio"), f.now.minusSeconds(60))
            val x = f.chat.createThread("Altra", ChatOrigin.QUICK, f.now.minusSeconds(120)).id
            f.chat.append(x, NewChatMessage(ChatRole.USER, "dalla cattura"), f.now.minusSeconds(120))
            o to x
        }
        val llm = StepLlmClient({ text("Ciao!") })
        val picks = MutableStateFlow<String?>(null)
        val vm = ChatViewModel(f.chat, f.agent(llm), f.processor(llm), f.captures, f.pending, { true }, f.time, null,
            threadPicks = picks, onThreadPickConsumed = { picks.value = null })
        var latest: ChatUiState? = null
        scope.launch { vm.state.collect { latest = it } }
        awaitUntil("latest chat thread reopened") { (latest?.items?.firstOrNull() as? ChatItem.User)?.text == "messaggio vecchio" }

        picks.value = other   // a quick-capture thread chosen in the list
        awaitUntil("other thread") { (latest?.items?.firstOrNull() as? ChatItem.User)?.text == "dalla cattura" }

        picks.value = ChatViewModel.NEW_THREAD
        awaitUntil("empty chat") { latest?.items?.isEmpty() == true }
        assertTrue(vm.send("Prima domanda del nuovo thread"))
        awaitUntil("answered") { latest?.items?.any { it is ChatItem.Assistant } == true }
        val created = runBlocking { f.chat.observeThreads().first() }.first()
        assertEquals("Prima domanda del nuovo thread", created.title)
        assertTrue(created.id != old && created.id != other)

        // Deleting the open thread from the list: the chat goes back to empty and the next message starts a new thread.
        runBlocking { f.chat.deleteThread(created.id) }
        awaitUntil("cleared") { latest?.items?.isEmpty() == true }
    }

    @Test fun newThreadRouteStartsEmpty() {
        runBlocking { f.chat.createThread("Vecchia", ChatOrigin.CHAT, f.now).id.also { f.chat.append(it, NewChatMessage(ChatRole.USER, "x"), f.now) } }
        val vm = ChatViewModel(f.chat, f.agent(StepLlmClient()), f.processor(StepLlmClient()), f.captures, f.pending, { true }, f.time, ChatViewModel.NEW_THREAD)
        var latest: ChatUiState? = null
        scope.launch { vm.state.collect { latest = it } }
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue(latest!!.items.isEmpty())
    }
}
