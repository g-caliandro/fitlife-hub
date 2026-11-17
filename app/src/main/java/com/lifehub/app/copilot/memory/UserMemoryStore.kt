package com.lifehub.app.copilot.memory

import com.lifehub.app.copilot.agent.ContextProvider
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.food.TextMatch
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.UserMemory
import kotlinx.coroutines.flow.Flow
import java.time.Instant

sealed interface MemorySaveResult {
    data class Saved(val entry: UserMemory, val replaced: String?) : MemorySaveResult
    data class Invalid(val message: String) : MemorySaveResult
}

/**
 * User memory (design doc §10): short key/value facts with their update date, injected into every request, so the
 * store is capped ([MAX_ENTRIES] entries, [MAX_VALUE] characters per value). Written by `save_user_memory` (chat only) and
 * by hand from Copilot settings > Memory; removed only from that screen.
 */
interface UserMemoryStore {
    fun observe(): Flow<List<UserMemory>>
    suspend fun all(): List<UserMemory>
    suspend fun save(key: String, value: String, now: Instant): MemorySaveResult
    suspend fun delete(key: String)

    companion object {
        const val MAX_ENTRIES = 30
        const val MAX_KEY = 40
        const val MAX_VALUE = 200
        /** Hard cap of the memory section in the system prompt. */
        const val MAX_PROMPT_CHARS = 1500
        /** Keys whose values list foods the user does not like (comma separated); `suggest_meal` refuses them. */
        val DISLIKED_KEYS = setOf("disliked_foods", "cibi_non_graditi", "alimenti_non_graditi")

        /** "Cibi non graditi" -> "cibi_non_graditi"; null when nothing usable is left. */
        fun normalizeKey(raw: String): String? {
            val k = java.text.Normalizer.normalize(raw.trim().lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "_").trim('_')
            return k.take(MAX_KEY).trimEnd('_').takeIf { it.isNotEmpty() }
        }

        /** Disliked foods listed in [entries] (values of [DISLIKED_KEYS], split on commas / semicolons). */
        fun dislikedFoods(entries: List<UserMemory>): List<String> =
            entries.filter { it.key in DISLIKED_KEYS }.flatMap { it.value.split(',', ';', '\n') }.map { it.trim() }.filter { it.isNotEmpty() }

        /** The disliked food [name] matches (every word of the disliked food appears in the name), or null. */
        fun dislikedMatch(name: String, disliked: List<String>): String? {
            val words = TextMatch.tokens(name)
            return disliked.firstOrNull { d -> TextMatch.tokens(d).let { it.isNotEmpty() && words.containsAll(it) } }
        }
    }
}

class UserMemoryStoreImpl(db: LifeHubDatabase) : UserMemoryStore {
    private val dao = db.userMemoryDao()
    override fun observe() = dao.observeAll()
    override suspend fun all() = dao.all()

    override suspend fun save(key: String, value: String, now: Instant): MemorySaveResult {
        val k = UserMemoryStore.normalizeKey(key) ?: return MemorySaveResult.Invalid("The key is empty.")
        val v = value.trim().replace(Regex("\\s+"), " ")
        if (v.isEmpty()) return MemorySaveResult.Invalid("The value is empty.")
        if (v.length > UserMemoryStore.MAX_VALUE) return MemorySaveResult.Invalid("The value is longer than ${UserMemoryStore.MAX_VALUE} characters: keep it short.")
        val old = dao.get(k)
        if (old == null && dao.all().size >= UserMemoryStore.MAX_ENTRIES)
            return MemorySaveResult.Invalid("The memory is full (${UserMemoryStore.MAX_ENTRIES} facts). Update an existing key instead; the user can delete facts in Copilot settings > Memory.")
        val e = UserMemory(k, v, now)
        dao.upsert(e)
        return MemorySaveResult.Saved(e, old?.value)
    }

    override suspend fun delete(key: String) = dao.delete(key)
}

/** System-prompt section with the user memory (design doc §10), at most [UserMemoryStore.MAX_PROMPT_CHARS] characters. */
class MemoryContext(private val store: UserMemoryStore) : ContextProvider {
    override suspend fun section(ctx: ToolContext): String? = render(store.all())

    companion object {
        fun render(entries: List<UserMemory>, maxChars: Int = UserMemoryStore.MAX_PROMPT_CHARS): String? {
            if (entries.isEmpty()) return null
            val head = "Memoria utente (fatti stabili salvati, con data di aggiornamento; rispettali, es. non proporre alimenti non graditi):"
            val sb = StringBuilder(head)
            var omitted = 0
            entries.sortedBy { it.key }.forEach { e ->
                val line = "\n- ${e.key}: ${e.value} (${e.updatedAt.toString().take(10)})"
                // 50 characters stay free for the "omitted" line, so the section never exceeds maxChars.
                if (omitted == 0 && sb.length + line.length <= maxChars - 50) sb.append(line) else omitted++
            }
            if (omitted > 0) sb.append("\n(altri $omitted fatti omessi per brevità)")
            return sb.toString()
        }
    }
}
