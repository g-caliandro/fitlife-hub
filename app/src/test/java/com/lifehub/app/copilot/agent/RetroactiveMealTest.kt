package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.llm.LlmMessage
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.FoodSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/** Phase 5 retroactive log: "ieri sera ho mangiato 200 g di salmone" lands on yesterday with the time the model passed. */
@RunWith(RobolectricTestRunner::class)
class RetroactiveMealTest {
    private val f = CopilotFixture(seed = false)
    private lateinit var thread: String
    private val yesterday = LocalDate.of(2026, 11, 15)

    @Before fun setUp() = runBlocking {
        f.baseFoods.ensureSeeded()
        f.settings.setKcalTarget(2200); f.settings.setProteinTarget(150)
        // Yesterday already has 1000 kcal; today has 500: the card must use yesterday's total, not today's.
        val rice = Food("f-rice", FoodSource.CUSTOM, null, "Riso", null, 100.0, 2.0, 22.0, 0.3, null, false, null)
        f.food.addEntry(rice, yesterday, 1000.0, Instant.parse("2026-11-15T11:00:00Z"))
        f.food.addEntry(rice, f.today, 500.0, Instant.parse("2026-11-16T07:00:00Z"))
        thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id
    }
    @After fun tearDown() = f.close()

    private fun resultOf(m: List<LlmMessage>): JsonObject = Json.parseToJsonElement((m.last() as LlmMessage.Tool).content).jsonObject
    private fun entries(d: LocalDate): List<FoodEntry> = runBlocking { f.food.observeEntries(d, d).first() }

    @Test fun ieriSeraLandsOnYesterdayWithTheGivenTimeAndRemainingOfThatDay() = runBlocking {
        val llm = StepLlmClient(
            { m ->
                val s = (m.first() as LlmMessage.System).content
                assertTrue("clock with date and zone", s.contains("data 2026-11-16") && s.contains("Europe/Rome"))
                assertTrue("relative dates are resolved by the model into ISO values", s.contains("ieri") && s.contains("ISO"))
                tools(call("c1", "resolve_food", """{"name":"salmone"}"""))
            },
            { m -> tools(call("c2", "log_meal", """{"items":[{"ref":"${resultOf(m)["food"]!!.jsonObject["ref"]!!.jsonPrimitive.content}","grams":200}],"datetime":"2026-11-15T20:30"}""")) },
            { m ->
                val r = resultOf(m)
                assertEquals("2026-11-15", r["meal"]!!.jsonObject["date"]!!.jsonPrimitive.content)
                assertEquals("20:30", r["meal"]!!.jsonObject["time"]!!.jsonPrimitive.content)
                text("Registrato ieri sera.")
            },
        )
        f.processor(llm).submitText(thread, "Ieri sera ho mangiato 200 grammi di salmone")

        val e = entries(yesterday).single { it.name.contains("almone") }
        assertEquals(yesterday, e.date); assertEquals(200.0, e.grams, 0.0)
        assertEquals(Instant.parse("2026-11-15T19:30:00Z"), e.loggedAt)  // 20:30 Europe/Rome
        assertEquals("today is untouched", 1, entries(f.today).size)

        val card = f.chat.messages(thread).mapNotNull { CardCodec.decode(it.cardType, it.cardJson) }.filterIsInstance<MealLoggedCard>().single()
        assertEquals("2026-11-15", card.date); assertEquals("20:30", card.time)
        val yesterdayKcal = 1000.0 * 100 / 100 + e.kcal
        assertEquals("remaining refers to the logged day", (2200 - Math.round(yesterdayKcal)).toInt(), card.kcalRemaining)
        val todayRemaining = 2200 - 500
        assertTrue(card.kcalRemaining != todayRemaining)
    }

    @Test fun aFutureTimeIsRefusedAndNothingIsWritten() = runBlocking {
        val llm = StepLlmClient(
            { tools(call("c1", "log_meal", """{"items":[{"ref":"base:sr-169756","grams":100}],"datetime":"2026-11-17T20:30"}""")) },
            { m -> assertTrue((m.last() as LlmMessage.Tool).content.contains("future")); text("Non posso registrare nel futuro.") },
        )
        f.processor(llm).submitText(thread, "Domani sera mangerò del riso")
        assertEquals(1, entries(f.today).size); assertEquals(1, entries(yesterday).size)
    }
}
