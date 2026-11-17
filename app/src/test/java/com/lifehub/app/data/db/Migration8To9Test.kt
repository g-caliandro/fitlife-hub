package com.lifehub.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.copilot.food.HouseholdMeasureSeed
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.PortionKind
import com.lifehub.app.data.repo.ChatStoreImpl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Builds a real v8 database from schemas/8.json with rows in every pre-existing table (chat included), then opens it with
 * Room through MIGRATION_8_9 (Room validates the v9 schema): old data is untouched, new columns read null / false, the
 * new tables work and the default household measures are seeded.
 */
@RunWith(RobolectricTestRunner::class)
class Migration8To9Test {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-8-9.db"

    private fun createV8(seed: SupportSQLiteDatabase.() -> Unit) {
        ctx.deleteDatabase(dbName)
        val schema = Json.parseToJsonElement(File("schemas/com.lifehub.app.data.db.LifeHubDatabase/8.json").readText())
            .jsonObject.getValue("database").jsonObject
        val callback = object : SupportSQLiteOpenHelper.Callback(8) {
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

    @Test fun existingDataUntouchedAndPhase3TablesUsable() { runBlocking {
        val d = LocalDate.of(2026, 11, 16).toEpochDay()
        createV8 {
            execSQL("INSERT INTO goals (id, name, notes, startDate, deadline, deadlineIsAuto, state, achievedAt, createdAt) VALUES ('g1', 'Muscle up', NULL, $d, ${d + 84}, 1, 'ACTIVE', NULL, 1)")
            execSQL("INSERT INTO exercises (id, name, category, defaultTargetType, isCustom, isHidden) VALUES ('e1', 'Pull-up', 'PULL', 'REPS', 1, 0)")
            execSQL("INSERT INTO plans (id, name, weekdays, notes, isArchived, createdAt, dayType, iconKey) VALUES ('p1', 'Pull day', '1,4', NULL, 0, 1, 'PULL', NULL)")
            execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, sets, targetType, targetValue, weightKg, restSeconds) VALUES ('pe1', 'p1', 'e1', 0, 4, 'REPS', 8, NULL, 90)")
            execSQL("INSERT INTO goal_plan_links (goalId, planId, sessionsNeeded) VALUES ('g1', 'p1', 24)")
            execSQL("INSERT INTO foods (id, source, barcode, name, brand, kcal100, protein100, carbs100, fat100, servingSizeG, favorite, lastUsedAt, imageUrl) VALUES ('f1', 'OFF', '8001', 'Merluzzo gratinato', 'Findus', 150, 11, 9, 7.6, 125, 1, 5, 'http://x')")
            execSQL("INSERT INTO food_entries (id, date, foodId, name, brand, grams, kcal, proteinG, carbsG, fatG, createdAt, imageUrl) VALUES ('fe1', $d, 'f1', 'Merluzzo gratinato', 'Findus', 200, 300, 22, 18, 15.2, 500, NULL)")
            execSQL("INSERT INTO quick_add_entries (id, date, name, kcal, proteinG, carbsG, fatG, kcalIsAuto, createdAt) VALUES ('q1', $d, 'Pizza', 800, NULL, NULL, NULL, 0, 600)")
            execSQL("INSERT INTO body_entries (date, weightKg, muscleKg, fatKg, updatedAt) VALUES ($d, 77.4, 35.0, NULL, 0)")
            execSQL("INSERT INTO achievements (id, kind, title, detail, value, unit, exerciseId, goalId, sessionId, achievedOn, createdAt, dedupeKey) VALUES ('a1', 'GOAL_ACHIEVED', 'Done', NULL, NULL, NULL, NULL, 'g1', NULL, $d, 1, 'k1')")
            execSQL("INSERT INTO chat_threads (id, title, origin, createdAt, updatedAt) VALUES ('t1', 'Chat', 'CHAT', 1, 2)")
            execSQL("INSERT INTO chat_messages (id, threadId, seq, role, text, toolCallsJson, toolCallId, toolName, cardType, cardJson, captureId, createdAt) VALUES ('m1', 't1', 0, 'USER', 'Ciao', NULL, NULL, NULL, NULL, NULL, NULL, 2)")
        }
        val db = Room.databaseBuilder(ctx, LifeHubDatabase::class.java, dbName)
            .addMigrations(LifeHubDatabase.MIGRATION_8_9, LifeHubDatabase.MIGRATION_9_10).allowMainThreadQueries().build()

        assertEquals("Muscle up", db.goalDao().get("g1")!!.name)
        assertEquals(setOf(1, 4), db.planDao().getAll().single().weekdays)
        assertEquals(8, db.planDao().getAllExercises().single().targetValue)
        assertEquals(1, db.achievementDao().getAll().size)
        assertEquals(800, db.quickAddDao().getAll().single().kcal)
        assertEquals(77.4, db.bodyDao().getAll().single().weightKg, 0.0)
        assertEquals("Ciao", ChatStoreImpl(db).messages("t1").single().text)

        val food = db.foodDao().getAll().single()
        assertEquals("Merluzzo gratinato", food.name); assertEquals(125.0, food.servingSizeG!!, 0.0); assertEquals(true, food.favorite)
        assertNull(food.state); assertNull(food.aliases); assertNull(food.baseFoodId); assertFalse(food.incomplete)
        val fe = db.foodEntryDao().getAll().single()
        assertEquals(200.0, fe.grams, 0.0); assertEquals(300.0, fe.kcal, 0.0); assertEquals(15.2, fe.fatG, 0.0)
        assertNull(fe.loggedAt); assertNull(fe.source); assertNull(fe.captureId); assertNull(fe.servings); assertNull(fe.portionLabel)

        // Seeded household measures, exactly the isolated seed list.
        assertEquals(HouseholdMeasureSeed.measures().map { it.id to it.grams }.toSet(), db.portionDao().measures().map { it.id to it.grams }.toSet())
        assertEquals(9.0, db.portionDao().measures().single { it.label == "cucchiaio" && it.baseFoodId == "sr-171413" }.grams, 0.0)

        // New tables: portions (FK to foods, cascade), captures + writes, pending inputs, base foods.
        db.portionDao().upsertPortion(FoodPortion("p1", "f1", "package", 400.0, true, PortionKind.PACKAGE, Instant.EPOCH))
        assertEquals(1, db.portionDao().portions("f1").size)
        val capture = com.lifehub.app.copilot.capture.CaptureStoreImpl(db).create("t1", ChatOrigin.CHAT, "x", java.time.ZoneId.of("Europe/Rome"), Instant.EPOCH)
        assertEquals(CaptureStatus.RECORDED, db.captureDao().get(capture.id)!!.status)
        assertEquals(0, db.baseFoodDao().count())
        db.foodDao().delete("f1")
        assertEquals(0, db.portionDao().portions("f1").size)
        db.close()
        ctx.deleteDatabase(dbName)
    } }
}
