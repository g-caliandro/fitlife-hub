package com.lifehub.app.copilot.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Strict argument schemas for copilot tools. One definition produces both the JSON Schema sent to the model
 * ([toJsonSchema]) and the app-side validation ([validate]); they cannot drift apart.
 *
 * Validation is strict: unknown properties, wrong types, missing required properties, out-of-range numbers, values
 * outside an enum and malformed dates are rejected with a readable message (returned to the model, never thrown).
 * Nested [Obj] and [Arr] exist for the write/draft tools of later phases (`log_meal` items, plan drafts).
 */
sealed class ParamType {
    abstract val description: String?

    data class Str(
        override val description: String? = null,
        val enum: List<String>? = null,
        val minLength: Int = 0,
        val maxLength: Int = 500,
    ) : ParamType()

    /** ISO date `YYYY-MM-DD` (string in the JSON Schema). [min]/[max] bound the accepted dates. */
    data class Date(override val description: String? = null, val min: LocalDate = MIN_DATE, val max: LocalDate = MAX_DATE) : ParamType()

    data class Integer(override val description: String? = null, val min: Long? = null, val max: Long? = null) : ParamType()

    data class Num(override val description: String? = null, val min: Double? = null, val max: Double? = null) : ParamType()

    data class Bool(override val description: String? = null) : ParamType()

    data class Obj(val params: List<Param>, override val description: String? = null) : ParamType()

    data class Arr(val items: ParamType, override val description: String? = null, val minItems: Int = 0, val maxItems: Int = 50) : ParamType()

    companion object {
        val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)
        val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)
    }
}

data class Param(val name: String, val type: ParamType, val required: Boolean = true)

/** Top-level arguments of a tool (always a JSON object). */
data class ToolSchema(val params: List<Param>) {
    init {
        require(params.map { it.name }.toSet().size == params.size) { "duplicate parameter names" }
    }

    fun toJsonSchema(): JsonObject = ParamType.Obj(params).toJsonSchema()

    /** Parses and validates [raw] (the model's argument string). */
    fun validate(raw: String): ValidationResult {
        val el = try {
            kotlinx.serialization.json.Json.parseToJsonElement(raw.ifBlank { "{}" })
        } catch (_: Exception) {
            return ValidationResult.Invalid("Arguments are not valid JSON.")
        }
        val errors = mutableListOf<String>()
        ParamType.Obj(params).check(el, "arguments", errors)
        return if (errors.isEmpty()) ValidationResult.Valid(ToolArgs(el as JsonObject))
        else ValidationResult.Invalid(errors.take(5).joinToString(" "))
    }

    companion object {
        val EMPTY = ToolSchema(emptyList())
    }
}

sealed interface ValidationResult {
    data class Valid(val args: ToolArgs) : ValidationResult
    data class Invalid(val message: String) : ValidationResult
}

/** Already-validated arguments; getters return null only for absent optional parameters. */
class ToolArgs(val json: JsonObject) {
    private fun prim(name: String) = (json[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    fun string(name: String): String? = prim(name)?.contentOrNull
    fun date(name: String): LocalDate? = string(name)?.let(LocalDate::parse)
    fun long(name: String): Long? = prim(name)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    fun int(name: String): Int? = long(name)?.toInt()
    fun double(name: String): Double? = prim(name)?.doubleOrNull
    fun bool(name: String): Boolean? = prim(name)?.booleanOrNull
    fun obj(name: String): ToolArgs? = (json[name] as? JsonObject)?.let(::ToolArgs)
    fun array(name: String): JsonArray? = json[name] as? JsonArray
}

private fun ParamType.toJsonSchema(): JsonObject = buildJsonObject {
    when (val t = this@toJsonSchema) {
        is ParamType.Str -> {
            put("type", "string")
            t.enum?.let { e -> put("enum", buildJsonArray { e.forEach { add(JsonPrimitive(it)) } }) }
            if (t.minLength > 0) put("minLength", t.minLength)
            put("maxLength", t.maxLength)
        }
        is ParamType.Date -> { put("type", "string"); put("format", "date") }
        is ParamType.Integer -> { put("type", "integer"); t.min?.let { put("minimum", it) }; t.max?.let { put("maximum", it) } }
        is ParamType.Num -> { put("type", "number"); t.min?.let { put("minimum", it) }; t.max?.let { put("maximum", it) } }
        is ParamType.Bool -> put("type", "boolean")
        is ParamType.Obj -> {
            put("type", "object")
            put("properties", buildJsonObject { t.params.forEach { p -> put(p.name, p.type.toJsonSchema()) } })
            put("required", buildJsonArray { t.params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) } })
            put("additionalProperties", false)
        }
        is ParamType.Arr -> {
            put("type", "array"); put("items", t.items.toJsonSchema())
            if (t.minItems > 0) put("minItems", t.minItems)
            put("maxItems", t.maxItems)
        }
    }
    description?.let { put("description", it) }
}

private fun ParamType.check(el: JsonElement?, path: String, errors: MutableList<String>) {
    fun fail(msg: String) { errors += "'$path' $msg." }
    val prim = el as? JsonPrimitive
    when (val t = this) {
        is ParamType.Str -> {
            if (prim == null || prim is JsonNull || !prim.isString) return fail("must be a string")
            val v = prim.content
            if (t.enum != null && v !in t.enum) return fail("must be one of ${t.enum.joinToString()}")
            if (v.length < t.minLength) return fail(if (t.minLength == 1) "must not be empty" else "must have at least ${t.minLength} characters")
            if (v.length > t.maxLength) fail("must have at most ${t.maxLength} characters")
        }
        is ParamType.Date -> {
            if (prim == null || prim is JsonNull || !prim.isString) return fail("must be an ISO date string (YYYY-MM-DD)")
            val d = try { LocalDate.parse(prim.content) } catch (_: DateTimeParseException) { null }
                ?: return fail("must be an ISO date (YYYY-MM-DD), got \"${prim.content.take(40)}\"")
            if (d.isBefore(t.min) || d.isAfter(t.max)) fail("must be between ${t.min} and ${t.max}")
        }
        is ParamType.Integer -> {
            if (prim == null || prim is JsonNull || prim.isString) return fail("must be an integer")
            val d = prim.doubleOrNull ?: return fail("must be an integer")
            if (d != Math.floor(d) || d.isInfinite()) return fail("must be an integer")
            if (t.min != null && d < t.min) fail("must be >= ${t.min}")
            if (t.max != null && d > t.max) fail("must be <= ${t.max}")
        }
        is ParamType.Num -> {
            if (prim == null || prim is JsonNull || prim.isString) return fail("must be a number")
            val d = prim.doubleOrNull?.takeIf { it.isFinite() } ?: return fail("must be a number")
            if (t.min != null && d < t.min) fail("must be >= ${t.min}")
            if (t.max != null && d > t.max) fail("must be <= ${t.max}")
        }
        is ParamType.Bool -> if (prim == null || prim is JsonNull || prim.isString || prim.booleanOrNull == null) fail("must be true or false")
        is ParamType.Obj -> {
            val o = el as? JsonObject ?: return fail("must be an object")
            val known = t.params.map { it.name }.toSet()
            (o.keys - known).forEach { errors += "Unknown property '$it' in '$path' (allowed: ${known.joinToString().ifEmpty { "none" }})." }
            t.params.forEach { p ->
                val v = o[p.name]
                val child = if (path == "arguments") p.name else "$path.${p.name}"
                if (v == null || v is JsonNull) { if (p.required) errors += "Missing required property '$child'." }
                else p.type.check(v, child, errors)
            }
        }
        is ParamType.Arr -> {
            val a = el as? JsonArray ?: return fail("must be an array")
            if (a.size < t.minItems) return fail("must have at least ${t.minItems} items")
            if (a.size > t.maxItems) return fail("must have at most ${t.maxItems} items")
            a.forEachIndexed { i, item -> t.items.check(item, "$path[$i]", errors) }
        }
    }
}
