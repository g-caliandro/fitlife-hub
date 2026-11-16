package com.lifehub.app.copilot.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Redacts headers and body: a request must never leak its key or content through `toString` / logs. */
class HttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    /** Binary body (e.g. multipart audio upload); when set it is sent instead of [body]. */
    val bytes: ByteArray? = null,
) {
    override fun toString() = "HttpRequest(POST $url, <redacted>)"
}

class HttpResponse(val code: Int, val body: String) {
    override fun toString() = "HttpResponse($code)"
}

/** The seam that lets [OpenAiCompatibleLlmClient] (and the phase 4 transcriber) be tested without a network. Throws [IOException] on transport failure. */
interface HttpTransport {
    suspend fun post(request: HttpRequest): HttpResponse
}

/** Thrown by a transport for a connection that failed before the request was sent (safe to retry). */
class ConnectFailedException(cause: Throwable) : IOException("connect failed", cause)

class UrlConnectionTransport : HttpTransport {
    override suspend fun post(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val c = try { URL(request.url).openConnection() as HttpURLConnection } catch (e: IOException) { throw ConnectFailedException(e) }
        try {
            c.requestMethod = "POST"
            c.connectTimeout = request.connectTimeoutMs
            c.readTimeout = request.readTimeoutMs
            c.doOutput = true
            request.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            try { c.connect() } catch (e: java.net.ConnectException) { throw ConnectFailedException(e) }
            catch (e: java.net.UnknownHostException) { throw ConnectFailedException(e) }
            c.outputStream.use { it.write(request.bytes ?: request.body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally { c.disconnect() }
    }
}
