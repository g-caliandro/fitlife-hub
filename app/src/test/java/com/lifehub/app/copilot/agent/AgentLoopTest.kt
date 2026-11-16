package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.llm.LlmClient
import com.lifehub.app.copilot.llm.LlmError
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.copilot.llm.LlmTool
import com.lifehub.app.copilot.llm.LlmToolChoice
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.repo.NewChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentLoopTest {
    private val f = CopilotFixture()

    @After fun tearDown() = f.close()

    private fun loop(llm: LlmClient, config: AgentConfig = AgentConfig()) = AgentLoop(llm, f.registry, f.chat, f.time, config)
    private fun thread() = runBlocking { f.chat.createThread("t", ChatOrigin.CHAT, f.now).id }

    /**
     * Phase 2 acceptance: "Quante proteine mi mancano oggi?" -> the model calls get_daily_summary and answers with the
     * numbers the app computed, which are the diary's numbers. The summary card is persisted with the same values.
     */
    @Test fun readToolTurnEndToEnd() { runBlocking {
        val llm = FakeLlmClient(tools(call("c1", "get_daily_summary", """{"date":"2026-11-16"}""")))
        // Second round: the "model" answers from the tool result it received (as a real model must).
        llm.fallback = {
            val toolMsg = llm.requests.last().messages.filterIsInstance<LlmMessage.Tool>().single()
            val left = Json.parseToJsonElement(toolMsg.content).jsonObject["remaining"]!!.jsonObject["protein_g"]!!.jsonPrimitive.int
            text("Ti mancano $left g di proteine oggi.")
        }
        val id = thread()
        val r = loop(llm).send(id, "Quante proteine mi mancano oggi?")

        val diary = f.food.observeNutrition(f.today, f.today).first().single().asMacroDay()
        val expectedLeft = f.settings.current().proteinTargetG - diary.proteinG
        assertEquals(TurnResult.Answered("Ti mancano $expectedLeft g di proteine oggi."), r)
        assertEquals(99, expectedLeft)

        // Request shape: system prompt (Italian, clock in the user's zone), user message, tool definitions.
        val first = llm.requests.first()
        val system = first.messages.first() as LlmMessage.System
        assertTrue(system.content.contains("italiano"))
        assertTrue(system.content, system.content.contains("2026-11-16") && system.content.contains("14:05") && system.content.contains("Europe/Rome"))
        assertEquals(LlmMessage.User("Quante proteine mi mancano oggi?"), first.messages[1])
        assertEquals(6, first.tools.size)

        // Persisted: user, assistant tool call, tool result with card, final answer.
        val stored = f.chat.messages(id)
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT), stored.map { it.role })
        assertEquals("get_daily_summary", stored[2].toolName)
        val card = CardCodec.decode(stored[2].cardType, stored[2].cardJson) as DailySummaryCard
        assertEquals(diary.proteinG, card.proteinG); assertEquals(diary.kcal, card.kcal); assertEquals(expectedLeft, card.proteinRemainingG)
    } }

    @Test fun invalidToolArgumentsGoBackToModelAsError() { runBlocking {
        val llm = FakeLlmClient(
            tools(call("c1", "get_daily_summary", """{"date":"oggi"}""")),
            tools(call("c2", "get_daily_summary", """{"date":"2026-11-16"}""")),
            text("Ok."),
        )
        val id = thread()
        val r = loop(llm).send(id, "Riepilogo di oggi")
        assertEquals(TurnResult.Answered("Ok."), r)
        val errorMsg = llm.requests[1].messages.filterIsInstance<LlmMessage.Tool>().single()
        assertEquals("c1", errorMsg.toolCallId)
        assertTrue(errorMsg.content, errorMsg.content.contains("\"error\"") && errorMsg.content.contains("ISO date"))
        // The error result is stored without a card; the corrected call produced one.
        val toolRows = f.chat.messages(id).filter { it.role == ChatRole.TOOL }
        assertEquals(listOf(null, CardCodec.DAILY_SUMMARY), toolRows.map { it.cardType })
    } }

    @Test fun unknownToolIsErrorNotCrash() { runBlocking {
        val llm = FakeLlmClient(tools(call("c1", "log_meal", """{"items":[]}""")), text("Non posso ancora registrare pasti."))
        val r = loop(llm).send(thread(), "Ho mangiato una mela")
        assertTrue(r is TurnResult.Answered)
        assertTrue(llm.requests[1].messages.filterIsInstance<LlmMessage.Tool>().single().content.contains("Unknown tool"))
    } }

    @Test fun iterationLimitStopsTheTurn() { runBlocking {
        val llm = FakeLlmClient()
        var n = 0
        llm.fallback = { tools(call("c${n++}", "get_nutrition_targets")) }
        val id = thread()
        val r = loop(llm, AgentConfig(maxToolIterations = 3)).send(id, "loop")
        assertEquals(TurnResult.Failed(TurnError.IterationLimit), r)
        assertEquals(4, llm.requests.size) // 3 tool rounds + the 4th request that asked for more tools
        assertEquals(3, f.chat.messages(id).count { it.role == ChatRole.TOOL })
    } }

    @Test fun llmErrorSurfacedWithoutCrashAndRetryContinues() { runBlocking {
        val llm = FakeLlmClient(LlmResult.Failure(LlmError.Network(timeout = false)))
        val agent = loop(llm)
        val id = thread()
        val r = agent.send(id, "Ciao")
        val err = (r as TurnResult.Failed).error as TurnError.Llm
        assertEquals(LlmError.Network(timeout = false), err.error)
        assertEquals(listOf(ChatRole.USER), f.chat.messages(id).map { it.role }) // user message kept for Retry

        llm.fallback = { text("Ciao!") }
        assertEquals(TurnResult.Answered("Ciao!"), agent.run(id))
        assertEquals(LlmMessage.User("Ciao"), llm.requests.last().messages[1])
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), f.chat.messages(id).map { it.role })
    } }

    @Test fun missingKeyAndMalformedReplies() { runBlocking {
        val r = loop(FakeLlmClient(LlmResult.Failure(LlmError.MissingKey))).send(thread(), "x")
        assertEquals(LlmError.MissingKey, ((r as TurnResult.Failed).error as TurnError.Llm).error)
        val empty = loop(FakeLlmClient(text("  "))).send(thread(), "x")
        assertTrue(((empty as TurnResult.Failed).error as TurnError.Llm).error is LlmError.Malformed)
    } }

    @Test fun turnTimeout() { runBlocking {
        val slow = object : LlmClient {
            override suspend fun chat(messages: List<LlmMessage>, tools: List<LlmTool>, toolChoice: LlmToolChoice, maxTokens: Int?): LlmResult {
                delay(10_000); return text("late")
            }
        }
        val r = loop(slow, AgentConfig(turnTimeoutMs = 50)).send(thread(), "x")
        assertEquals(TurnResult.Failed(TurnError.Timeout), r)
    } }

    @Test fun historyWindowIsCappedAndDropsOldToolResults() { runBlocking {
        val id = thread()
        // 15 old turns, each: user, assistant tool call, tool result, assistant answer.
        repeat(15) { i ->
            f.chat.append(id, NewChatMessage(ChatRole.USER, "domanda $i"), f.now)
            f.chat.append(id, NewChatMessage(ChatRole.ASSISTANT, null, toolCallsJson = ToolCallsCodec.encode(listOf(call("old$i", "get_nutrition_targets")))), f.now)
            f.chat.append(id, NewChatMessage(ChatRole.TOOL, """{"kcal":2200}""", toolCallId = "old$i", toolName = "get_nutrition_targets"), f.now)
            f.chat.append(id, NewChatMessage(ChatRole.ASSISTANT, "risposta $i"), f.now)
        }
        val llm = FakeLlmClient(text("ok"))
        loop(llm, AgentConfig(historyMaxMessages = 10, historyToolTurns = 1)).send(id, "nuova domanda")
        val sent = llm.requests.single().messages
        val history = sent.drop(1) // system prompt
        assertTrue("cap: ${history.size}", history.size <= 10)
        assertTrue(history.first() is LlmMessage.User) // never starts with an orphan tool result
        assertEquals(LlmMessage.User("nuova domanda"), history.last())
        // Only the previous turn (14) keeps its tool call/result; older turns are text only.
        val toolIds = history.filterIsInstance<LlmMessage.Tool>().map { it.toolCallId }
        assertEquals(listOf("old14"), toolIds)
        assertTrue(history.contains(LlmMessage.Assistant("risposta 13")))
        assertFalse(history.contains(LlmMessage.User("domanda 0")))
    } }

    @Test fun historyDropsInterruptedToolCalls() {
        val calls = ToolCallsCodec.encode(listOf(call("a", "get_goals"), call("b", "get_goals")))
        val msgs = listOf(
            com.lifehub.app.data.model.ChatMessage("1", "t", 0, ChatRole.USER, "ciao", createdAt = f.now),
            com.lifehub.app.data.model.ChatMessage("2", "t", 1, ChatRole.ASSISTANT, null, toolCallsJson = calls, createdAt = f.now),
            com.lifehub.app.data.model.ChatMessage("3", "t", 2, ChatRole.TOOL, "{}", toolCallId = "a", toolName = "get_goals", createdAt = f.now),
        )
        assertEquals(listOf(LlmMessage.User("ciao")), HistoryWindow.build(msgs, 20, 1))
    }

    @Test fun toolFilterAndCaptureIdAreHonoured() { runBlocking {
        val llm = FakeLlmClient(tools(call("c1", "get_goals")), text("ok"))
        val id = thread()
        loop(llm).send(id, "x", TurnOptions(toolFilter = { it.name != "get_goals" }, captureId = "cap-1"))
        assertFalse(llm.requests.first().tools.any { it.name == "get_goals" })
        assertTrue(llm.requests[1].messages.filterIsInstance<LlmMessage.Tool>().single().content.contains("Unknown tool"))
        assertTrue(f.chat.messages(id).all { it.captureId == "cap-1" })
    } }

    @Test fun contextProvidersAddSystemSections() { runBlocking {
        val llm = FakeLlmClient(text("ok"))
        AgentLoop(llm, f.registry, f.chat, f.time, contextProviders = listOf(ContextProvider { "Memoria: niente lattosio." }))
            .send(thread(), "x", TurnOptions(extraContext = listOf("Prodotto allegato: yogurt.")))
        val system = (llm.requests.single().messages.first() as LlmMessage.System).content
        assertTrue(system.contains("Memoria: niente lattosio.") && system.contains("Prodotto allegato: yogurt."))
        assertNotNull(system)
    } }
}
