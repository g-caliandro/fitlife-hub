package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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

/** Argument validation of resolve_food, log_meal and request_user_input: readable errors, nothing written. */
@RunWith(RobolectricTestRunner::class)
class FoodToolsTest {
    private val f = CopilotFixture(seed = false)
    private lateinit var ctx: ToolContext

    @Before fun setUp() = runBlocking {
        f.baseFoods.ensureSeeded()
        val thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
        val c = f.captures.create(thread, ChatOrigin.CHAT, "x", f.zone, f.now)
        f.captures.move(c.id, CaptureStatus.PROCESSING, f.now)
        ctx = ToolContext(f.now, f.zone, thread, c.id)
    }
    @After fun tearDown() = f.close()

    private fun run(name: String, args: String, c: ToolContext = ctx) = runBlocking { f.fullRegistry.execute(call("id", name, args), c).outcome }
    private fun error(name: String, args: String, c: ToolContext = ctx): String = (run(name, args, c) as ToolOutcome.Error).message
    private fun ok(name: String, args: String): JsonObject = (run(name, args) as ToolOutcome.Ok).result
    private fun noEntries() = runBlocking { assertTrue(f.db.foodEntryDao().getAll().isEmpty()) }

    @Test fun schemasNeverAcceptMacrosFromTheModel() {
        val logMeal = f.fullRegistry.definitions().single { it.name == "log_meal" }.parameters
        val item = logMeal["properties"]!!.jsonObject["items"]!!.jsonObject["items"]!!.jsonObject
        assertEquals("false", item["additionalProperties"]!!.jsonPrimitive.content)
        assertFalse(item["properties"]!!.jsonObject.keys.any { it.contains("kcal") || it.contains("protein") })
        val e = error("log_meal", """{"items":[{"ref":"base:sr-169756","grams":90,"kcal":300}]}""")
        assertTrue(e, e.contains("Unknown property 'kcal'"))
        noEntries()
    }

    @Test fun logMealRejectsMalformedItems() {
        assertTrue(error("log_meal", """{"items":[]}""").contains("at least 1"))
        assertTrue(error("log_meal", """{}""").contains("Missing required property 'items'"))
        assertTrue(error("log_meal", """{"items":[{"ref":"base:sr-169756","grams":-5}]}""").contains(">= 0.1"))
        assertTrue(error("log_meal", """{"items":[{"ref":"base:sr-169756","grams":90,"servings":1}]}""").contains("only one of grams, servings or measure"))
        assertTrue(error("log_meal", """{"items":[{"ref":"base:sr-169756","portion":"scatola"}]}""").contains("'portion' needs 'servings'"))
        assertTrue(error("log_meal", """{"items":[{"ref":"base:sr-169756","count":2}]}""").contains("'count' needs 'measure'"))
        noEntries()
    }

    @Test fun logMealRejectsFutureAndMalformedDates() {
        val future = error("log_meal", """{"items":[{"ref":"base:sr-169756","grams":90}],"datetime":"2026-11-17T08:00"}""")
        assertTrue(future, future.contains("future"))
        assertTrue(error("log_meal", """{"items":[{"ref":"base:sr-169756","grams":90}],"datetime":"ieri sera"}""").contains("local date and time"))
        noEntries()
        // Yesterday evening is fine.
        ok("log_meal", """{"items":[{"ref":"base:sr-169756","grams":90}],"datetime":"2026-11-15T20:30"}""")
        assertEquals(f.today.minusDays(1), runBlocking { f.db.foodEntryDao().getAll().single().date })
    }

    @Test fun logMealNeedsACapture() {
        val e = error("log_meal", """{"items":[{"ref":"base:sr-169756","grams":90}]}""", ctx.copy(captureId = null))
        assertTrue(e.contains("not available"))
        noEntries()
    }

    @Test fun unresolvableItemsComeBackAsNotLoggedWithWhatToAsk() {
        runBlocking { f.db.foodDao().insert(Food("f-y", FoodSource.CUSTOM, null, "Yogurt", null, 60.0, 4.0, 5.0, 3.0, null, false, null)) }
        val r = ok("log_meal", """{"items":[
            {"ref":"base:nope","grams":10},
            {"ref":"food:f-y","servings":0.5,"portion":"scatola"},
            {"ref":"food:f-y"},
            {"ref":"base:sr-169756","measure":"vassoio"}]}""")
        val reasons = r["not_logged"]!!.jsonArray.map { it.jsonObject["reason"]!!.jsonPrimitive.content to it.jsonObject["ask_with"]?.jsonPrimitive?.content }
        assertEquals(listOf("unknown_ref" to null, "no_package_weight" to "package_weight", "no_default_portion" to "quantity", "unknown_measure" to "quantity"),
            reasons.map { (a, b) -> a to (if (b == "null") null else b) })
        noEntries()
    }

    @Test fun resolveFoodValidatesArguments() {
        assertTrue(error("resolve_food", """{"name":""}""").contains("must not be empty"))
        assertTrue(error("resolve_food", """{"name":"riso","state":"fried"}""").contains("must be one of raw, cooked"))
        val r = ok("resolve_food", """{"name":"olio"}""")
        val food = r["food"]!!.jsonObject
        assertEquals("base:sr-171413", food["ref"]!!.jsonPrimitive.content)
        assertTrue(food["household_measures"]!!.jsonArray.map { it.jsonPrimitive.content }.contains("cucchiaio 9 g"))
    }

    @Test fun requestUserInputValidatesArguments() {
        assertTrue(error("request_user_input", """{"kind":"maybe","prompt":"?"}""").contains("must be one of"))
        assertTrue(error("request_user_input", """{"kind":"use_estimate","prompt":"Stima?","item":{"name":"x"},"options":[{"label":"a","ref":"base:sr-169756"}]}""")
            .contains("needs an option with 'estimate'"))
        assertTrue(error("request_user_input", """{"kind":"choose_food","prompt":"Quale?","item":{"name":"x"},"options":[{"label":"a"}]}""")
            .contains("exactly one of 'ref' or 'estimate'"))
        assertTrue(error("request_user_input", """{"kind":"use_estimate","prompt":"?","item":{"name":"x"},"options":[{"label":"a","estimate":{"kcal_100g":500,"protein_100g":60,"carbs_100g":40,"fat_100g":20}}]}""")
            .contains("exceed 100 g"))
        assertTrue(error("request_user_input", """{"kind":"choose_food","prompt":"Quale?","options":[{"label":"a","ref":"base:sr-169756"}]}""").contains("'item' is required"))
        assertTrue(error("request_user_input", """{"kind":"choose_food","prompt":"Quale?","item":{"name":"x"},"options":[{"label":"a","ref":"off:404"}]}""").contains("unknown"))
        assertTrue(error("request_user_input", """{"kind":"package_weight","prompt":"Quanto pesa?"}""").contains("'food_ref' is required"))
        assertTrue(error("request_user_input", """{"kind":"quantity","prompt":"Quanto?","item":{"name":"x","grams":1,"servings":1}}""").contains("only one"))
        assertTrue(runBlocking { f.pending.byCapture(ctx.captureId!!).isEmpty() })
    }

    @Test fun packageWeightAskedOnceIsSavedAsTheProductPortion() = runBlocking {
        f.db.foodDao().insert(Food("f-cod", FoodSource.OFF, "8001", "Merluzzo gratinato", "Findus", 150.0, 11.0, 9.0, 7.6, null, false, f.now))
        val out = run("request_user_input", """{"kind":"package_weight","prompt":"Quanto pesa la confezione?","food_ref":"food:f-cod","item":{"name":"merluzzo","servings":0.5,"portion":"scatola"}}""")
        val pid = (out as ToolOutcome.Suspend).pendingInputId
        f.captures.move(ctx.captureId!!, CaptureStatus.AWAITING_INPUT, f.now)
        val llm = com.lifehub.app.copilot.StepLlmClient(
            { com.lifehub.app.copilot.FakeLlmClient.tools(call("c", "log_meal", """{"items":[{"ref":"food:f-cod","servings":0.5,"portion":"scatola"}]}""")) },
            { com.lifehub.app.copilot.FakeLlmClient.text("Ok") },
        )
        f.processor(llm).answer(pid, text = "400 g")
        val portion = f.db.portionDao().portions("f-cod").single()
        assertEquals(400.0, portion.grams, 0.0); assertEquals(com.lifehub.app.data.model.PortionKind.PACKAGE, portion.kind)
        assertEquals(200.0, f.db.foodEntryDao().getAll().single().grams, 0.0)
        assertEquals(CaptureStatus.DONE, f.captures.get(ctx.captureId!!)!!.status)
    }
}
