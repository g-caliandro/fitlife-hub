package com.lifehub.app.copilot.debug

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant

/**
 * Debug trace of the copilot, one JSON object per line (JSONL), so a real session can be handed over and evaluated:
 * per turn the system prompt, tool names and history sent; per model call the reply, tool calls, token usage (cached
 * tokens, cost) and latency; per tool call the arguments and the result; and how the turn ended.
 *
 * Lives in `noBackupFilesDir` (like the keys: never backed up) and contains the food diary the copilot saw, but never an
 * API key (requests are logged without headers). Capped: past [maxBytes] the file rotates to `<name>.1` (one old file kept).
 * Logging never throws and never blocks the turn on a failure.
 */
class CopilotDebugLog(private val file: File, private val maxBytes: Long = 4L * 1024 * 1024, private val now: () -> Instant = Instant::now) {
    private val old get() = File(file.parentFile, file.name + ".1")

    @Synchronized
    fun record(type: String, fields: JsonObjectBuilder.() -> Unit) {
        runCatching {
            file.parentFile?.mkdirs()
            if (file.length() > maxBytes) { old.delete(); file.renameTo(old) }
            val line = buildJsonObject { put("t", type); put("ts", now().toString()); fields() }
            file.appendText(line.toString() + "\n", Charsets.UTF_8)
        }
    }

    @Synchronized
    fun sizeBytes(): Long = (if (file.isFile) file.length() else 0L) + (if (old.isFile) old.length() else 0L)

    @Synchronized
    fun clear() { runCatching { file.delete(); old.delete() } }

    /** Old + current file as one file at [target] (oldest first); false when there is nothing to export. */
    @Synchronized
    fun exportTo(target: File): Boolean = runCatching {
        if (sizeBytes() == 0L) return false
        target.outputStream().use { out -> listOf(old, file).filter { it.isFile }.forEach { f -> f.inputStream().use { it.copyTo(out) } } }
        true
    }.getOrDefault(false)

    companion object {
        /** Long texts (tool results, prompts) are clipped so one line stays readable; the full data is in the chat DB. */
        fun clip(s: String?, max: Int = 4000): String? = s?.let { if (it.length <= max) it else it.take(max) + "…[+${it.length - max} chars]" }
        fun obj(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)
    }
}
