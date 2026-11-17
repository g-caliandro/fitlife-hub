package com.lifehub.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.repo.ChatStoreImpl
import com.lifehub.app.data.repo.NewChatMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Builds a real v7 database from schemas/7.json with rows in the pre-existing tables, then opens it with Room through
 * MIGRATION_7_8 (Room validates the v8 schema): old data is untouched and the new chat tables work.
 */
@RunWith(RobolectricTestRunner::class)
class Migration7To8Test {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-7-8.db"

    private fun createV7(seed: SupportSQLiteDatabase.() -> Unit) {
        ctx.deleteDatabase(dbName)
        val schema = Json.parseToJsonElement(File("schemas/com.lifehub.app.data.db.LifeHubDatabase/7.json").readText())
            .jsonObject.getValue("database").jsonObject
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
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

    @Test fun existingDataUntouchedAndChatTablesUsable() { runBlocking {
        val d = LocalDate.of(2026, 11, 16).toEpochDay()
        createV7 {
            execSQL("INSERT INTO goals (id, name, notes, startDate, deadline, deadlineIsAuto, state, achievedAt, createdAt) VALUES ('g1', 'Muscle up', NULL, $d, ${d + 84}, 1, 'ACTIVE', NULL, 1)")
            execSQL("INSERT INTO exercises (id, name, category, defaultTargetType, isCustom, isHidden) VALUES ('e1', 'Pull-up', 'PULL', 'REPS', 1, 0)")
            execSQL("INSERT INTO plans (id, name, weekdays, notes, isArchived, createdAt, dayType, iconKey) VALUES ('p1', 'Pull day', '1,4', NULL, 0, 1, 'PULL', NULL)")
            execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, sets, targetType, targetValue, weightKg, restSeconds) VALUES ('pe1', 'p1', 'e1', 0, 4, 'REPS', 8, NULL, 90)")
            execSQL("INSERT INTO goal_plan_links (goalId, planId, sessionsNeeded) VALUES ('g1', 'p1', 24)")
            execSQL("INSERT INTO foods (id, source, barcode, name, brand, kcal100, protein100, carbs100, fat100, servingSizeG, favorite, lastUsedAt, imageUrl) VALUES ('f1', 'CUSTOM', NULL, 'Rice', NULL, 360, 7, 79, 0.6, NULL, 0, 5, NULL)")
            execSQL("INSERT INTO food_entries (id, date, foodId, name, brand, grams, kcal, proteinG, carbsG, fatG, createdAt, imageUrl) VALUES ('fe1', $d, 'f1', 'Rice', NULL, 80, 288, 5.6, 63.2, 0.48, 500, NULL)")
            execSQL("INSERT INTO quick_add_entries (id, date, name, kcal, proteinG, carbsG, fatG, kcalIsAuto, createdAt) VALUES ('q1', $d, 'Pizza', 800, NULL, NULL, NULL, 0, 600)")
            execSQL("INSERT INTO body_entries (date, weightKg, muscleKg, fatKg, updatedAt) VALUES ($d, 77.4, 35.0, NULL, 0)")
        }
        val db = Room.databaseBuilder(ctx, LifeHubDatabase::class.java, dbName)
            .addMigrations(LifeHubDatabase.MIGRATION_7_8, LifeHubDatabase.MIGRATION_8_9, LifeHubDatabase.MIGRATION_9_10).allowMainThreadQueries().build()
        assertEquals("Muscle up", db.goalDao().get("g1")!!.name)
        assertEquals(setOf(1, 4), db.planDao().getAll().single().weekdays)
        assertEquals(8, db.planDao().getAllExercises().single().targetValue)
        assertEquals(1, db.foodDao().getAll().size)
        val fe = db.foodEntryDao().getAll().single()
        assertEquals(288.0, fe.kcal, 0.0); assertEquals(5.6, fe.proteinG, 0.0)
        val q = db.quickAddDao().getAll().single()
        assertEquals(800, q.kcal); assertNull(q.proteinG)
        assertEquals(77.4, db.bodyDao().getAll().single().weightKg, 0.0)

        // New tables: insert, read back, cascade on thread delete.
        val store = ChatStoreImpl(db)
        val t = store.createThread("Test", ChatOrigin.CHAT, Instant.ofEpochMilli(1000))
        store.append(t.id, NewChatMessage(ChatRole.USER, "Ciao"), Instant.ofEpochMilli(2000))
        store.append(t.id, NewChatMessage(ChatRole.ASSISTANT, "Ciao!"), Instant.ofEpochMilli(3000))
        assertEquals(listOf(0, 1), store.messages(t.id).map { it.seq })
        store.deleteThread(t.id)
        assertEquals(0, store.messages(t.id).size)
        db.close()
        ctx.deleteDatabase(dbName)
    } }
}
