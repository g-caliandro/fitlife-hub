package com.lifehub.app.copilot.agent

import com.lifehub.app.copilot.llm.LlmToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ToolRegistryTest {
    private val ctx = ToolContext(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)

    /** Echo tool with every parameter kind, including nested object/array (shape of phase 3 log_meal items). */
    private class EchoTool(override val name: String = "echo", override val kind: ToolKind = ToolKind.READ) : CopilotTool {
        var calls = 0
        override val description = "Echo"
        override val schema = ToolSchema(listOf(
            Param("date", ParamType.Date()),
            Param("count", ParamType.Integer(min = 1, max = 10), required = false),
            Param("grams", ParamType.Num(min = 0.0, max = 5000.0), required = false),
            Param("state", ParamType.Str(enum = listOf("raw", "cooked")), required = false),
            Param("flag", ParamType.Bool(), required = false),
            Param("items", ParamType.Arr(ParamType.Obj(listOf(Param("name", ParamType.Str(minLength = 1)), Param("servings", ParamType.Num(min = 0.0), required = false))), maxItems = 3), required = false),
        ))
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome {
            calls++
            return ToolOutcome.Ok(buildJsonObject { put("date", args.date("date").toString()); args.int("count")?.let { put("count", it) } })
        }
    }

    private class Boom : CopilotTool {
        override val name = "boom"; override val description = "x"; override val kind = ToolKind.READ
        override val schema = ToolSchema.EMPTY
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolOutcome = throw IllegalStateException("db exploded")
    }

    private val echo = EchoTool()
    private val registry = ToolRegistry(listOf(echo, Boom()))

    private fun run(name: String, args: String) = runBlocking { registry.execute(LlmToolCall("c1", name, args), ctx) }
    private fun errorOf(e: ToolExecution) = (e.outcome as ToolOutcome.Error).message

    @Test fun validArgumentsExecute() {
        val e = run("echo", """{"date":"2026-11-15","count":3,"grams":120.5,"state":"cooked","flag":true,"items":[{"name":"riso","servings":0.5}]}""")
        val ok = e.outcome as ToolOutcome.Ok
        assertEquals("2026-11-15", ok.result["date"]!!.jsonPrimitive.content)
        assertEquals(3, ok.result["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, echo.calls)
    }

    @Test fun integerGivenAsWholeDoubleIsAccepted() {
        assertTrue(run("echo", """{"date":"2026-11-15","count":3.0}""").outcome is ToolOutcome.Ok)
    }

    @Test fun missingRequiredIsReadableError() {
        val msg = errorOf(run("echo", "{}"))
        assertTrue(msg, msg.contains("Missing required property 'date'"))
        assertEquals(0, echo.calls)
    }

    @Test fun badDateIsRejected() {
        val msg = errorOf(run("echo", """{"date":"ieri"}"""))
        assertTrue(msg, msg.contains("ISO date") && msg.contains("ieri"))
        assertTrue(errorOf(run("echo", """{"date":"2026-02-30"}""")).contains("ISO date"))
        assertTrue(errorOf(run("echo", """{"date":"1999-12-31"}""")).contains("between"))
    }

    @Test fun wrongTypesAndRangesAreRejected() {
        assertTrue(errorOf(run("echo", """{"date":"2026-11-15","count":"3"}""")).contains("'count' must be an integer"))
        assertTrue(errorOf(run("echo", """{"date":"2026-11-15","count":2.5}""")).contains("'count' must be an integer"))
        assertTrue(errorOf(run("echo", """{"date":"2026-11-15","count":11}""")).contains("<= 10"))
        assertTrue(errorOf(run("echo", """{"date":"2026-11-15","grams":-1}""")).contains(">= 0.0"))
        assertTrue(errorOf(run("echo", """{"date":"2026-11-15","state":"fried"}""")).contains("one of raw, cooked"))
        assertTrue(errorOf(run("echo", """{"date":"2026-11-15","flag":"yes"}""")).contains("true or false"))
    }

    @Test fun nestedErrorsCarryAPath() {
        val msg = errorOf(run("echo", """{"date":"2026-11-15","items":[{"name":""},{"servings":1}]}"""))
        assertTrue(msg, msg.contains("'items[0].name' must not be empty"))
        assertTrue(msg, msg.contains("Missing required property 'items[1].name'"))
        assertTrue(errorOf(run("echo", """{"date":"2026-11-15","items":[{"name":"a"},{"name":"b"},{"name":"c"},{"name":"d"}]}""")).contains("at most 3 items"))
    }

    @Test fun unknownPropertyIsRejected() {
        val msg = errorOf(run("echo", """{"date":"2026-11-15","meal_type":"lunch"}"""))
        assertTrue(msg, msg.contains("Unknown property 'meal_type'"))
    }

    @Test fun malformedJsonAndNonObjectAreRejected() {
        assertTrue(errorOf(run("echo", """{"date":""")).contains("not valid JSON"))
        assertTrue(errorOf(run("echo", "[1,2]")).contains("must be an object"))
    }

    @Test fun emptyArgumentsAllowedForNoParamTools() {
        val r = ToolRegistry(listOf(object : CopilotTool {
            override val name = "noargs"; override val description = "x"; override val kind = ToolKind.READ
            override val schema = ToolSchema.EMPTY
            override suspend fun execute(args: ToolArgs, ctx: ToolContext) = ToolOutcome.Ok(JsonObject(emptyMap()))
        }))
        val e = runBlocking { r.execute(LlmToolCall("c", "noargs", ""), ctx) }
        assertTrue(e.outcome is ToolOutcome.Ok)
    }

    @Test fun unknownToolIsError() {
        val msg = errorOf(run("delete_everything", "{}"))
        assertTrue(msg, msg.contains("Unknown tool 'delete_everything'") && msg.contains("echo"))
    }

    @Test fun exceptionInsideToolBecomesErrorNotCrash() {
        val e = run("boom", "{}")
        val msg = errorOf(e)
        assertTrue(msg.contains("failed to run boom"))
        assertFalse("internal details must not leak", msg.contains("exploded"))
        assertEquals("""{"error":"$msg"}""", e.content)
    }

    @Test fun filterHidesTools() {
        val e = runBlocking { registry.execute(LlmToolCall("c", "echo", """{"date":"2026-11-15"}"""), ctx) { it.kind != ToolKind.READ || it.name != "echo" } }
        assertTrue(errorOf(e).contains("Unknown tool"))
        assertEquals(listOf("boom"), registry.definitions { it.name != "echo" }.map { it.name })
    }

    @Test fun jsonSchemaIsStrict() {
        val schema = registry.definitions().first { it.name == "echo" }.parameters
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(false), schema["additionalProperties"])
        assertEquals(listOf("date"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val props = schema["properties"]!!.jsonObject
        assertEquals("date", props["date"]!!.jsonObject["format"]!!.jsonPrimitive.content)
        assertEquals("integer", props["count"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val item = props["items"]!!.jsonObject["items"]!!.jsonObject
        assertEquals(JsonPrimitive(false), item["additionalProperties"])
        assertEquals(listOf("name"), item["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun argsGettersReturnNullForAbsentOptionals() {
        val v = ToolSchema(listOf(Param("d", ParamType.Date(), required = false))).validate("{}") as ValidationResult.Valid
        assertNull(v.args.date("d"))
        val w = ToolSchema(listOf(Param("d", ParamType.Date()))).validate("""{"d":"2026-01-02"}""") as ValidationResult.Valid
        assertEquals(LocalDate.of(2026, 1, 2), w.args.date("d"))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateToolNamesRejected() { ToolRegistry(listOf(EchoTool(), EchoTool())) }

    @Test fun classificationIsExposed() {
        val r = ToolRegistry(listOf(EchoTool("a", ToolKind.READ), EchoTool("b", ToolKind.WRITE), EchoTool("c", ToolKind.DRAFT), EchoTool("d", ToolKind.INPUT_REQUEST)))
        assertEquals(listOf("b"), r.definitions { it.kind == ToolKind.WRITE }.map { it.name })
        assertEquals(ToolKind.INPUT_REQUEST, r["d"]!!.kind)
    }
}
