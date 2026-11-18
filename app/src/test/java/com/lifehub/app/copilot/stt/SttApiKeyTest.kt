package com.lifehub.app.copilot.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One OpenRouter key is enough: the language model's key is used for transcription only on the same provider host. */
class SttApiKeyTest {
    private val or = "https://openrouter.ai/api/v1"

    @Test fun savedTranscriptionKeyWins() = assertEquals("stt", sttApiKey(or, or, "stt", "llm"))
    @Test fun sameHostFallsBackToTheLanguageModelKey() {
        assertEquals("llm", sttApiKey(or, or, null, "llm"))
        assertEquals("llm", sttApiKey("https://OpenRouter.ai/api/v1/", or, "  ", "llm"))
    }
    @Test fun differentHostNeverGetsTheLanguageModelKey() {
        assertNull(sttApiKey("https://api.groq.com/openai/v1", or, null, "llm"))
    }
    @Test fun noKeysOrBadUrlGivesNull() {
        assertNull(sttApiKey(or, or, null, null))
        assertNull(sttApiKey("not a url", or, null, "llm"))
    }
}
