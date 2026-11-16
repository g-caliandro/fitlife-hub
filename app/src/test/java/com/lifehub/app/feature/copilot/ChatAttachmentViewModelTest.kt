package com.lifehub.app.feature.copilot

import android.os.Looper
import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.off.OffProduct
import com.lifehub.app.data.repo.ProductLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The chat's composer attachment: a scanner result becomes a chip, is sent with the message and cleared (phase 5). */
@RunWith(RobolectricTestRunner::class)
class ChatAttachmentViewModelTest {
    private val f = CopilotFixture(seed = false)
    private val scope = CoroutineScope(Dispatchers.Main)
    @After fun tearDown() { scope.cancel(); f.close() }

    private val bar = "8001505005707"
    private fun awaitUntil(what: String, cond: () -> Boolean) {
        repeat(400) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(25)
        }
        throw AssertionError("timeout waiting for $what")
    }

    @Test fun scannedProductBecomesAChipIsSentWithTheMessageAndShownInTheBubble() {
        val foodId = runBlocking {
            f.baseFoods.ensureSeeded()
            f.offBarcodes.products = mapOf(bar to OffProduct(bar, "Nocciolata", "Rigoni", 539.0, 6.3, 57.5, 30.9, 350.0, 15.0))
            f.barcodes.cache((f.barcodes.findProduct(bar) as ProductLookup.Found).product).id
        }
        val thread = runBlocking { f.chat.createThread("t", ChatOrigin.CHAT, f.now).id }
        val llm = StepLlmClient(
            { tools(call("c1", "log_meal", """{"items":[{"ref":"off:$bar","grams":30}]}""")) },
            { text("Registrato.") },
        )
        val picks = MutableStateFlow<String?>(null)
        var consumed = 0
        val vm = ChatViewModel(
            f.chat, f.agent(llm), f.processor(llm), f.captures, f.pending, { true }, f.time, thread,
            attachmentLoader = { id -> f.catalog.food(id)?.let { ChatAttachment(it.id, it.name + (it.brand?.let { b -> " ($b)" } ?: ""), it.incomplete) } },
            picks = picks, onPickConsumed = { consumed++; picks.value = null },
        )
        var latest: ChatUiState? = null
        scope.launch { vm.state.collect { latest = it } }

        picks.value = foodId
        awaitUntil("chip") { latest?.attachment != null }
        assertEquals("Nocciolata (Rigoni)", latest!!.attachment!!.label)
        assertEquals(1, consumed)

        vm.removeAttachment()
        awaitUntil("chip removed") { latest?.attachment == null }
        picks.value = foodId
        awaitUntil("chip again") { latest?.attachment != null }

        assertTrue(vm.send("ne ho mangiati 30 grammi"))
        awaitUntil("turn done") { latest?.items?.any { it is ChatItem.Assistant } == true }
        assertNull("cleared after sending", latest!!.attachment)
        val cap = runBlocking { f.captures.observeByThread(thread).let { it.first() } }.single()
        assertEquals(foodId, cap.attachedFoodId)
        val bubble = latest!!.items.filterIsInstance<ChatItem.User>().single()
        assertEquals("Nocciolata (Rigoni)", bubble.attachment)
        assertNotNull(runBlocking { f.db.foodEntryDao().byCapture(cap.id) }.singleOrNull())
    }
}
