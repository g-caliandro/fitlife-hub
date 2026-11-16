package com.lifehub.app.copilot.capture

import androidx.room.withTransaction
import com.lifehub.app.copilot.food.FoodCandidate
import com.lifehub.app.copilot.food.Quantity
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.CaptureWrite
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.data.model.PortionKind
import com.lifehub.app.data.model.WriteOp
import com.lifehub.app.domain.macrosFor
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One item to write. [idemKey] identifies it within the capture: a re-run of the same capture (retry, or the model
 * logging an item again after a pending answer) finds the key and writes nothing. [name] = name for an estimate food.
 */
data class LogItem(val food: FoodCandidate, val quantity: Quantity.Resolved, val idemKey: String, val name: String? = null)

sealed interface LogResult {
    val item: LogItem
    /** Written now; [entry] holds the snapshot (grams, kcal and macros computed by the app). */
    data class Logged(override val item: LogItem, val entry: FoodEntry) : LogResult
    /** Written by an earlier run of this capture ([entry] null if the user deleted it from the diary since). */
    data class AlreadyLogged(override val item: LogItem, val entry: FoodEntry?) : LogResult
}

sealed interface UndoResult {
    data class Undone(val removedEntries: Int) : UndoResult
    data object AlreadyUndone : UndoResult
    data object NotFound : UndoResult
}

/**
 * The only writer of copilot meal logs (design doc §7). [log] writes all items in ONE transaction: caches the food when
 * it is new (base table copy, Open Food Facts product with its portions, accepted estimate as a user food), bumps
 * `lastUsedAt`, inserts the diary entries with their snapshot and provenance, and records every created/changed row in
 * `capture_writes`. [undo] reverses exactly those rows in one transaction (missing rows are skipped) so the DB is
 * identical to before (owner decision 8), then marks the capture CANCELLED and discards its open requests.
 */
class FoodLogWriter(private val db: LifeHubDatabase) {
    private val captures = db.captureDao()
    private val foods = db.foodDao()
    private val entries = db.foodEntryDao()
    private val portions = db.portionDao()
    private val pending = db.pendingInputDao()

    suspend fun log(captureId: String, date: LocalDate, mealAt: Instant, items: List<LogItem>, now: Instant): List<LogResult> = db.withTransaction {
        val capture = captures.get(captureId) ?: throw IllegalTransitionException("Unknown capture $captureId")
        if (!CaptureStateMachine.acceptsWrites(capture.status)) throw IllegalTransitionException("Capture is ${capture.status}")
        items.map { item ->
            val done = captures.writeByKey(captureId, item.idemKey)
            if (done != null) return@map LogResult.AlreadyLogged(item, entries.get(done.recordId))
            val food = ensureFood(captureId, item, now)
            val q = item.quantity
            val t = food.macrosFor(q.grams)
            val e = FoodEntry(
                id = UUID.randomUUID().toString(), date = date, foodId = food.id, name = food.name, brand = food.brand,
                grams = q.grams, kcal = t.kcal, proteinG = t.proteinG, carbsG = t.carbsG, fatG = t.fatG, createdAt = now,
                imageUrl = food.imageUrl, loggedAt = mealAt, source = item.food.source, captureId = captureId,
                servings = q.servings ?: q.count, portionLabel = q.portion?.label ?: q.measure?.label, portionGrams = q.portion?.grams ?: q.measure?.grams,
            )
            entries.insert(e)
            record(captureId, item.idemKey, TABLE_ENTRIES, e.id, WriteOp.INSERT, null, null, now)
            if (capture.mealAt == null) captures.upsert(capture.copy(mealAt = mealAt, updatedAt = now))
            LogResult.Logged(item, e)
        }
    }

    /** The user food to log against, created (and recorded) when it does not exist yet; `lastUsedAt` bumped once per capture. */
    private suspend fun ensureFood(captureId: String, item: LogItem, now: Instant): Food {
        val c = item.food
        val existing: Food? = when (c.source) {
            EntrySource.HISTORY -> c.foodId?.let { foods.get(it) }
            EntrySource.BASE_TABLE -> c.baseFoodId?.let { foods.byBaseId(it) }
            EntrySource.OPEN_FOOD_FACTS -> c.barcode?.let { foods.getByBarcode(it) }
            EntrySource.LLM_ESTIMATE -> c.foodId?.let { foods.get(it) }
        } ?: c.foodId?.let { foods.get(it) }
        if (existing != null) {
            if (captures.write(captureId, TABLE_FOODS, existing.id) == null) {
                record(captureId, null, TABLE_FOODS, existing.id, WriteOp.UPDATE, lastUsedJson(existing.lastUsedAt), lastUsedJson(now), now)
                foods.setLastUsed(existing.id, now)
                return existing.copy(lastUsedAt = now)
            }
            return existing
        }
        if (c.source == EntrySource.HISTORY) throw IllegalStateException("food ${c.foodId} no longer exists")
        val food = Food(
            id = c.foodId ?: UUID.randomUUID().toString(),
            source = when (c.source) { EntrySource.OPEN_FOOD_FACTS -> FoodSource.OFF; EntrySource.LLM_ESTIMATE -> FoodSource.ESTIMATE; else -> FoodSource.BASE },
            barcode = c.barcode, name = item.name?.takeIf { c.source == EntrySource.LLM_ESTIMATE } ?: c.name, brand = c.brand,
            kcal100 = c.kcal100, protein100 = c.protein100, carbs100 = c.carbs100, fat100 = c.fat100,
            servingSizeG = null, favorite = false, lastUsedAt = now, imageUrl = c.imageUrl,
            state = c.state, aliases = c.aliases.joinToString("|").ifEmpty { null }, baseFoodId = c.baseFoodId, incomplete = c.incomplete,
        )
        foods.insert(food)
        record(captureId, null, TABLE_FOODS, food.id, WriteOp.INSERT, null, null, now)
        c.portions.forEach { p ->
            val row = FoodPortion(UUID.randomUUID().toString(), food.id, p.label, p.grams, p.isDefault, p.kind, now)
            portions.upsertPortion(row)
            record(captureId, null, TABLE_PORTIONS, row.id, WriteOp.INSERT, null, null, now)
        }
        return food
    }

    /**
     * "Package weight missing" answered: saves [grams] as the PACKAGE portion of the product (caching the product first if
     * needed), recorded on [captureId] so Undo removes it too. Asked once: an existing PACKAGE portion is left as is.
     */
    suspend fun savePackageWeight(captureId: String, food: FoodCandidate, grams: Double, now: Instant): Food = db.withTransaction {
        val f = ensureFood(captureId, LogItem(food, Quantity.Resolved(0.0), idemKey = ""), now)
        val current = portions.portions(f.id)
        if (current.none { it.kind == PortionKind.PACKAGE }) {
            val row = FoodPortion(UUID.randomUUID().toString(), f.id, "package", grams, isDefault = current.none { it.isDefault } && f.servingSizeG == null, kind = PortionKind.PACKAGE, createdAt = now)
            portions.upsertPortion(row)
            record(captureId, null, TABLE_PORTIONS, row.id, WriteOp.INSERT, null, null, now)
        }
        f
    }

    suspend fun undo(captureId: String, now: Instant): UndoResult = db.withTransaction {
        val capture = captures.get(captureId) ?: return@withTransaction UndoResult.NotFound
        if (capture.status == CaptureStatus.CANCELLED) return@withTransaction UndoResult.AlreadyUndone
        val writes = captures.writes(captureId).asReversed()
        var removed = 0
        writes.filter { it.tableName == TABLE_ENTRIES && it.op == WriteOp.INSERT }.forEach { w ->
            if (entries.get(w.recordId) != null) { entries.delete(w.recordId); removed++ }
        }
        // Body weight (owner decision 5): a new day's row is deleted, an overwritten day gets its previous row back exactly
        // (muscle, fat and updatedAt included). A row the user changed after the capture is left alone.
        writes.filter { it.tableName == BodyLogWriter.TABLE_BODY }.forEach { w ->
            val date = java.time.LocalDate.parse(w.recordId)
            val written = w.newJson?.let(BodyLogWriter::parse)
            val current = db.bodyDao().get(date)
            if (current != null && written != null && current.weightKg == written.weightKg && current.updatedAt == written.updatedAt) {
                val prev = w.prevJson?.let(BodyLogWriter::parse)
                if (w.op == WriteOp.INSERT || prev == null) db.bodyDao().delete(date) else db.bodyDao().upsert(prev)
            }
        }
        writes.filter { it.tableName == TABLE_PORTIONS && it.op == WriteOp.INSERT }.forEach { portions.deletePortion(it.recordId) }
        writes.filter { it.tableName == TABLE_FOODS && it.op == WriteOp.UPDATE }.forEach { w ->
            val f = foods.get(w.recordId) ?: return@forEach
            // Restore only if nothing used the food after this capture.
            if (f.lastUsedAt?.toEpochMilli() == lastUsed(w.newJson)) foods.setLastUsed(f.id, lastUsed(w.prevJson)?.let(Instant::ofEpochMilli))
        }
        writes.filter { it.tableName == TABLE_FOODS && it.op == WriteOp.INSERT }.forEach { w ->
            // A food this capture created is removed unless another diary entry uses it meanwhile.
            if (foods.get(w.recordId) != null && entries.countForFood(w.recordId) == 0) foods.delete(w.recordId)
        }
        pending.byCapture(captureId).filter { it.status == PendingStatus.OPEN }
            .forEach { pending.update(it.copy(status = PendingStatus.DISCARDED, resolvedAt = now)) }
        captures.upsert(capture.copy(status = CaptureStatus.CANCELLED, updatedAt = now))
        UndoResult.Undone(removed)
    }

    private suspend fun record(captureId: String, idemKey: String?, table: String, recordId: String, op: WriteOp, prev: String?, new: String?, now: Instant) {
        captures.insertWrite(CaptureWrite(UUID.randomUUID().toString(), captureId, idemKey, table, recordId, op, prev, new, now))
    }

    private fun lastUsedJson(at: Instant?) = buildJsonObject { put("lastUsedAt", at?.toEpochMilli()?.let(::JsonPrimitive) ?: JsonNull) }.toString()
    private fun lastUsed(json: String?): Long? = json?.let {
        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject["lastUsedAt"]?.jsonPrimitive?.longOrNull }.getOrNull()
    }

    companion object {
        const val TABLE_ENTRIES = "food_entries"
        const val TABLE_FOODS = "foods"
        const val TABLE_PORTIONS = "food_portions"
    }
}
