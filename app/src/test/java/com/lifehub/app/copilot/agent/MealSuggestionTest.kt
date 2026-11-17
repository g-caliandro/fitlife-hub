package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.copilot.llm.LlmToolCall
import com.lifehub.app.copilot.tools.LogAsMealResult
import com.lifehub.app.copilot.tools.dailySummary
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.feature.copilot.coverageLines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Use case 7 "Cosa preparo per cena?": the model reads the remaining amounts and proposes a dish with resolved refs and
 * grams; the app computes the macros. "Log as meal" writes exactly what the card shows through the capture path, and Undo
 * makes the DB identical again. Disliked foods from user memory are refused. Fake model, no network.
 */
@RunWith(RobolectricTestRunner::class)
class MealSuggestionTest {
    private val f = CopilotFixture()
    @Before fun seedBase() = runBlocking { f.baseFoods.ensureSeeded() }
    @After fun tearDown() = f.close()

    private val zucchine = "base:sr-169291"
    private val suggestion = """{"title":"Pollo con riso e zucchine","items":[{"ref":"food:f-chicken","grams":180},{"ref":"food:f-rice","grams":70},{"ref":"$zucchine","grams":200}]}"""

    private suspend fun suggestInChat(): Pair<String, MealSuggestionCard> {
        val thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        val llm = StepLlmClient(
            { tools(call("c1", "get_daily_summary")) },
            { tools(call("c2", "suggest_meal", suggestion)) },
            { text("Ti propongo pollo con riso e zucchine: copre buona parte delle proteine che ti mancano.") },
        )
        f.processor(llm).submitText(thread, "Cosa preparo per cena?")
        val card = f.chat.messages(thread).mapNotNull { CardCodec.decode(it.cardType, it.cardJson) }.filterIsInstance<MealSuggestionCard>().single()
        return thread to card
    }

    @Test fun cardMacrosAreComputedByTheAppAndEqualWhatLogAsMealWrites() = runBlocking {
        val before = dumpTables(f.db)
        val (thread, card) = suggestInChat()
        assertEquals("a suggestion writes nothing", before, dumpTables(f.db))

        // Values computed by the app from the values per 100 g (chicken 110 kcal / 23.3 P per 100 g).
        val chicken = card.items[0]
        assertEquals(180.0, chicken.grams, 0.0); assertEquals(110.0 * 1.8, chicken.kcal, 1e-9); assertEquals(23.3 * 1.8, chicken.proteinG, 1e-9)
        assertEquals(card.items.sumOf { it.kcal }, card.totalKcal, 0.0)
        assertEquals("base_table", card.items[2].source)
        // Remaining before the meal = the diary's numbers (same as get_daily_summary).
        val summary = dailySummary(f.today, f.food.observeNutrition(f.today, f.today).first().firstOrNull(), f.settings.current())
        assertEquals(summary.kcalRemaining, card.kcalRemaining); assertEquals(summary.proteinRemainingG, card.proteinRemainingG)
        assertNull(card.carbsRemainingG)   // no carbs target in the fixture
        assertTrue(coverageLines(card).any { it.contains("protein") && it.contains("${summary.proteinRemainingG} g protein left") })

        // Log as meal: written now, through a capture of this thread, with the exact card values.
        val r = f.suggestions.logAsMeal(card, thread) as LogAsMealResult.Logged
        assertEquals(card.captureId, r.captureId)
        assertEquals(CaptureStatus.DONE, f.captures.get(card.captureId)!!.status)
        assertEquals(thread, f.captures.get(card.captureId)!!.threadId)
        val entries = f.db.foodEntryDao().byCapture(card.captureId).sortedBy { it.name }
        val items = card.items.sortedBy { it.name }
        assertEquals(items.map { it.name }, entries.map { it.name })
        items.zip(entries).forEach { (i, e) ->
            assertEquals(i.grams, e.grams, 0.0); assertEquals(i.kcal, e.kcal, 0.0); assertEquals(i.proteinG, e.proteinG, 0.0)
            assertEquals(i.carbsG, e.carbsG, 0.0); assertEquals(i.fatG, e.fatG, 0.0)
        }
        assertEquals(card.totalKcal, entries.sumOf { it.kcal }, 1e-9)
        assertEquals(EntrySource.BASE_TABLE, entries.single { it.name == "Zucchine" }.source)

        // A second tap logs nothing more.
        assertEquals(LogAsMealResult.AlreadyLogged(card.captureId, CaptureStatus.DONE), f.suggestions.logAsMeal(card, thread))
        assertEquals(3, f.db.foodEntryDao().byCapture(card.captureId).size)

        // Undo (same path as the Meal logged card): DB identical to before, base food copy and lastUsedAt included.
        f.processor(StepLlmClient()).undo(card.captureId)
        assertEquals(before, dumpTables(f.db))
        // Tapping again after Undo never re-logs it.
        assertTrue(f.suggestions.logAsMeal(card, thread) is LogAsMealResult.AlreadyLogged)
        assertEquals(before, dumpTables(f.db))
    }

    @Test fun dislikedFoodsFromMemoryAndEstimatesAreRefused() = runBlocking {
        f.memory.save("disliked_foods", "zucchine, funghi", f.now)
        val ctx = ToolContext(f.now, f.zone)
        val r = f.fullRegistry.execute(LlmToolCall("x", "suggest_meal", suggestion), ctx)
        val msg = (r.outcome as ToolOutcome.Error).message
        assertTrue(msg, msg.contains("disliked") && msg.contains("Zucchine"))
        val est = f.fullRegistry.execute(LlmToolCall("x", "suggest_meal", """{"title":"x","items":[{"ref":"estimate:p1:0","grams":100}]}"""), ctx)
        assertTrue((est.outcome as ToolOutcome.Error).message.contains("estimates"))
        val unknown = f.fullRegistry.execute(LlmToolCall("x", "suggest_meal", """{"title":"x","items":[{"ref":"food:nope","grams":100}]}"""), ctx)
        assertTrue((unknown.outcome as ToolOutcome.Error).message.contains("unknown"))
        // Without the disliked food it works, and the result tells the model it is not logged.
        val ok = f.fullRegistry.execute(LlmToolCall("x", "suggest_meal", """{"title":"Pollo e riso","items":[{"ref":"food:f-chicken","grams":150},{"ref":"food:f-rice","grams":80}]}"""), ctx)
        val res = (ok.outcome as ToolOutcome.Ok).result
        assertEquals("shown_not_logged", res.getValue("status").jsonPrimitive.content)
        assertEquals((110 * 1.5 + 360 * 0.8).toInt(), res.getValue("total").jsonObject.getValue("kcal").jsonPrimitive.int)
    }
}
