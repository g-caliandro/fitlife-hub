package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.capture.PendingCodec
import com.lifehub.app.copilot.capture.UndoResult
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.copilot.llm.LlmError
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.data.model.PortionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Phase 3 acceptance (design doc §12, phase 3 row + cross-cutting criteria), end to end through CaptureProcessor ->
 * AgentLoop -> ToolRegistry -> FoodResolver / QuantityNormalizer / FoodLogWriter on a real in-memory DB, with a scripted
 * fake model and a fake Open Food Facts. No network.
 */
@RunWith(RobolectricTestRunner::class)
class MealLoggingTest {
    private val f = CopilotFixture(seed = false)
    private lateinit var thread: String

    @Before fun setUp() = runBlocking {
        f.baseFoods.ensureSeeded()
        f.settings.setProteinTarget(150); f.settings.setKcalTarget(2200)
        thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
    }
    @After fun tearDown() = f.close()

    private val json = Json
    /** Results of the tool calls of the last round (what the model just received). */
    private fun lastResults(msgs: List<LlmMessage>): List<JsonObject> =
        msgs.takeLastWhile { it is LlmMessage.Tool }.map { json.parseToJsonElement((it as LlmMessage.Tool).content).jsonObject }
    private fun refs(msgs: List<LlmMessage>) = lastResults(msgs).map { it["food"]!!.jsonObject["ref"]!!.jsonPrimitive.content }
    private fun entries() = runBlocking { f.food.observeEntries(f.today.minusDays(30), f.today).first() }
    private fun cards(): List<ChatCard> = runBlocking { f.chat.messages(thread).mapNotNull { CardCodec.decode(it.cardType, it.cardJson) } }
    private fun base(id: String) = runBlocking { f.baseFoods.get(id)!! }

    /** Rice / chicken / oil script: resolve the three foods, log them with the refs received, answer. */
    private fun riceChickenOil() = StepLlmClient(
        { tools(call("c1", "resolve_food", """{"name":"riso basmati"}"""), call("c2", "resolve_food", """{"name":"petto di pollo"}"""), call("c3", "resolve_food", """{"name":"olio"}""")) },
        { m ->
            val r = refs(m)
            tools(call("c4", "log_meal", """{"items":[{"ref":"${r[0]}","name":"riso basmati","grams":90},{"ref":"${r[1]}","name":"petto di pollo","grams":180},{"ref":"${r[2]}","name":"olio","measure":"cucchiaio"}]}"""))
        },
        { text("Registrato: riso, pollo e olio.") },
    )

    @Test fun riceChickenOilIsLoggedAtOnceWithCorrectMacrosAndUndoRestoresTheDbExactly() = runBlocking {
        val before = dumpTables(f.db)
        val run = f.processor(riceChickenOil()).submitText(thread, "Ho mangiato 90 grammi di riso basmati, 180 di petto di pollo e un cucchiaio di olio")

        assertEquals(TurnResult.Answered("Registrato: riso, pollo e olio."), run.turn)
        assertEquals(CaptureStatus.DONE, f.captures.get(run.captureId)!!.status)
        val e = entries().sortedBy { it.grams }
        assertEquals(listOf(9.0, 90.0, 180.0), e.map { it.grams })
        val (oil, rice, chicken) = e
        fun check(entry: FoodEntry, baseId: String, grams: Double) {
            val b = base(baseId)
            assertEquals(b.name, entry.name)
            assertEquals(b.kcal100 * grams / 100, entry.kcal, 1e-9); assertEquals(b.protein100 * grams / 100, entry.proteinG, 1e-9)
            assertEquals(b.carbs100 * grams / 100, entry.carbsG, 1e-9); assertEquals(b.fat100 * grams / 100, entry.fatG, 1e-9)
            assertEquals(EntrySource.BASE_TABLE, entry.source); assertEquals(run.captureId, entry.captureId)
            assertEquals(f.now, entry.loggedAt); assertEquals(f.today, entry.date)
        }
        check(rice, "sr-169756", 90.0); check(chicken, "sr-171077", 180.0); check(oil, "sr-171413", 9.0)
        // Known values from USDA SR Legacy: riso 365 kcal/100 g, olio 884 kcal/100 g, cucchiaio d'olio = 9 g (seed).
        assertEquals(328.5, rice.kcal, 1e-9); assertEquals(79.56, oil.kcal, 1e-9); assertEquals(40.5, chicken.proteinG, 1e-9)
        assertEquals("cucchiaio", oil.portionLabel); assertEquals(9.0, oil.portionGrams!!, 0.0)

        // Card macros equal the diary's (same doubles per item; totals = the day's totals).
        val card = cards().filterIsInstance<MealLoggedCard>().single()
        card.items.forEach { item ->
            val stored = e.single { it.id == item.entryId }
            assertEquals(listOf(stored.grams, stored.kcal, stored.proteinG, stored.carbsG, stored.fatG), listOf(item.grams, item.kcal, item.proteinG, item.carbsG, item.fatG))
            assertEquals("base_table", item.source)
        }
        val day = f.food.observeNutrition(f.today, f.today).first().single()
        assertEquals(day.totals.kcal, card.totalKcal, 1e-9); assertEquals(day.totals.proteinG, card.totalProteinG, 1e-9)
        assertEquals(day.totals.carbsG, card.totalCarbsG, 1e-9); assertEquals(day.totals.fatG, card.totalFatG, 1e-9)
        assertEquals(2200 - day.asMacroDay().kcal, card.kcalRemaining)

        // Undo: the DB is identical to before (foods created from the base table removed too).
        assertEquals(UndoResult.Undone(3), f.processor(StepLlmClient()).undo(run.captureId))
        assertEquals(before, dumpTables(f.db))
        assertEquals(CaptureStatus.CANCELLED, f.captures.get(run.captureId)!!.status)
        assertEquals(UndoResult.AlreadyUndone, f.processor(StepLlmClient()).undo(run.captureId))
    }

    @Test fun undoIsStrictAboutLastUsedAtAndSkipsMissingRecords() = runBlocking {
        val s0 = dumpTables(f.db)
        val first = f.processor(riceChickenOil()).submitText(thread, "riso, pollo, olio")
        val s1 = dumpTables(f.db)
        // A later capture logs rice again: the food now comes from history and its lastUsedAt is bumped.
        f.time.instant = f.now.plusSeconds(3600)
        val llm = StepLlmClient(
            { tools(call("d1", "resolve_food", """{"name":"riso basmati"}""")) },
            { m -> assertEquals("history", lastResults(m).single()["food"]!!.jsonObject["source"]!!.jsonPrimitive.content)
                tools(call("d2", "log_meal", """{"items":[{"ref":"${refs(m)[0]}","grams":50}]}""")) },
            { text("Ok.") },
        )
        val second = f.processor(llm).submitText(thread, "50 g di riso basmati")
        val riceFood = f.db.foodDao().getAll().single { it.baseFoodId == "sr-169756" }
        assertEquals(f.time.instant, riceFood.lastUsedAt)

        f.processor(StepLlmClient()).undo(second.captureId)
        assertEquals("lastUsedAt and everything else back to after the first capture", s1, dumpTables(f.db))

        // The user deleted one entry from the diary meanwhile: Undo skips it silently and still restores everything.
        f.food.deleteEntry(entries().first().id)
        assertTrue(f.processor(StepLlmClient()).undo(first.captureId) is UndoResult.Undone)
        assertEquals(s0, dumpTables(f.db))
    }

    @Test fun undoneCaptureIsNeverRedoneByTheCopilot() = runBlocking {
        val run = f.processor(riceChickenOil()).submitText(thread, "riso, pollo, olio")
        f.processor(StepLlmClient()).undo(run.captureId)
        val ctx = ToolContext(f.now, f.zone, thread, run.captureId)
        val exec = f.fullRegistry.execute(call("x", "log_meal", """{"items":[{"ref":"base:sr-169756","grams":90}]}"""), ctx)
        assertTrue(exec.outcome is ToolOutcome.Error)
        assertTrue(entries().isEmpty())
    }

    @Test fun halfFindusCodTheUsualLogsHalfThePackageWithItsValuesAndIsASnapshot() = runBlocking {
        val cod = Food("f-cod", FoodSource.OFF, "8001", "Merluzzo gratinato", "Findus", 150.0, 11.0, 9.0, 7.6, null, false, Instant.parse("2026-11-01T12:00:00Z"))
        f.db.foodDao().insert(cod)
        f.db.portionDao().upsertPortion(FoodPortion("p-box", "f-cod", "scatola", 400.0, isDefault = true, kind = PortionKind.PACKAGE, createdAt = f.now))
        val llm = StepLlmClient(
            { tools(call("c1", "resolve_food", """{"name":"merluzzo Findus gratinato"}""")) },
            { m -> tools(call("c2", "log_meal", """{"items":[{"ref":"${refs(m)[0]}","name":"merluzzo Findus gratinato","servings":0.5}]}""")) },
            { text("Registrato mezzo merluzzo.") },
        )
        f.processor(llm).submitText(thread, "Ho mangiato mezzo merluzzo Findus gratinato, il solito")
        assertTrue("history resolved it: no Open Food Facts search", f.off.queries.isEmpty())
        val e = entries().single()
        assertEquals(200.0, e.grams, 0.0)
        assertEquals(300.0, e.kcal, 1e-9); assertEquals(22.0, e.proteinG, 1e-9); assertEquals(18.0, e.carbsG, 1e-9); assertEquals(15.2, e.fatG, 1e-9)
        assertEquals("f-cod", e.foodId); assertEquals(EntrySource.HISTORY, e.source)
        assertEquals(0.5, e.servings!!, 0.0); assertEquals("scatola", e.portionLabel); assertEquals(400.0, e.portionGrams!!, 0.0)
        val item = cards().filterIsInstance<MealLoggedCard>().single().items.single()
        assertEquals("history", item.source); assertEquals(0.5, item.servings!!, 0.0); assertTrue(item.usedDefaultPortion)

        // Editing the product (values and box weight) afterwards does not change the log.
        f.food.saveFood(cod.copy(kcal100 = 999.0, protein100 = 1.0))
        f.db.portionDao().upsertPortion(FoodPortion("p-box", "f-cod", "scatola", 500.0, true, PortionKind.PACKAGE, f.now))
        assertEquals(e, entries().single())
    }

    @Test fun unknownFoodIsNeverWrittenAndBecomesARequestWithTheEstimateMarked() = runBlocking {
        val llm = StepLlmClient(
            { tools(call("c1", "resolve_food", """{"name":"polpette della nonna Pina"}""")) },
            { m ->
                assertEquals("not_found", lastResults(m).single()["status"]!!.jsonPrimitive.content)
                tools(call("c2", "request_user_input", """{"kind":"use_estimate","prompt":"Non trovo le polpette della nonna Pina. Uso una stima?",
                    "options":[{"label":"Polpette (stima)","estimate":{"kcal_100g":220,"protein_100g":14,"carbs_100g":8,"fat_100g":15}}],
                    "item":{"name":"polpette della nonna Pina","grams":150}}"""))
            },
        )
        val run = f.processor(llm).submitText(thread, "150 g di polpette della nonna Pina")
        assertTrue(run.turn is TurnResult.AwaitingInput)
        assertEquals(0, llm.remaining)
        assertTrue("nothing written", entries().isEmpty())
        assertTrue(f.db.foodDao().getAll().isEmpty())
        assertEquals(CaptureStatus.AWAITING_INPUT, f.captures.get(run.captureId)!!.status)
        assertEquals(1, f.pending.observeOpenCount().first())
        val p = f.pending.byCapture(run.captureId).single()
        val option = PendingCodec.options(p).single()
        assertTrue(option.isEstimate); assertNotNull(option.ref)
        val card = cards().filterIsInstance<PendingInputCard>().single()
        assertTrue(card.options.single().isEstimate)

        // Even if the model tries, the estimate cannot be logged before the user chooses it.
        val ctx = ToolContext(f.now, f.zone, thread, run.captureId)
        f.captures.move(run.captureId, CaptureStatus.PROCESSING, f.now)
        val sneaky = f.fullRegistry.execute(call("x", "log_meal", """{"items":[{"ref":"${option.ref}","grams":150}]}"""), ctx)
        assertTrue("held while the question is open", (sneaky.outcome as ToolOutcome.Error).message.contains("unanswered"))
        assertTrue(entries().isEmpty())
        f.captures.move(run.captureId, CaptureStatus.AWAITING_INPUT, f.now)

        // The user picks the estimate: a new turn logs it, and it becomes a user food found at step 1 next time.
        val resume = StepLlmClient(
            { m ->
                val ctxSection = (m.first() as LlmMessage.System).content
                assertTrue(ctxSection, ctxSection.contains(option.ref!!))
                tools(call("c3", "log_meal", """{"items":[{"ref":"${option.ref}","name":"polpette della nonna Pina","grams":150}]}"""))
            },
            { text("Registrate con la stima.") },
        )
        f.processor(resume).answer(p.id, option = 0)
        val e = entries().single()
        assertEquals(EntrySource.LLM_ESTIMATE, e.source); assertEquals(330.0, e.kcal, 1e-9); assertEquals(150.0, e.grams, 0.0)
        assertEquals(PendingStatus.ANSWERED, f.pending.get(p.id)!!.status)
        assertEquals(0, f.pending.observeOpenCount().first())
        assertEquals(CaptureStatus.DONE, f.captures.get(run.captureId)!!.status)
        val estimateFood = f.db.foodDao().getAll().single()
        assertEquals(FoodSource.ESTIMATE, estimateFood.source)
        val again = f.resolver.resolve("polpette della nonna Pina")
        assertEquals(EntrySource.HISTORY, again.food!!.source); assertEquals("food:${estimateFood.id}", again.food!!.ref)
    }

    @Test fun nothingIsWrittenWhileAnotherItemIsPendingAndTheAnswerLogsTheWholeMealOnce() = runBlocking {
        var rice = ""
        val llm = StepLlmClient(
            { tools(call("c1", "resolve_food", """{"name":"riso basmati"}"""), call("c2", "resolve_food", """{"name":"polpette della nonna Pina"}""")) },
            { m ->
                rice = lastResults(m)[0]["food"]!!.jsonObject["ref"]!!.jsonPrimitive.content
                // A model that logs the clear item and asks about the other in the same round: the log is held back.
                tools(
                    call("c3", "log_meal", """{"items":[{"ref":"$rice","grams":90}],"datetime":"2026-11-16T13:30"}"""),
                    call("c4", "request_user_input", """{"kind":"use_estimate","prompt":"Uso una stima per le polpette?",
                        "options":[{"label":"Stima","estimate":{"kcal_100g":220,"protein_100g":14,"carbs_100g":8,"fat_100g":15}}],"item":{"name":"polpette","grams":100}}"""),
                )
            },
        )
        val run = f.processor(llm).submitText(thread, "A pranzo 90 g di riso basmati e 100 g di polpette della nonna Pina")
        assertTrue(run.turn is TurnResult.AwaitingInput)
        assertTrue("nothing written while a question is open", entries().isEmpty())
        assertTrue(cards().none { it is MealLoggedCard })
        val held = f.chat.messages(thread).first { it.toolCallId == "c3" }
        assertTrue(held.text!!, held.text!!.contains("Not executed"))
        assertEquals(CaptureStatus.AWAITING_INPUT, f.captures.get(run.captureId)!!.status)

        // Later (after 18:00) the user answers: the model logs the WHOLE meal once, at the time of the original request.
        f.time.instant = Instant.parse("2026-11-16T17:00:00Z")
        val p = f.pending.byCapture(run.captureId).single()
        val ref = PendingCodec.options(p).single().ref!!
        val resume = StepLlmClient(
            { m ->
                val section = (m.first() as LlmMessage.System).content
                assertTrue(section, section.contains("Richiesta originale") && section.contains("polpette della nonna Pina") && section.contains("niente è stato scritto"))
                tools(call("c5", "log_meal", """{"items":[{"ref":"$rice","grams":90},{"ref":"$ref","grams":100}],"datetime":"2026-11-16T13:30"}"""))
            },
            { text("Registrato il pranzo.") },
        )
        f.processor(resume).answer(p.id, option = 0)
        val all = entries()
        assertEquals(2, all.size)
        val mealAt = Instant.parse("2026-11-16T12:30:00Z")   // 13:30 Europe/Rome
        assertTrue(all.all { it.captureId == run.captureId && it.loggedAt == mealAt && it.date == f.today })
        assertEquals(CaptureStatus.DONE, f.captures.get(run.captureId)!!.status)
        assertEquals(1, cards().filterIsInstance<MealLoggedCard>().size)
        // The user's answer is in the chat as a message of the same capture.
        assertEquals("Stima (stima)", f.chat.messages(thread).last { it.role == ChatRole.USER }.text)
    }

    @Test fun rerunningTheSameCaptureDoesNotDuplicateLogs() = runBlocking {
        // First run: the meal is written, then the model call fails (e.g. network) -> capture FAILED.
        val llm = StepLlmClient(
            { tools(call("c1", "resolve_food", """{"name":"riso basmati"}"""), call("c2", "resolve_food", """{"name":"petto di pollo"}""")) },
            { m -> val r = refs(m); tools(call("c3", "log_meal", """{"items":[{"ref":"${r[0]}","grams":90},{"ref":"${r[1]}","grams":180}]}""")) },
            { LlmResult.Failure(LlmError.Network(timeout = false)) },
        )
        val p = f.processor(llm)
        val run = p.submitText(thread, "90 g di riso basmati e 180 g di petto di pollo")
        assertEquals(CaptureStatus.FAILED, f.captures.get(run.captureId)!!.status)
        assertEquals(2, entries().size)

        // Retry re-runs the whole capture: the model resolves (now from history) and logs the same items again.
        llm.add { tools(call("d1", "resolve_food", """{"name":"riso basmati"}"""), call("d2", "resolve_food", """{"name":"petto di pollo"}""")) }
        llm.add { m -> val r = refs(m); tools(call("d3", "log_meal", """{"items":[{"ref":"${r[0]}","grams":90},{"ref":"${r[1]}","grams":180}]}""")) }
        llm.add { m ->
            assertEquals(2, lastResults(m).single()["already_logged"]!!.jsonPrimitive.content.toInt())
            text("Già registrato.")
        }
        p.retry(run.captureId)
        assertEquals(CaptureStatus.DONE, f.captures.get(run.captureId)!!.status)
        assertEquals(2, entries().size)
        assertEquals(2, f.db.foodDao().getAll().size)
        // Only one "Meal logged" card: the re-run wrote nothing new.
        assertEquals(1, cards().filterIsInstance<MealLoggedCard>().size)
    }

    @Test fun samePendingQuestionIsNotStoredTwice() = runBlocking {
        val ask = """{"kind":"quantity","prompt":"Quanta pasta?","item":{"name":"pasta"}}"""
        val llm = StepLlmClient({ tools(call("c1", "request_user_input", ask)) })
        val run = f.processor(llm).submitText(thread, "ho mangiato la pasta")
        f.captures.move(run.captureId, CaptureStatus.PROCESSING, f.now)
        val again = f.fullRegistry.execute(call("c2", "request_user_input", ask), ToolContext(f.now, f.zone, thread, run.captureId))
        assertTrue(again.outcome is ToolOutcome.Suspend)
        assertEquals(1, f.pending.byCapture(run.captureId).size)
    }
}
