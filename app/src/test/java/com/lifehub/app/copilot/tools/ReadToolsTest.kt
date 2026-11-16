package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.agent.DailySummaryCard
import com.lifehub.app.copilot.agent.ToolContext
import com.lifehub.app.copilot.agent.ToolOutcome
import com.lifehub.app.copilot.llm.LlmToolCall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadToolsTest {
    private val f = CopilotFixture()
    private val ctx = ToolContext(f.now, f.zone)

    @After fun tearDown() = f.close()

    private fun exec(name: String, args: String = "{}") = runBlocking { f.registry.execute(LlmToolCall("c", name, args), ctx) }.outcome
    private fun ok(name: String, args: String = "{}") = exec(name, args) as ToolOutcome.Ok
    private fun JsonObject.obj(k: String) = getValue(k).jsonObject
    private fun JsonObject.int(k: String) = getValue(k).jsonPrimitive.int

    @Test fun dailySummaryMatchesDiaryNumbers() { runBlocking {
        val out = ok("get_daily_summary", """{"date":"2026-11-16"}""")
        // The diary's numbers: rounded DayNutrition totals (food entries + quick adds).
        val diary = f.food.observeNutrition(f.today, f.today).first().single().asMacroDay()
        val consumed = out.result.obj("consumed")
        assertEquals(diary.kcal, consumed.int("kcal"))
        assertEquals(diary.proteinG, consumed.int("protein_g"))
        assertEquals(diary.carbsG, consumed.int("carbs_g"))
        assertEquals(diary.fatG, consumed.int("fat_g"))
        // 150 g chicken (34.95 P) + 80 g rice (5.6 P) + yogurt (10 P) = 50.55 -> 51; kcal 165 + 288 + 120 = 573.
        assertEquals(51, diary.proteinG); assertEquals(573, diary.kcal)
        val remaining = out.result.obj("remaining")
        assertEquals(150 - 51, remaining.int("protein_g"))
        assertEquals(2200 - 573, remaining.int("kcal"))
        assertEquals(70 - diary.fatG, remaining.int("fat_g"))
        assertEquals(JsonNull, remaining["carbs_g"]) // no carbs target
        assertEquals(JsonNull, out.result.obj("targets")["carbs_g"])
        assertEquals("complete", out.result.obj("macro_status").getValue("protein").jsonPrimitive.content)
        assertEquals(3, out.result.getValue("items").jsonArray.size)
        // Card carries the same numbers.
        val card = out.card as DailySummaryCard
        assertEquals("2026-11-16", card.date)
        assertEquals(listOf(573, 51, diary.carbsG, diary.fatG), listOf(card.kcal, card.proteinG, card.carbsG, card.fatG))
        assertEquals(99, card.proteinRemainingG); assertEquals(1627, card.kcalRemaining); assertNull(card.carbsRemainingG)
    } }

    @Test fun dailySummaryDefaultsToTodayInUserZone() {
        val out = ok("get_daily_summary")
        assertEquals("2026-11-16", out.result.getValue("date").jsonPrimitive.content)
        assertEquals("monday", out.result.getValue("weekday").jsonPrimitive.content)
    }

    @Test fun dailySummaryFlagsPartialMacros() {
        val out = ok("get_daily_summary", """{"date":"2026-11-15"}""")
        // Pizza has unknown macros, rice 100 g has 7 g protein: protein is a lower bound.
        assertEquals("partial", out.result.obj("macro_status").getValue("protein").jsonPrimitive.content)
        assertEquals(7, out.result.obj("consumed").int("protein_g"))
        assertEquals(800 + 360, out.result.obj("consumed").int("kcal"))
        val pizza = out.result.getValue("items").jsonArray.map { it.jsonObject }.first { it["name"]!!.jsonPrimitive.content == "Pizza" }
        assertEquals(JsonNull, pizza["protein_g"])
        assertTrue(pizza["quick_add"]!!.jsonPrimitive.boolean)
    }

    @Test fun dailySummaryEmptyDay() {
        val card = ok("get_daily_summary", """{"date":"2026-11-01"}""").card as DailySummaryCard
        assertEquals(0, card.kcal); assertEquals(150, card.proteinRemainingG); assertEquals(0, card.items)
    }

    @Test fun nutritionTargets() {
        val r = ok("get_nutrition_targets").result
        assertEquals(2200, r.int("kcal")); assertEquals(150, r.int("protein_g")); assertEquals(70, r.int("fat_g"))
        assertEquals(JsonNull, r["carbs_g"])
    }

    @Test fun goalsActiveByDefault() {
        val goals = ok("get_goals").result.getValue("goals").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Muscle up"), goals.map { it["name"]!!.jsonPrimitive.content })
        assertEquals(84, goals[0].int("days_to_deadline"))
        assertEquals(listOf("Pull day"), goals[0].getValue("linked_plans").jsonArray.map { it.jsonPrimitive.content })
        val all = ok("get_goals", """{"include_inactive":true}""").result.getValue("goals").jsonArray
        assertEquals(2, all.size)
    }

    @Test fun plannedWorkoutsFromWeekdayPlans() {
        // Mon 16 .. Thu 19: Pull (Mon, done), rest Tue, Legs Wed, Pull Thu. The archived plan never appears.
        val days = ok("get_planned_workouts", """{"from":"2026-11-16","to":"2026-11-19"}""").result.getValue("days").jsonArray.map { it.jsonObject }
        assertEquals(4, days.size)
        fun plans(i: Int) = days[i].getValue("plans").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Pull day"), plans(0).map { it["name"]!!.jsonPrimitive.content })
        assertTrue(plans(0)[0]["done"]!!.jsonPrimitive.boolean)
        assertTrue(plans(1).isEmpty())
        assertEquals(listOf("Legs"), plans(2).map { it["name"]!!.jsonPrimitive.content })
        assertEquals(false, plans(3)[0]["done"]!!.jsonPrimitive.boolean)
    }

    @Test fun plannedWorkoutsRangeErrors() {
        assertTrue((exec("get_planned_workouts", """{"from":"2026-11-19","to":"2026-11-16"}""") as ToolOutcome.Error).message.contains("before"))
        assertTrue((exec("get_planned_workouts", """{"from":"2026-01-01","to":"2026-12-31"}""") as ToolOutcome.Error).message.contains("31 days"))
    }

    @Test fun workoutPlanListsActivePlans() {
        val plans = ok("get_workout_plan").result.getValue("plans").jsonArray.map { it.jsonObject }
        assertEquals(setOf("Pull day", "Legs"), plans.map { it["name"]!!.jsonPrimitive.content }.toSet())
        val pull = plans.first { it["name"]!!.jsonPrimitive.content == "Pull day" }
        assertEquals(listOf("monday", "thursday"), pull.getValue("weekdays").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("Muscle up"), pull.getValue("goals").jsonArray.map { it.jsonPrimitive.content })
        val ex = pull.getValue("exercises").jsonArray.single().jsonObject
        assertEquals("Pull-up", ex["name"]!!.jsonPrimitive.content)
        assertEquals(4, ex.int("sets")); assertEquals(8, ex.int("target_value")); assertEquals(JsonNull, ex["weight_kg"])
    }

    @Test fun bodyWeight() {
        val r = ok("get_body_weight", """{"from":"2026-11-01","to":"2026-11-12"}""").result
        val entries = r.getValue("entries").jsonArray.map { it.jsonObject }
        assertEquals(listOf("2026-11-10"), entries.map { it["date"]!!.jsonPrimitive.content })
        assertEquals(78.0, entries[0]["weight_kg"]!!.jsonPrimitive.double, 0.0)
        val latest = r.obj("latest")
        assertEquals("2026-11-15", latest["date"]!!.jsonPrimitive.content)
        assertEquals(35.0, latest["muscle_kg"]!!.jsonPrimitive.double, 0.0)
    }

    @Test fun allReadToolsAreClassifiedRead() {
        assertEquals(6, f.registry.tools.size)
        assertTrue(f.registry.tools.all { it.kind == com.lifehub.app.copilot.agent.ToolKind.READ })
    }
}
