package com.lifehub.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Non-secret copilot settings. Own prefs file: deliberately not part of [Settings], the JSON export or the DB. API keys live in SecretStore. */
data class CopilotSettings(
    val llmBaseUrl: String = DEFAULT_LLM_BASE_URL,
    val llmModel: String = DEFAULT_LLM_MODEL,
    val sttBaseUrl: String = DEFAULT_STT_BASE_URL,
    val sttModel: String = DEFAULT_STT_MODEL,
    val sttLanguage: String = DEFAULT_STT_LANGUAGE,
    /** Voice capture notifications (outcome, needs an answer, error). */
    val notificationsEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_LLM_BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_LLM_MODEL = "anthropic/claude-haiku-5.5"
        const val DEFAULT_STT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_STT_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_STT_LANGUAGE = "it"
    }
}

class CopilotSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("copilot_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<CopilotSettings> = _settings

    fun current(): CopilotSettings = settings.value

    private fun load(): CopilotSettings {
        val d = CopilotSettings()
        fun s(k: String, def: String) = prefs.getString(k, null)?.takeIf { it.isNotBlank() } ?: def
        return CopilotSettings(
            llmBaseUrl = s("llmBaseUrl", d.llmBaseUrl), llmModel = s("llmModel", d.llmModel),
            sttBaseUrl = s("sttBaseUrl", d.sttBaseUrl), sttModel = s("sttModel", d.sttModel),
            sttLanguage = s("sttLanguage", d.sttLanguage),
            notificationsEnabled = prefs.getBoolean("notificationsEnabled", d.notificationsEnabled),
        )
    }

    private fun write(s: CopilotSettings) {
        prefs.edit()
            .putString("llmBaseUrl", s.llmBaseUrl).putString("llmModel", s.llmModel)
            .putString("sttBaseUrl", s.sttBaseUrl).putString("sttModel", s.sttModel)
            .putString("sttLanguage", s.sttLanguage)
            .putBoolean("notificationsEnabled", s.notificationsEnabled)
            .apply()
        _settings.value = load()
    }

    // Blank input falls back to the default (an empty URL/model would only produce confusing errors).
    fun setLlmBaseUrl(v: String) = write(current().copy(llmBaseUrl = v.trim()))
    fun setLlmModel(v: String) = write(current().copy(llmModel = v.trim()))
    fun setSttBaseUrl(v: String) = write(current().copy(sttBaseUrl = v.trim()))
    fun setSttModel(v: String) = write(current().copy(sttModel = v.trim()))
    fun setSttLanguage(v: String) = write(current().copy(sttLanguage = v.trim().lowercase()))
    fun setNotificationsEnabled(v: Boolean) = write(current().copy(notificationsEnabled = v))

    /** Runtime permissions are asked once, on first use of the copilot (not at app start). */
    fun permissionAsked(name: String): Boolean = prefs.getBoolean("asked_$name", false)
    fun markPermissionAsked(name: String) = prefs.edit().putBoolean("asked_$name", true).apply()
}
