package com.lifehub.app.feature.copilot

import android.os.Looper
import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.capture.PendingCodec
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.PendingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What the user types in the chat while a request of the copilot is open IS the answer to it: it resumes the same capture
 * (same meal, nothing logged twice) instead of starting a new one that would re-log what was already logged.
 */
@RunWith(RobolectricTestRunner::class)
class ChatAnswersPendingTest {
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

    @Test fun textTypedInTheChatAnswersTheOpenRequestOfTheSameCapture() {
        val thread = runBlocking { f.chat.createThread("t", ChatOrigin.CHAT, f.now).id }
        val llm = StepLlmClient(
            { tools(call("c1", "request_user_input", """{"kind":"quantity","prompt":"Quanta scamorza?","item":{"name":"scamorza"}}""")) },
            { text("Ok, ti aspetto.") },
            { text("Fatto.") },
        )
        val vm = ChatViewModel(f.chat, f.agent(llm), f.processor(llm), f.captures, f.pending, { true }, f.time, thread)
        var latest: ChatUiState? = null
        scope.launch { vm.state.collect { latest = it } }

        assertTrue(vm.send("riso, olio e 2 ciliegine di scamorza"))
        awaitUntil("request open") { latest?.pending?.values?.any { it.status == PendingStatus.OPEN } == true && latest?.working == false }

        assertTrue(vm.send("non so la marca, usa valori standard"))
        awaitUntil("request answered") { latest?.pending?.values?.all { it.status == PendingStatus.ANSWERED } == true && latest?.working == false }

        val caps = runBlocking { f.captures.observeByThread(thread).first() }
        assertEquals("the reply resumes the same capture", 1, caps.size)
        val p = runBlocking { f.pending.byCapture(caps.single().id) }.single()
        assertEquals("non so la marca, usa valori standard", PendingCodec.answer(p)?.text)
    }

    @Test fun newChatStartsAnEmptyConversationAndTheNextMessageGetsItsOwnThread() {
        val thread = runBlocking { f.chat.createThread("t", ChatOrigin.CHAT, f.now).id }
        val llm = StepLlmClient({ text("Ciao.") }, { text("Ciao di nuovo.") })
        val vm = ChatViewModel(f.chat, f.agent(llm), f.processor(llm), f.captures, f.pending, { true }, f.time, thread)
        var latest: ChatUiState? = null
        scope.launch { vm.state.collect { latest = it } }

        assertTrue(vm.send("ciao"))
        awaitUntil("first answer") { latest?.items?.any { it is ChatItem.Assistant } == true && latest?.working == false }

        vm.newChat()
        awaitUntil("empty chat") { latest?.items?.isEmpty() == true }

        assertTrue(vm.send("ciao ancora"))
        awaitUntil("second answer") { latest?.items?.any { it is ChatItem.Assistant } == true && latest?.working == false }
        assertEquals(2, runBlocking { f.chat.observeThreads().first() }.size)
    }
}
