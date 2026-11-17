package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.memory.MemoryContext
import com.lifehub.app.copilot.memory.MemorySaveResult
import com.lifehub.app.copilot.memory.UserMemoryStore
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.UserMemory
import com.lifehub.app.data.repo.BackupRepositoryImpl
import com.lifehub.app.export.JsonCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZonedDateTime

/**
 * User memory (design doc §10): `save_user_memory` writes immediately in the chat and is never offered to a quick capture;
 * the memory goes into every request, capped; it is in the export (owner decision 7). Fake model, no network.
 */
@RunWith(RobolectricTestRunner::class)
class UserMemoryTest {
    private val f = CopilotFixture()
    @After fun tearDown() = f.close()

    private fun system(llm: StepLlmClient, i: Int) = (llm.requests[i].first() as LlmMessage.System).content

    @Test fun savedInChatAndInjectedIntoTheNextRequests() = runBlocking {
        val thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        val llm = StepLlmClient(
            { tools(call("c1", "save_user_memory", """{"key":"disliked_foods","value":"funghi, tonno"}""")) },
            { text("Ok, me lo ricordo.") },
            { text("Certo.") },
        )
        val p = f.processor(llm)
        p.submitText(thread, "Ricordati che non mi piacciono funghi e tonno")
        assertTrue("save_user_memory" in llm.toolNames.first())
        assertEquals("funghi, tonno", f.memory.all().single { it.key == "disliked_foods" }.value)
        assertEquals(f.now, f.memory.all().single().updatedAt)
        assertFalse("nothing in memory yet on the first request", system(llm, 0).contains("Memoria utente"))

        p.submitText(thread, "Cosa preparo stasera?")
        assertTrue(system(llm, 2).contains("Memoria utente"))
        assertTrue(system(llm, 2).contains("- disliked_foods: funghi, tonno (2026-11-16)"))
    }

    @Test fun quickCaptureNeverGetsSaveUserMemory() = runBlocking {
        val thread = f.chat.createThread("q", ChatOrigin.QUICK, f.now).id
        val llm = StepLlmClient(
            // Even if the model tries, the tool is not offered and the call fails as an unknown tool.
            { tools(call("c1", "save_user_memory", """{"key":"goals","value":"muscle up"}""")) },
            { text("Non posso salvarlo da qui.") },
        )
        f.processor(llm).submitText(thread, "Ricordati che voglio fare il muscle up", origin = ChatOrigin.QUICK)
        assertTrue(llm.toolNames.all { "save_user_memory" !in it && "log_meal" in it })
        assertTrue(f.memory.all().isEmpty())
        val result = f.chat.messages(thread).single { it.role == ChatRole.TOOL }.text!!
        assertTrue(result, result.contains("Unknown tool"))
    }

    @Test fun memoryIsCappedInTheStoreAndInThePrompt() = runBlocking {
        val long = "x".repeat(UserMemoryStore.MAX_VALUE)
        repeat(UserMemoryStore.MAX_ENTRIES) { f.memory.save("fact_$it", long, f.now) }
        assertTrue(f.memory.save("one_more", "y", f.now) is MemorySaveResult.Invalid)
        assertTrue("updating an existing key is still allowed", f.memory.save("fact_0", "short", f.now) is MemorySaveResult.Saved)
        assertTrue(f.memory.save("k", "z".repeat(UserMemoryStore.MAX_VALUE + 1), f.now) is MemorySaveResult.Invalid)
        assertTrue(f.memory.save("  ", "v", f.now) is MemorySaveResult.Invalid)
        assertEquals("cibi_non_graditi", UserMemoryStore.normalizeKey("Cibi non graditi"))

        val section = MemoryContext(f.memory).section(ToolContext(f.now, f.zone))!!
        assertTrue(section.length <= UserMemoryStore.MAX_PROMPT_CHARS)
        assertTrue(section.contains("- fact_0: short"))
        assertTrue(section.contains("omessi"))
        // The whole system prompt sent to the model carries the capped section.
        val llm = StepLlmClient({ text("ok") })
        f.agent(llm).send(f.chat.createThread("t", ChatOrigin.CHAT, f.now).id, "ciao")
        assertTrue((llm.requests.single().first() as LlmMessage.System).content.contains(section))
        assertNull(MemoryContext.render(emptyList()))
    }

    @Test fun memoryIsExportedAndImported() = runBlocking {
        f.memory.save("equipment", "sbarra e anelli", f.now)
        val backup = BackupRepositoryImpl(f.db, f.settings)
        val text = JsonCodec.encode(backup.snapshot(), ZonedDateTime.now(f.zone), "x")
        assertTrue(text.contains("\"userMemory\"")); assertTrue(text.contains("sbarra e anelli"))

        f.memory.delete("equipment")
        backup.replaceAll(JsonCodec.decode(text))
        assertEquals(listOf(UserMemory("equipment", "sbarra e anelli", f.now)), f.memory.all())

        // A file from before schemaVersion 9 has no userMemory: the phone keeps its own.
        f.memory.save("goals", "muscle up", Instant.EPOCH)
        val tree = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val old = kotlinx.serialization.json.JsonObject(tree - "userMemory" + ("schemaVersion" to kotlinx.serialization.json.JsonPrimitive(8))).toString()
        backup.replaceAll(JsonCodec.decode(old))
        assertEquals(setOf("equipment", "goals"), f.memory.all().map { it.key }.toSet())
    }
}
