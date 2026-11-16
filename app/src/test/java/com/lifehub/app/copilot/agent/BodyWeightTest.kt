package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.copilot.FakeLlmClient.Companion.call
import com.lifehub.app.copilot.FakeLlmClient.Companion.text
import com.lifehub.app.copilot.FakeLlmClient.Companion.tools
import com.lifehub.app.copilot.StepLlmClient
import com.lifehub.app.copilot.dumpTables
import com.lifehub.app.copilot.llm.LlmError
import com.lifehub.app.copilot.llm.LlmResult
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/** Phase 5 `log_body_weight` (design doc §5/§7, owner decisions 5 and 12) on a real in-memory DB with the fake model. */
@RunWith(RobolectricTestRunner::class)
class BodyWeightTest {
    private val f = CopilotFixture(seed = false)
    private lateinit var thread: String
    private val yesterday = LocalDate.of(2026, 11, 15)
    private val longAgo = Instant.parse("2026-11-15T07:00:00Z")

    @Before fun setUp() = runBlocking { thread = f.chat.createThread("t", ChatOrigin.CHAT, f.now).id }
    @After fun tearDown() = f.close()

    private fun rows() = runBlocking { f.db.bodyDao().getAll() }
    private fun cards(): List<ChatCard> = runBlocking { f.chat.messages(thread).mapNotNull { CardCodec.decode(it.cardType, it.cardJson) } }
    private fun log(args: String) = StepLlmClient({ tools(call("c1", "log_body_weight", args)) }, { text("Fatto.") })

    @Test fun newDayIsWrittenWithoutMuscleAndFatAndUndoDeletesTheRow() = runBlocking {
        f.body.upsert(BodyEntry(LocalDate.of(2026, 11, 10), 78.0, null, null, longAgo))
        val before = dumpTables(f.db)
        val run = f.processor(log("""{"weight_kg":74.2}""")).submitText(thread, "Stamattina pesavo 74,2")

        assertEquals(TurnResult.Answered("Fatto."), run.turn)
        val row = f.body.get(f.today)!!
        assertEquals(74.2, row.weightKg, 0.0); assertNull(row.muscleKg); assertNull(row.fatKg)
        assertEquals(f.now, row.updatedAt)
        val card = cards().filterIsInstance<WeightLoggedCard>().single()
        assertEquals(WeightLoggedCard(run.captureId, "2026-11-16", 74.2, null), card)

        f.processor(StepLlmClient()).undo(run.captureId)
        assertEquals("a new day's row is deleted: DB identical", before, dumpTables(f.db))
        assertEquals(CaptureStatus.CANCELLED, f.captures.get(run.captureId)!!.status)
    }

    @Test fun existingDayKeepsMuscleAndFatAndUndoRestoresThePreviousRowExactly() = runBlocking {
        f.body.upsert(BodyEntry(yesterday, 77.4, 35.0, 18.25, longAgo))
        f.body.upsert(BodyEntry(LocalDate.of(2026, 11, 10), 78.0, 34.0, null, longAgo))
        val before = dumpTables(f.db)
        val run = f.processor(log("""{"weight_kg":76.9,"date":"2026-11-15"}""")).submitText(thread, "Ieri pesavo 76,9")

        val row = f.body.get(yesterday)!!
        assertEquals(76.9, row.weightKg, 0.0); assertEquals(35.0, row.muscleKg!!, 0.0); assertEquals(18.25, row.fatKg!!, 0.0)
        assertEquals(f.now, row.updatedAt)
        assertEquals(2, rows().size)
        val card = cards().filterIsInstance<WeightLoggedCard>().single()
        assertEquals("2026-11-15", card.date); assertEquals(77.4, card.previousWeightKg!!, 0.0)

        f.processor(StepLlmClient()).undo(run.captureId)
        assertEquals("the previous row is back, updatedAt included (whole table compared)", before, dumpTables(f.db))
        assertEquals(BodyEntry(yesterday, 77.4, 35.0, 18.25, longAgo), f.body.get(yesterday))
    }

    @Test fun futureDatesAndImplausibleValuesAreRejectedWithReadableErrors() = runBlocking {
        val run = f.processor(StepLlmClient()).let { f.captures.create(thread, ChatOrigin.CHAT, "x", f.zone, f.now) }
        f.captures.move(run.id, CaptureStatus.PROCESSING, f.now)
        val ctx = ToolContext(f.now, f.zone, thread, run.id)
        fun exec(args: String) = runBlocking { f.fullRegistry.execute(call("x", "log_body_weight", args), ctx).outcome }

        val future = exec("""{"weight_kg":74.2,"date":"2026-11-17"}""") as ToolOutcome.Error
        assertTrue(future.message, future.message.contains("future"))
        assertTrue(exec("""{"weight_kg":7.42}""") is ToolOutcome.Error)
        assertTrue(exec("""{"weight_kg":742}""") is ToolOutcome.Error)
        assertTrue(exec("""{"weight_kg":74.2,"date":"ieri"}""") is ToolOutcome.Error)
        assertTrue(exec("""{"weight_kg":74.2,"extra":1}""") is ToolOutcome.Error)
        assertTrue("nothing written", rows().isEmpty())
        assertTrue(f.db.captureDao().writes(run.id).isEmpty())
        // Today is fine (also with the date given explicitly).
        assertTrue(exec("""{"weight_kg":74.2,"date":"2026-11-16"}""") is ToolOutcome.Ok)
    }

    @Test fun idempotentPerCaptureAcrossRetryAndAnotherValue() = runBlocking {
        f.body.upsert(BodyEntry(f.today, 75.0, 33.0, null, longAgo))
        val before = dumpTables(f.db)
        // First run: the tool writes, then the model call fails (Retry will re-run the turn).
        val first = StepLlmClient({ tools(call("c1", "log_body_weight", """{"weight_kg":74.2}""")) }, { LlmResult.Failure(LlmError.Network(timeout = false)) })
        val run = f.processor(first).submitText(thread, "Stamattina pesavo 74,2")
        assertEquals(CaptureStatus.FAILED, f.captures.get(run.captureId)!!.status)
        assertEquals(74.2, f.body.get(f.today)!!.weightKg, 0.0)

        // Retry: the model calls the tool again (even with a different value): nothing is written a second time.
        val again = StepLlmClient({ tools(call("c2", "log_body_weight", """{"weight_kg":74.9}""")) }, { text("Fatto.") })
        f.processor(again).retry(run.captureId)
        val row = f.body.get(f.today)!!
        assertEquals(74.2, row.weightKg, 0.0); assertEquals(33.0, row.muscleKg!!, 0.0)
        assertEquals(1, f.db.captureDao().writes(run.captureId).count { it.tableName == "body_entries" })
        val toolMsg = f.chat.messages(thread).last { it.toolName == "log_body_weight" }
        assertTrue(toolMsg.text!!, toolMsg.text!!.contains("already_logged"))
        assertEquals(CaptureStatus.DONE, f.captures.get(run.captureId)!!.status)

        // Undo goes back to the day's ORIGINAL row, not to the first run's value.
        f.processor(StepLlmClient()).undo(run.captureId)
        assertEquals(before, dumpTables(f.db))
    }

    @Test fun undoLeavesAWeightTheUserEditedAfterwards() = runBlocking {
        val run = f.processor(log("""{"weight_kg":74.2}""")).submitText(thread, "Stamattina pesavo 74,2")
        f.body.upsert(f.body.get(f.today)!!.copy(weightKg = 74.0, updatedAt = f.now.plusSeconds(60)))
        f.processor(StepLlmClient()).undo(run.captureId)
        assertEquals("the later manual edit is not destroyed", 74.0, f.body.get(f.today)!!.weightKg, 0.0)
    }

    @Test fun undoneWeightCannotBeLoggedAgainByTheModel() = runBlocking {
        val run = f.processor(log("""{"weight_kg":74.2}""")).submitText(thread, "Stamattina pesavo 74,2")
        f.processor(StepLlmClient()).undo(run.captureId)
        val r = f.fullRegistry.execute(call("x", "log_body_weight", """{"weight_kg":74.2}"""), ToolContext(f.now, f.zone, thread, run.captureId))
        assertTrue(r.outcome is ToolOutcome.Error)
        assertTrue(rows().isEmpty())
    }

    @Test fun toolResultReturnsTheLoggedValueAndDate() = runBlocking {
        f.processor(log("""{"weight_kg":74.24,"date":"2026-11-15"}""")).submitText(thread, "Ieri pesavo 74,24")
        val result = kotlinx.serialization.json.Json.parseToJsonElement(f.chat.messages(thread).last { it.toolName == "log_body_weight" }.text!!).let { it as kotlinx.serialization.json.JsonObject }
        assertEquals("2026-11-15", result["date"]!!.jsonPrimitive.content)
        assertEquals("74.2", result["weight_kg"]!!.jsonPrimitive.content)
        assertEquals("2026-11-15", result["id"]!!.jsonPrimitive.content)
    }
}
