package com.lifehub.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.copilot.llm.ConnectionTestResult
import com.lifehub.app.copilot.llm.ConnectionTester
import com.lifehub.app.copilot.secrets.SecretNames
import com.lifehub.app.copilot.secrets.SecretStore
import com.lifehub.app.copilot.secrets.maskKey
import com.lifehub.app.data.prefs.CopilotSettings
import com.lifehub.app.data.prefs.CopilotSettingsRepository
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Running : ConnectionTestState
    data class Success(val model: String) : ConnectionTestState
    data class Failed(val message: String) : ConnectionTestState
}

/** Keys are exposed only as masks ("••••abcd"), never in full. */
data class CopilotUiState(
    val settings: CopilotSettings = CopilotSettings(),
    val llmKeyMask: String? = null,
    val sttKeyMask: String? = null,
    val test: ConnectionTestState = ConnectionTestState.Idle,
)

class CopilotSettingsViewModel(
    private val repo: CopilotSettingsRepository,
    private val secrets: SecretStore,
    private val tester: ConnectionTester,
) : ViewModel() {
    private val _ui = MutableStateFlow(snapshot(ConnectionTestState.Idle))
    val ui: StateFlow<CopilotUiState> = _ui

    private fun snapshot(test: ConnectionTestState) = CopilotUiState(
        settings = repo.current(),
        llmKeyMask = maskKey(secrets.get(SecretNames.LLM_API_KEY)),
        sttKeyMask = maskKey(secrets.get(SecretNames.STT_API_KEY)),
        test = test,
    )

    private fun refresh(test: ConnectionTestState = _ui.value.test) { _ui.value = snapshot(test) }

    // A changed provider/model/key invalidates a previous test result.
    private fun edited(block: () -> Unit) { block(); refresh(ConnectionTestState.Idle) }

    fun setLlmBaseUrl(v: String) = edited { if (v.isNotBlank()) repo.setLlmBaseUrl(v) }
    fun setLlmModel(v: String) = edited { if (v.isNotBlank()) repo.setLlmModel(v) }
    fun setSttBaseUrl(v: String) = edited { if (v.isNotBlank()) repo.setSttBaseUrl(v) }
    fun setSttModel(v: String) = edited { if (v.isNotBlank()) repo.setSttModel(v) }
    fun setSttLanguage(v: String) = edited { if (v.isNotBlank()) repo.setSttLanguage(v) }

    fun setNotificationsEnabled(v: Boolean) { repo.setNotificationsEnabled(v); refresh() }

    fun saveLlmKey(v: String) = edited { secrets.put(SecretNames.LLM_API_KEY, v.trim()) }
    fun removeLlmKey() = edited { secrets.remove(SecretNames.LLM_API_KEY) }
    fun saveSttKey(v: String) = edited { secrets.put(SecretNames.STT_API_KEY, v.trim()) }
    fun removeSttKey() = edited { secrets.remove(SecretNames.STT_API_KEY) }

    fun testConnection() {
        if (_ui.value.test == ConnectionTestState.Running) return
        viewModelScope.launch { runTest() }
    }

    internal suspend fun runTest() {
        if (_ui.value.test == ConnectionTestState.Running) return
        refresh(ConnectionTestState.Running)
        val state = when (val r = tester.run()) {
            is ConnectionTestResult.Success -> ConnectionTestState.Success(r.model)
            is ConnectionTestResult.Failure -> ConnectionTestState.Failed(r.error.message)
        }
        refresh(state)
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { CopilotSettingsViewModel(c.copilotSettings, c.secrets, c.llmConnectionTester) } }
    }
}
