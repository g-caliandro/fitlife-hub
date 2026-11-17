package com.lifehub.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.copilot.memory.UserMemoryStoreImpl
import com.lifehub.app.copilot.plan.DraftPayload
import com.lifehub.app.copilot.plan.PlanDraftStoreImpl
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.data.repo.ChatStoreImpl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Builds a real v9 database from schemas/9.json with rows in every pre-existing table (copilot tables included), then
 * opens it with Room through MIGRATION_9_10 (Room validates the v10 schema against 10.json): every old row is identical,
 * and the new `plan_drafts` and `user_memory` tables are empty and usable.
 */
@RunWith(RobolectricTestRunner::class)
class Migration9To10Test {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-9-10.db"

    private fun createV9(seed: SupportSQLiteDatabase.() -> Unit) {
        ctx.deleteDatabase(dbName)
        val schema = Json.parseToJsonElement(File("schemas/com.lifehub.app.data.db.LifeHubDatabase/9.json").readText())
            .jsonObject.getValue("database").jsonObject
        val callback = object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                schema.getValue("entities").jsonArray.forEach { e ->
                    val o = e.jsonObject
                    val table = o.getValue("tableName").jsonPrimitive.content
                    val sql = listOf(o.getValue("createSql")) + (o["indices"]?.jsonArray?.map { it.jsonObject.getValue("createSql") } ?: emptyList())
                    sql.forEach { db.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
                }
                schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                db.seed()
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ctx).name(dbName).callback(callback).build())
            .writableDatabase.close()
    }

    /** Every row of every table as text, to compare before/after the migration. */
    private fun dump(db: SupportSQLiteDatabase, tables: List<String>): Map<String, List<String>> = tables.associateWith { t ->
        db.query("SELECT * FROM `$t`").use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { i -> if (c.isNull(i)) "NULL" else c.getString(i) }) }.sorted()
        }
    }

    @Test fun existingDataUntouchedAndPhase6TablesUsable() { runBlocking {
        val d = LocalDate.of(2026, 11, 16).toEpochDay()
        createV9 {
            execSQL("INSERT INTO goals (id, name, notes, startDate, deadline, deadlineIsAuto, state, achievedAt, createdAt) VALUES ('g1', 'Muscle up', NULL, $d, ${d + 84}, 1, 'ACTIVE', NULL, 1)")
            execSQL("INSERT INTO exercises (id, name, category, defaultTargetType, isCustom, isHidden) VALUES ('e1', 'Pull-up', 'PULL', 'REPS', 1, 0)")
            execSQL("INSERT INTO plans (id, name, weekdays, notes, isArchived, createdAt, dayType, iconKey) VALUES ('p1', 'Pull day', '1,4', NULL, 0, 1, 'PULL', NULL)")
            execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, sets, targetType, targetValue, weightKg, restSeconds) VALUES ('pe1', 'p1', 'e1', 0, 4, 'REPS', 8, NULL, 90)")
            execSQL("INSERT INTO goal_plan_links (goalId, planId, sessionsNeeded) VALUES ('g1', 'p1', 24)")
            execSQL("INSERT INTO sessions (id, planId, planName, date, startedAt, endedAt, durationSec, status, rpe, notes, isManual, currentSetIndex, restEndsAt) VALUES ('s1', 'p1', 'Pull day', $d, 1, 2, 60, 'COMPLETED', 7, NULL, 0, 0, NULL)")
            execSQL("INSERT INTO session_sets (id, sessionId, exerciseId, exerciseName, exerciseOrder, setIndex, targetType, targetValue, actualReps, actualSeconds, weightKg, status, completedAt, restSeconds) VALUES ('ss1', 's1', 'e1', 'Pull-up', 0, 0, 'REPS', 8, 8, NULL, NULL, 'DONE', 2, 90)")
            execSQL("INSERT INTO foods (id, source, barcode, name, brand, kcal100, protein100, carbs100, fat100, servingSizeG, favorite, lastUsedAt, imageUrl, state, aliases, baseFoodId, incomplete) VALUES ('f1', 'OFF', '8001', 'Merluzzo gratinato', 'Findus', 150, 11, 9, 7.6, 125, 1, 5, NULL, NULL, 'il solito', NULL, 0)")
            execSQL("INSERT INTO food_portions (id, foodId, label, grams, isDefault, kind, createdAt) VALUES ('fp1', 'f1', 'package', 400, 1, 'PACKAGE', 3)")
            execSQL("INSERT INTO food_entries (id, date, foodId, name, brand, grams, kcal, proteinG, carbsG, fatG, createdAt, imageUrl, loggedAt, source, captureId, servings, portionLabel, portionGrams) VALUES ('fe1', $d, 'f1', 'Merluzzo gratinato', 'Findus', 200, 300, 22, 18, 15.2, 500, NULL, 500, 'HISTORY', 'c1', 0.5, 'package', 400)")
            execSQL("INSERT INTO quick_add_entries (id, date, name, kcal, proteinG, carbsG, fatG, kcalIsAuto, createdAt) VALUES ('q1', $d, 'Pizza', 800, NULL, NULL, NULL, 0, 600)")
            execSQL("INSERT INTO body_entries (date, weightKg, muscleKg, fatKg, updatedAt) VALUES ($d, 77.4, 35.0, NULL, 0)")
            execSQL("INSERT INTO achievements (id, kind, title, detail, value, unit, exerciseId, goalId, sessionId, achievedOn, createdAt, dedupeKey) VALUES ('a1', 'GOAL_ACHIEVED', 'Done', NULL, NULL, NULL, NULL, 'g1', NULL, $d, 1, 'k1')")
            execSQL("INSERT INTO chat_threads (id, title, origin, createdAt, updatedAt) VALUES ('t1', 'Chat', 'CHAT', 1, 2)")
            execSQL("INSERT INTO chat_messages (id, threadId, seq, role, text, toolCallsJson, toolCallId, toolName, cardType, cardJson, captureId, createdAt) VALUES ('m1', 't1', 0, 'USER', 'Ciao', NULL, NULL, NULL, NULL, NULL, 'c1', 2)")
            execSQL("INSERT INTO captures (id, threadId, origin, status, inputText, audioPath, transcript, attachedFoodId, zoneId, mealAt, outcome, errorCode, errorMessage, attempts, createdAt, updatedAt) VALUES ('c1', 't1', 'CHAT', 'DONE', 'Ciao', NULL, NULL, NULL, 'Europe/Rome', 500, 'ok', NULL, NULL, 1, 1, 2)")
            execSQL("INSERT INTO capture_writes (id, captureId, idemKey, tableName, recordId, op, prevJson, newJson, createdAt) VALUES ('w1', 'c1', 'k', 'food_entries', 'fe1', 'INSERT', NULL, NULL, 2)")
            execSQL("INSERT INTO pending_inputs (id, captureId, threadId, kind, prompt, optionsJson, itemJson, foodRef, status, answerJson, createdAt, resolvedAt, notifiedAt) VALUES ('pi1', 'c1', 't1', 'QUANTITY', 'Quanto?', '[]', NULL, NULL, 'OPEN', NULL, 1, NULL, NULL)")
        }
        val tables = listOf("goals", "exercises", "plans", "plan_exercises", "goal_plan_links", "sessions", "session_sets", "foods",
            "food_portions", "food_entries", "quick_add_entries", "body_entries", "achievements", "chat_threads", "chat_messages",
            "captures", "capture_writes", "pending_inputs", "household_measures", "base_foods")
        val before = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ctx).name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()).let { h -> dump(h.readableDatabase, tables).also { h.close() } }

        val db = Room.databaseBuilder(ctx, LifeHubDatabase::class.java, dbName)
            .addMigrations(LifeHubDatabase.MIGRATION_9_10).allowMainThreadQueries().build()
        assertEquals(before, dump(db.openHelper.readableDatabase, tables))
        assertEquals("Ciao", ChatStoreImpl(db).messages("t1").single().text)
        assertEquals(setOf(1, 4), db.planDao().getAll().single().weekdays)

        // New tables: empty, then usable.
        assertTrue(db.planDraftDao().all().isEmpty()); assertTrue(db.userMemoryDao().all().isEmpty())
        val draft = PlanDraftStoreImpl(db).propose("Bozza", DraftPayload(null, emptyList()), "t1", "c1", Instant.EPOCH)
        assertEquals(PlanDraftStatus.DRAFT, db.planDraftDao().get(draft.id)!!.status)
        UserMemoryStoreImpl(db).save("Attrezzatura", "sbarra, anelli", Instant.EPOCH)
        assertEquals("sbarra, anelli", db.userMemoryDao().get("attrezzatura")!!.value)
        db.close()
        ctx.deleteDatabase(dbName)
    } }
}
