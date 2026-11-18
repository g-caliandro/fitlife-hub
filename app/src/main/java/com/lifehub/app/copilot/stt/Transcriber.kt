package com.lifehub.app.copilot.stt

import com.lifehub.app.copilot.llm.ConnectFailedException
import com.lifehub.app.copilot.llm.HttpRequest
import com.lifehub.app.copilot.llm.HttpTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

/** Typed, user-readable speech-to-text failures. [message] is safe to show (never contains the key or the audio). */
sealed class SttError(val message: String) {
    data object MissingKey : SttError("No speech-to-text API key set. Add it in Copilot settings.")
    data class InvalidConfig(val reason: String) : SttError("Invalid speech-to-text settings: $reason")
    data class Network(val timeout: Boolean) : SttError(
        if (timeout) "The transcription server took too long to answer." else "Couldn't reach the transcription server. Check your connection."
    )
    data class Auth(val httpCode: Int) : SttError("The speech-to-text key was rejected ($httpCode). Check the key.")
    data object RateLimited : SttError("Speech-to-text rate limit reached. Try again in a moment.")
    data object TooLarge : SttError("The recording is too large to transcribe.")
    data object AudioMissing : SttError("The recording file is missing.")
    data class Http(val httpCode: Int, val detail: String?) : SttError("The transcription server returned an error ($httpCode)" + (detail?.let { ": $it" } ?: "."))
    data class Malformed(val reason: String) : SttError("Unexpected response from the transcription server ($reason).")

    /** Worth a Retry without changing settings (network trouble, temporary limits, server errors). */
    val transient: Boolean get() = this is Network || this == RateLimited || (this is Http && httpCode >= 500)
}

sealed interface SttResult {
    data class Ok(val text: String) : SttResult
    data class Failure(val error: SttError) : SttResult
}

/**
 * Audio -> text (design doc §4 "Transcriber"). The cloud implementation below is the first one; an on-device model can
 * replace it behind this interface without touching the capture pipeline.
 */
interface Transcriber {
    suspend fun transcribe(audio: File, language: String?): SttResult
}

/** What the transcriber needs at call time; read fresh from settings + secret store on every call. */
class SttConfig(val baseUrl: String, val model: String, val apiKey: String?) {
    override fun toString() = "SttConfig(baseUrl=$baseUrl, model=$model, apiKey=<redacted>)"
}

/**
 * The key for transcription: the one saved for it, else (same provider host as the language model, e.g. OpenRouter for
 * both) the language model's key, so a single key is enough. Never falls back across different hosts.
 */
fun sttApiKey(sttBaseUrl: String, llmBaseUrl: String, sttKey: String?, llmKey: String?): String? {
    sttKey?.takeIf { it.isNotBlank() }?.let { return it }
    fun host(u: String) = runCatching { java.net.URI(u.trim()).host }.getOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }
    val h = host(sttBaseUrl) ?: return null
    return if (h == host(llmBaseUrl)) llmKey?.takeIf { it.isNotBlank() } else null
}

/** One multipart/form-data body (RFC 7578): text fields + one file part. Pure, unit-tested. */
class MultipartBody(val boundary: String = "lifehub-" + UUID.randomUUID().toString().replace("-", "")) {
    private val out = ByteArrayOutputStream()
    private fun line(s: String = "") = out.write((s + "\r\n").toByteArray(Charsets.UTF_8))

    fun field(name: String, value: String) = apply {
        line("--$boundary"); line("Content-Disposition: form-data; name=\"$name\""); line(); line(value)
    }

    fun file(name: String, fileName: String, contentType: String, bytes: ByteArray) = apply {
        line("--$boundary"); line("Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"")
        line("Content-Type: $contentType"); line(); out.write(bytes); line()
    }

    fun build(): ByteArray { line("--$boundary--"); return out.toByteArray() }
    val contentType: String get() = "multipart/form-data; boundary=$boundary"
}

/** Pure request/response code for the OpenAI-compatible `audio/transcriptions` endpoint (OpenRouter, Groq, OpenAI, ...). */
object WhisperCodec {
    /** Groq free tier: 25 MB per request (https://console.groq.com/docs/speech-to-text, checked 2026-11-16). */
    const val MAX_BYTES = 25L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun url(base: String) = base.trim().trimEnd('/') + "/audio/transcriptions"

    /** Fields: file (m4a = AAC in MPEG-4, MIME audio/mp4), model, language (ISO 639-1, optional), response_format=json, temperature=0. */
    fun body(model: String, language: String?, fileName: String, audio: ByteArray, boundary: String? = null): MultipartBody {
        val b = boundary?.let(::MultipartBody) ?: MultipartBody()
        b.file("file", fileName, "audio/mp4", audio)
        b.field("model", model)
        language?.trim()?.takeIf { it.isNotEmpty() }?.let { b.field("language", it) }
        b.field("response_format", "json")
        b.field("temperature", "0")
        return b
    }

    fun parse(code: Int, body: String): SttResult {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (code in 200..299) {
            val text = obj?.get("text")?.jsonPrimitive?.contentOrNull ?: return SttResult.Failure(SttError.Malformed("no text"))
            return SttResult.Ok(text.trim())
        }
        val detail = obj?.let(::errorMessage)?.take(160)
        return SttResult.Failure(
            when (code) {
                401, 403 -> SttError.Auth(code)
                413 -> SttError.TooLarge
                429 -> SttError.RateLimited
                else -> SttError.Http(code, detail)
            }
        )
    }

    private fun errorMessage(o: JsonObject): String? =
        (o["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            ?: runCatching { o["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

/**
 * Cloud transcriber over the phase 1 [HttpTransport] (no new dependency). No retries here: the capture keeps its audio and
 * the user retries; nothing about the request, the audio or the key is logged.
 */
class WhisperApiTranscriber(
    private val config: () -> SttConfig,
    private val transport: HttpTransport,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 90_000,
) : Transcriber {
    override suspend fun transcribe(audio: File, language: String?): SttResult {
        val cfg = config()
        val key = cfg.apiKey?.trim().orEmpty()
        if (key.isEmpty()) return SttResult.Failure(SttError.MissingKey)
        val base = cfg.baseUrl.trim()
        if (!(base.startsWith("https://") || base.startsWith("http://")) || runCatching { java.net.URI(base).host }.getOrNull().isNullOrEmpty())
            return SttResult.Failure(SttError.InvalidConfig("base URL must start with https://"))
        if (cfg.model.isBlank()) return SttResult.Failure(SttError.InvalidConfig("model is empty"))
        if (!audio.isFile) return SttResult.Failure(SttError.AudioMissing)
        if (audio.length() > WhisperCodec.MAX_BYTES) return SttResult.Failure(SttError.TooLarge)
        val body = WhisperCodec.body(cfg.model.trim(), language, audio.name, audio.readBytes())
        val request = HttpRequest(
            url = WhisperCodec.url(base),
            headers = mapOf("Authorization" to "Bearer $key", "Content-Type" to body.contentType, "Accept" to "application/json"),
            body = "", connectTimeoutMs = connectTimeoutMs, readTimeoutMs = readTimeoutMs, bytes = body.build(),
        )
        return try {
            val r = transport.post(request)
            WhisperCodec.parse(r.code, r.body)
        } catch (e: SocketTimeoutException) {
            SttResult.Failure(SttError.Network(timeout = true))
        } catch (e: ConnectFailedException) {
            SttResult.Failure(SttError.Network(timeout = e.cause is SocketTimeoutException))
        } catch (e: IOException) {
            SttResult.Failure(SttError.Network(timeout = false))
        }
    }
}
