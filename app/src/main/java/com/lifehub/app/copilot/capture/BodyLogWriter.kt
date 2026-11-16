package com.lifehub.app.copilot.capture

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.CaptureWrite
import com.lifehub.app.data.model.WriteOp
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface WeightLogResult {
    /** Written now. [previous] = the row the day already had (its muscle/fat are kept), null for a new day. */
    data class Logged(val entry: BodyEntry, val previous: BodyEntry?) : WeightLogResult
    /** An earlier run of this capture already wrote [date]: [weightKg] is what it wrote (nothing is written again). */
    data class AlreadyLogged(val date: LocalDate, val weightKg: Double?) : WeightLogResult
}

/**
 * The only writer of copilot body-weight logs (design doc §7, owner decision 5). One row per date: on a day that already has
 * a weight only the weight (and `updatedAt`) change, muscle and fat are kept, and the capture ledger stores the whole
 * previous row so Undo ([FoodLogWriter.undo]) puts it back exactly; on a new day Undo deletes the row. Idempotent per
 * capture and date (a re-run, or the model calling again with another value, never writes twice).
 */
class BodyLogWriter(private val db: LifeHubDatabase) {
    private val captures = db.captureDao()
    private val body = db.bodyDao()

    suspend fun log(captureId: String, date: LocalDate, weightKg: Double, now: Instant): WeightLogResult = db.withTransaction {
        val capture = captures.get(captureId) ?: throw IllegalTransitionException("Unknown capture $captureId")
        if (!CaptureStateMachine.acceptsWrites(capture.status)) throw IllegalTransitionException("Capture is ${capture.status}")
        val key = idemKey(date)
        captures.writeByKey(captureId, key)?.let { w ->
            return@withTransaction WeightLogResult.AlreadyLogged(date, w.newJson?.let(::parse)?.weightKg)
        }
        val previous = body.get(date)
        val entry = BodyEntry(date, weightKg, previous?.muscleKg, previous?.fatKg, now)
        body.upsert(entry)
        captures.insertWrite(CaptureWrite(
            UUID.randomUUID().toString(), captureId, key, TABLE_BODY, date.toString(),
            if (previous == null) WriteOp.INSERT else WriteOp.UPDATE, previous?.let(::json), json(entry), now,
        ))
        WeightLogResult.Logged(entry, previous)
    }

    /** The weight rows a capture wrote (current values), for notifications. */
    suspend fun entriesOf(captureId: String): List<BodyEntry> =
        captures.writes(captureId).filter { it.tableName == TABLE_BODY }.mapNotNull { body.get(LocalDate.parse(it.recordId)) }

    companion object {
        const val TABLE_BODY = "body_entries"
        fun idemKey(date: LocalDate) = "body:$date"

        fun json(e: BodyEntry): String = buildJsonObject {
            put("date", JsonPrimitive(e.date.toString())); put("weightKg", JsonPrimitive(e.weightKg))
            put("muscleKg", e.muscleKg?.let(::JsonPrimitive) ?: JsonNull); put("fatKg", e.fatKg?.let(::JsonPrimitive) ?: JsonNull)
            put("updatedAt", JsonPrimitive(e.updatedAt.toEpochMilli()))
        }.toString()

        fun parse(json: String): BodyEntry? = runCatching {
            val o = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
            BodyEntry(
                LocalDate.parse(o["date"]!!.jsonPrimitive.content), o["weightKg"]!!.jsonPrimitive.content.toDouble(),
                o["muscleKg"]?.jsonPrimitive?.doubleOrNull, o["fatKg"]?.jsonPrimitive?.doubleOrNull,
                Instant.ofEpochMilli(o["updatedAt"]!!.jsonPrimitive.longOrNull!!),
            )
        }.getOrNull()
    }
}
