package com.lifehub.app.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.copilot.llm.*
import com.lifehub.app.copilot.secrets.InMemorySecretStore
import com.lifehub.app.copilot.secrets.SecretNames
import com.lifehub.app.data.prefs.CopilotSettings
import com.lifehub.app.data.prefs.CopilotSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CopilotSettingsViewModelTest {
    private lateinit var repo: CopilotSettingsRepository
    private val secrets = InMemorySecretStore()
    private var next: LlmResult = LlmResult.Failure(LlmError.MissingKey)
    private val llm = object : LlmClient {
        override suspend fun chat(messages: List<LlmMessage>, tools: List<LlmTool>, toolChoice: LlmToolChoice, maxTokens: Int?) = next
    }
    private fun toolOk() = LlmResult.Ok(LlmReply(LlmMessage.Assistant(null, listOf(LlmToolCall("1", "ping", "{}"))), "tool_calls"))

    private fun vm() = CopilotSettingsViewModel(repo, secrets, ConnectionTester(llm) { repo.current().llmModel })

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("copilot_settings", Context.MODE_PRIVATE).edit().clear().commit()
        repo = CopilotSettingsRepository(ctx)
    }

    @Test fun defaultsMatchDecision17() {
        val s = vm().ui.value.settings
        assertEquals("https://openrouter.ai/api/v1", s.llmBaseUrl)
        assertEquals("anthropic/claude-haiku-5.5", s.llmModel)
        assertEquals("openai/whisper-large-v3-turbo", s.sttModel)
        assertEquals("it", s.sttLanguage)
        assertEquals("https://openrouter.ai/api/v1", s.sttBaseUrl)
    }

    @Test fun savedGroqDefaultsMoveToOpenRouterButACustomChoiceIsKept() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("copilot_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("sttBaseUrl", "https://api.groq.com/openai/v1").putString("sttModel", "whisper-large-v3-turbo").commit()
        val migrated = CopilotSettingsRepository(ctx).current()
        assertEquals("https://openrouter.ai/api/v1", migrated.sttBaseUrl)
        assertEquals("openai/whisper-large-v3-turbo", migrated.sttModel)

        prefs.edit().putString("sttBaseUrl", "https://api.groq.com/openai/v1").putString("sttModel", "whisper-large-v3").commit()
        val kept = CopilotSettingsRepository(ctx).current()
        assertEquals("https://api.groq.com/openai/v1", kept.sttBaseUrl)
        assertEquals("whisper-large-v3", kept.sttModel)
    }

    @Test fun editsPersistAndBlankIsIgnored() {
        val v = vm()
        v.setLlmModel("  openai/gpt-5-mini "); v.setSttLanguage("EN"); v.setLlmBaseUrl("   ")
        assertEquals("openai/gpt-5-mini", v.ui.value.settings.llmModel)
        assertEquals("en", v.ui.value.settings.sttLanguage)
        assertEquals(CopilotSettings.DEFAULT_LLM_BASE_URL, v.ui.value.settings.llmBaseUrl)
        assertEquals("openai/gpt-5-mini", vm().ui.value.settings.llmModel)
    }

    @Test fun keysGoToSecretStoreAndUiOnlySeesMask() {
        val v = vm()
        assertNull(v.ui.value.llmKeyMask)
        v.saveLlmKey("  sk-or-v1-abcdefghijklmnop1234 ")
        assertEquals("sk-or-v1-abcdefghijklmnop1234", secrets.get(SecretNames.LLM_API_KEY))
        assertEquals("••••1234", v.ui.value.llmKeyMask)
        assertFalse(v.ui.value.toString().contains("abcdefghijklmnop"))
        v.saveSttKey("short"); assertEquals("••••", v.ui.value.sttKeyMask)
        v.removeLlmKey(); assertNull(v.ui.value.llmKeyMask); assertNull(secrets.get(SecretNames.LLM_API_KEY))
        v.saveLlmKey("   "); assertNull(secrets.get(SecretNames.LLM_API_KEY))
    }

    @Test fun testConnectionSuccessAndFailureStates() = runBlocking {
        val v = vm()
        next = toolOk(); v.runTest()
        assertEquals(ConnectionTestState.Success("anthropic/claude-haiku-5.5"), v.ui.value.test)
        next = LlmResult.Failure(LlmError.ToolsUnsupported); v.runTest()
        assertTrue((v.ui.value.test as ConnectionTestState.Failed).message.contains("does not support tool calling"))
        next = LlmResult.Failure(LlmError.MissingKey); v.runTest()
        assertTrue((v.ui.value.test as ConnectionTestState.Failed).message.contains("No LLM API key"))
    }

    @Test fun editingInvalidatesPreviousTestResult() = runBlocking {
        val v = vm(); next = toolOk(); v.runTest()
        assertTrue(v.ui.value.test is ConnectionTestState.Success)
        v.setLlmModel("x/y"); assertEquals(ConnectionTestState.Idle, v.ui.value.test)
    }

    @Test fun notificationsToggleIsPersistedAndOnByDefault() {
        val vm = vm()
        assertTrue(vm.ui.value.settings.notificationsEnabled)
        vm.setNotificationsEnabled(false)
        assertFalse(vm.ui.value.settings.notificationsEnabled)
        assertFalse(CopilotSettingsRepository(ApplicationProvider.getApplicationContext()).current().notificationsEnabled)
    }
}
