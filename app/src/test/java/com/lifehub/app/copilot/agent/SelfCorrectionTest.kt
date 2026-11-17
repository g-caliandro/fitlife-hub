package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.data.model.ChatOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cross-cutting criterion "180 grammi, no aspetta, 120 registra 120". The self-correction is resolved by the model before
 * writing (design doc §7); with a fake model the app-side part is checked: the system prompt carries the rule, and the
 * single log_meal call with the final value writes 120 g only (no 180 g entry, nothing written twice).
 */
@RunWith(RobolectricTestRunner::class)
class SelfCorrectionTest {
    private val f = CopilotFixture(seed = false)
    @After fun tearDown() = f.close()

    @Test fun onlyTheFinalValueIsLogged() = runBlocking {
        f.baseFoods.ensureSeeded()
        val thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        val llm = StepLlmClient(
            { tools(call("c1", "log_meal", """{"items":[{"ref":"base:sr-171077","name":"petto di pollo","grams":120}]}""")) },
            { text("Registrati 120 g di pollo.") },
        )
        f.processor(llm).submitText(thread, "180 grammi di petto di pollo, no aspetta, erano 120")
        val system = (llm.requests.first().first() as LlmMessage.System).content
        assertTrue(system.contains("\"180 grammi, no aspetta, 120\"") && system.contains("usa solo il valore finale"))
        val entries = f.food.observeEntries(f.today, f.today).first()
        assertEquals(listOf(120.0), entries.map { it.grams })
    }
}
