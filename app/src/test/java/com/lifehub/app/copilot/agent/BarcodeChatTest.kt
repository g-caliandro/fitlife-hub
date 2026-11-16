package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.copilot.llm.LlmError
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.off.OffProduct
import com.lifehub.app.data.repo.ProductLookup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phase 5: a product scanned in the chat composer is attached to the message and its data enters the turn's context;
 * "ne ho mangiati 30 grammi" then logs it. Also the `get_product_by_barcode` tool. Fake model and fake Open Food Facts.
 */
@RunWith(RobolectricTestRunner::class)
class BarcodeChatTest {
    private val f = CopilotFixture(seed = false)
    private lateinit var thread: String
    private val bar = "8001505005707"
    private val nocciolata = OffProduct(bar, "Nocciolata", "Rigoni", 539.0, 6.3, 57.5, 30.9, packageG = 350.0, servingG = 15.0)

    @Before fun setUp() = runBlocking {
        f.baseFoods.ensureSeeded()
        thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
    }
    @After fun tearDown() = f.close()

    private fun system(m: List<LlmMessage>) = (m.first() as LlmMessage.System).content
    private fun lastResult(m: List<LlmMessage>): JsonObject = Json.parseToJsonElement((m.last() as LlmMessage.Tool).content).jsonObject
    private fun entries(): List<FoodEntry> = runBlocking { f.food.observeEntries(f.today.minusDays(30), f.today).first() }
    private fun cards(): List<ChatCard> = runBlocking { f.chat.messages(thread).mapNotNull { CardCodec.decode(it.cardType, it.cardJson) } }

    /** What the composer does after the scanner returned: cache the product, remember its food id. */
    private suspend fun scan(p: OffProduct = nocciolata): String {
        f.offBarcodes.products = mapOf(p.code to p)
        return f.barcodes.cache((f.barcodes.findProduct(p.code) as ProductLookup.Found).product).id
    }

    @Test fun attachedProductEntersTheContextAndThirtyGramsLogsItWithItsMacros() = runBlocking {
        val foodId = scan()
        val before = dumpTables(f.db)
        val llm = StepLlmClient(
            { m ->
                val s = system(m)
                assertTrue(s, s.contains("prodotto allegato") && s.contains("Nocciolata") && s.contains("off:$bar"))
                assertTrue("package weight and serving are known", s.contains("package 350") && s.contains("serving 15"))
                tools(call("c1", "log_meal", """{"items":[{"ref":"off:$bar","grams":30}]}"""))
            },
            { text("Registrati 30 g di Nocciolata.") },
        )
        val run = f.processor(llm).submitText(thread, "ne ho mangiati 30 grammi", attachedFoodId = foodId)

        assertEquals(TurnResult.Answered("Registrati 30 g di Nocciolata."), run.turn)
        assertEquals(foodId, f.captures.get(run.captureId)!!.attachedFoodId)
        val e = entries().single()
        assertEquals(30.0, e.grams, 0.0); assertEquals(foodId, e.foodId)
        assertEquals(539.0 * 0.3, e.kcal, 1e-9); assertEquals(6.3 * 0.3, e.proteinG, 1e-9)
        assertEquals(57.5 * 0.3, e.carbsG, 1e-9); assertEquals(30.9 * 0.3, e.fatG, 1e-9)
        assertEquals(EntrySource.OPEN_FOOD_FACTS, e.source)
        assertEquals("the scanned product is the only food", 1, f.db.foodDao().getAll().size)
        val card = cards().filterIsInstance<MealLoggedCard>().single()
        assertEquals(e.kcal, card.items.single().kcal, 0.0)
        assertFalse(card.items.single().incomplete)

        f.processor(StepLlmClient()).undo(run.captureId)
        assertEquals("Undo restores the DB as it was after the scan", before, dumpTables(f.db))
    }

    @Test fun halfAPackOfTheAttachedProductUsesItsPackageWeight() = runBlocking {
        val foodId = scan()
        val llm = StepLlmClient(
            { tools(call("c1", "log_meal", """{"items":[{"ref":"off:$bar","servings":0.5,"portion":"package"}]}""")) },
            { text("Ok.") },
        )
        f.processor(llm).submitText(thread, "ne ho mangiata mezza confezione", attachedFoodId = foodId)
        val e = entries().single()
        assertEquals(175.0, e.grams, 0.0); assertEquals("package", e.portionLabel); assertEquals(0.5, e.servings!!, 0.0)
    }

    @Test fun theAttachmentSurvivesARetryAndAnAppRestart() = runBlocking {
        val foodId = scan()
        val failing = StepLlmClient({ LlmResult.Failure(LlmError.Network(timeout = false)) })
        val run = f.processor(failing).submitText(thread, "ne ho mangiati 30 grammi", attachedFoodId = foodId)
        assertEquals(CaptureStatus.FAILED, f.captures.get(run.captureId)!!.status)

        // New processor instances (as after a restart): the context is rebuilt from the stored capture, nothing in memory.
        val llm = StepLlmClient(
            { m -> assertTrue(system(m).contains("off:$bar")); tools(call("c1", "log_meal", """{"items":[{"ref":"off:$bar","grams":30}]}""")) },
            { text("Ok.") },
        )
        f.processor(llm).retry(run.captureId)
        assertEquals(CaptureStatus.DONE, f.captures.get(run.captureId)!!.status)
        assertEquals(30.0, entries().single().grams, 0.0)
    }

    @Test fun attachedProductWithMissingMacrosIsLoggedFlaggedIncomplete() = runBlocking {
        val foodId = scan(nocciolata.copy(kcal100 = null, protein100 = null, fat100 = null))
        val llm = StepLlmClient(
            { m -> assertTrue(system(m).contains("\"incomplete\":true")); tools(call("c1", "log_meal", """{"items":[{"ref":"off:$bar","grams":30}]}""")) },
            { text("Registrato, ma mancano dei valori.") },
        )
        f.processor(llm).submitText(thread, "ne ho mangiati 30 grammi", attachedFoodId = foodId)
        val card = cards().filterIsInstance<MealLoggedCard>().single()
        assertTrue(card.items.single().incomplete)
        assertTrue(f.db.foodDao().getAll().single().incomplete)
    }

    @Test fun getProductByBarcodeReadsTheCacheThenOpenFoodFactsAndShowsAProductCard() = runBlocking {
        f.offBarcodes.products = mapOf(bar to nocciolata)
        val llm = StepLlmClient(
            { tools(call("c1", "get_product_by_barcode", """{"barcode":"$bar"}""")) },
            { m ->
                val r = lastResult(m)
                assertEquals("found", r["status"]!!.jsonPrimitive.content); assertEquals("false", r["cached"]!!.jsonPrimitive.content)
                val ref = r["food"]!!.jsonObject["ref"]!!.jsonPrimitive.content
                assertEquals("off:$bar", ref)
                tools(call("c2", "log_meal", """{"items":[{"ref":"$ref","servings":0.5,"portion":"package"}]}"""))
            },
            { text("Fatto.") },
        )
        f.processor(llm).submitText(thread, "ho mangiato mezza confezione del prodotto $bar")
        assertEquals(175.0, entries().single().grams, 0.0)
        assertEquals(listOf(bar), f.offBarcodes.codes)
        assertTrue(cards().any { it is ProductCard && it.name == "Nocciolata" && !it.incomplete })
        assertEquals(EntrySource.OPEN_FOOD_FACTS, entries().single().source)

        // A second lookup is served by the local cache (the product was cached by the log).
        val again = f.fullRegistry.execute(call("x", "get_product_by_barcode", """{"barcode":"$bar"}"""), ToolContext(f.now, f.zone, thread, null))
        assertEquals("true", (again.outcome as ToolOutcome.Ok).result["cached"]!!.jsonPrimitive.content)
        assertEquals(1, f.offBarcodes.codes.size)
    }

    @Test fun getProductByBarcodeNotFoundOfflineAndBadInput() = runBlocking {
        val ctx = ToolContext(f.now, f.zone, thread, null)
        val nf = f.fullRegistry.execute(call("a", "get_product_by_barcode", """{"barcode":"5000000000000"}"""), ctx).outcome as ToolOutcome.Ok
        assertEquals("not_found", nf.result["status"]!!.jsonPrimitive.content)
        f.offBarcodes.fail = true
        assertTrue(f.fullRegistry.execute(call("b", "get_product_by_barcode", """{"barcode":"5000000000000"}"""), ctx).outcome is ToolOutcome.Error)
        assertTrue(f.fullRegistry.execute(call("c", "get_product_by_barcode", """{"barcode":"abcdefgh"}"""), ctx).outcome is ToolOutcome.Error)
        assertTrue(f.fullRegistry.execute(call("d", "get_product_by_barcode", """{"barcode":"12"}"""), ctx).outcome is ToolOutcome.Error)
        assertTrue("nothing written", f.db.foodDao().getAll().isEmpty())
    }
}
