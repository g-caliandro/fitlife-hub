package com.lifehub.app.copilot.stt

import com.lifehub.app.copilot.llm.ConnectFailedException
import com.lifehub.app.copilot.llm.HttpRequest
import com.lifehub.app.copilot.llm.HttpResponse
import com.lifehub.app.copilot.llm.HttpTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

private class FakeTransport(private val step: Any) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun post(request: HttpRequest): HttpResponse {
        requests += request
        if (step is Throwable) throw step
        return step as HttpResponse
    }
}

/** Whisper-compatible transcriber over the fake transport: multipart request building and error parsing. No network. */
class TranscriberTest {
    @get:Rule val tmp = TemporaryFolder()
    private val KEY = "gsk_test-SECRET-0123456789"
    private val audioBytes = byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x4d, 0x34, 0x41, 0x20, -1, 7)

    private fun audio(): File = File(tmp.root, "capture.m4a").apply { writeBytes(audioBytes) }
    private fun cfg(base: String = "https://api.groq.com/openai/v1/", key: String? = KEY, model: String = "whisper-large-v3-turbo") = { SttConfig(base, model, key) }
    private fun run(t: HttpTransport, c: () -> SttConfig = cfg(), file: File = audio(), lang: String? = "it") =
        runBlocking { WhisperApiTranscriber(c, t).transcribe(file, lang) }
    private fun err(r: SttResult) = (r as SttResult.Failure).error

    @Test fun buildsMultipartRequestForGroq() {
        val t = FakeTransport(HttpResponse(200, """{"text":" Ho mangiato 90 grammi di riso. ","x_groq":{"id":"req_1"}}"""))
        val r = run(t)
        assertEquals(SttResult.Ok("Ho mangiato 90 grammi di riso."), r)
        val req = t.requests.single()
        assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", req.url)
        assertEquals("Bearer $KEY", req.headers["Authorization"])
        val ct = req.headers["Content-Type"]!!
        assertTrue(ct, ct.startsWith("multipart/form-data; boundary="))
        val boundary = ct.substringAfter("boundary=")
        val body = req.bytes!!
        val text = String(body, Charsets.ISO_8859_1)
        fun field(name: String) = Regex("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n([^\r]*)\r\n").find(text)?.groupValues?.get(1)
        assertEquals("whisper-large-v3-turbo", field("model"))
        assertEquals("it", field("language"))
        assertEquals("json", field("response_format"))
        assertEquals("0", field("temperature"))
        assertTrue(text.contains("Content-Disposition: form-data; name=\"file\"; filename=\"capture.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n"))
        // The audio bytes are sent unchanged, and the body ends with the closing boundary.
        val start = text.indexOf("audio/mp4\r\n\r\n") + "audio/mp4\r\n\r\n".length
        assertArrayEquals(audioBytes, body.copyOfRange(start, start + audioBytes.size))
        assertTrue(text.endsWith("--$boundary--\r\n"))
        // Nothing secret in toString (logs).
        assertFalse(req.toString().contains(KEY))
        assertFalse(cfg()().toString().contains(KEY))
    }

    @Test fun languageIsOptional() {
        val t = FakeTransport(HttpResponse(200, """{"text":"ok"}"""))
        run(t, lang = null)
        assertFalse(String(t.requests.single().bytes!!, Charsets.ISO_8859_1).contains("name=\"language\""))
    }

    @Test fun configProblemsFailBeforeAnyRequest() {
        val t = FakeTransport(HttpResponse(200, "{}"))
        assertEquals(SttError.MissingKey, err(run(t, cfg(key = null))))
        assertEquals(SttError.MissingKey, err(run(t, cfg(key = "  "))))
        assertTrue(err(run(t, cfg(base = "api.groq.com"))) is SttError.InvalidConfig)
        assertTrue(err(run(t, cfg(model = " "))) is SttError.InvalidConfig)
        assertEquals(SttError.AudioMissing, err(run(t, file = File(tmp.root, "gone.m4a"))))
        assertTrue(t.requests.isEmpty())
    }

    @Test fun httpErrorsAreTyped() {
        fun code(c: Int, body: String = """{"error":{"message":"Invalid API Key","type":"invalid_request_error","code":"invalid_api_key"}}""") =
            err(run(FakeTransport(HttpResponse(c, body))))
        assertEquals(SttError.Auth(401), code(401))
        assertEquals(SttError.Auth(403), code(403))
        assertEquals(SttError.TooLarge, code(413))
        assertEquals(SttError.RateLimited, code(429))
        assertEquals(SttError.Http(400, "file must be one of the supported formats"), code(400, """{"error":{"message":"file must be one of the supported formats"}}"""))
        assertEquals(SttError.Http(502, null), code(502, "<html>bad gateway</html>"))
        assertTrue(code(502, "").transient)
        assertFalse(SttError.Auth(401).transient)
        assertTrue(err(run(FakeTransport(HttpResponse(200, """{"segments":[]}""")))) is SttError.Malformed)
        assertTrue(err(run(FakeTransport(HttpResponse(200, "not json")))) is SttError.Malformed)
        // The error message never contains the key.
        assertFalse(code(401).message.contains(KEY))
    }

    @Test fun networkFailures() {
        assertEquals(SttError.Network(timeout = true), err(run(FakeTransport(SocketTimeoutException()))))
        assertEquals(SttError.Network(timeout = false), err(run(FakeTransport(ConnectFailedException(java.net.UnknownHostException())))))
        assertEquals(SttError.Network(timeout = false), err(run(FakeTransport(IOException("reset")))))
        assertTrue(SttError.Network(false).transient)
    }

    @Test fun silenceDetectorStopsAfterAPauseOrNoSpeech() {
        val d = SilenceDetector(threshold = 1000, silenceMs = 3000, noSpeechMs = 8000, maxMs = 60_000)
        assertFalse(d.onSample(5000, 0)); assertFalse(d.onSample(200, 2000)); assertFalse(d.onSample(4000, 2500))
        assertFalse(d.onSample(100, 5000)); assertTrue(d.onSample(100, 5500))
        val quiet = SilenceDetector(threshold = 1000, silenceMs = 3000, noSpeechMs = 8000)
        assertFalse(quiet.onSample(10, 0)); assertFalse(quiet.onSample(10, 7800)); assertTrue(quiet.onSample(10, 8000))
        assertFalse(quiet.heardSpeech)
        val long = SilenceDetector(threshold = 1000, maxMs = 10_000)
        (0..9).forEach { assertFalse(long.onSample(5000, it * 1000L)) }
        assertTrue(long.onSample(5000, 10_000))
    }
}
