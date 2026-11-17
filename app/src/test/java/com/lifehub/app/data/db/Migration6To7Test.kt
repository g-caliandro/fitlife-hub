package com.lifehub.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Builds a real v6 database from schemas/6.json, then opens it with Room through MIGRATION_6_7 (Room validates the
 * v7 schema): every per-day macro_days row becomes one quick_add_entries row with the same values.
 */
@RunWith(RobolectricTestRunner::class)
class Migration6To7Test {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-6-7.db"

    private fun createV6(seed: SupportSQLiteDatabase.() -> Unit) {
        ctx.deleteDatabase(dbName)
        val schema = Json.parseToJsonElement(File("schemas/com.lifehub.app.data.db.LifeHubDatabase/6.json").readText())
            .jsonObject.getValue("database").jsonObject
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
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

    @Test fun macroDaysBecomeQuickAddEntries() { runBlocking {
        val d1 = LocalDate.of(2026, 11, 10); val d2 = LocalDate.of(2026, 11, 11)
        createV6 {
            execSQL("INSERT INTO macro_days (date, proteinG, carbsG, fatG, kcal, kcalIsAuto, updatedAt) VALUES (${d1.toEpochDay()}, 150, 240, 70, 2190, 1, 1000)")
            execSQL("INSERT INTO macro_days (date, proteinG, carbsG, fatG, kcal, kcalIsAuto, updatedAt) VALUES (${d2.toEpochDay()}, 12, 25, 4, 300, 0, 2000)")
            // Other data is untouched.
            execSQL("INSERT INTO food_entries (id, date, foodId, name, brand, grams, kcal, proteinG, carbsG, fatG, createdAt, imageUrl) VALUES ('fe1', ${d1.toEpochDay()}, NULL, 'Oats', NULL, 70, 260, 9, 42, 5, 500, NULL)")
            execSQL("INSERT INTO body_entries (date, weightKg, muscleKg, fatKg, updatedAt) VALUES (${d1.toEpochDay()}, 77.0, NULL, NULL, 0)")
        }
        val db = Room.databaseBuilder(ctx, LifeHubDatabase::class.java, dbName)
            .addMigrations(LifeHubDatabase.MIGRATION_6_7, LifeHubDatabase.MIGRATION_7_8, LifeHubDatabase.MIGRATION_8_9, LifeHubDatabase.MIGRATION_9_10).allowMainThreadQueries().build()
        val all = db.quickAddDao().getAll()
        assertEquals(2, all.size)
        val a = all.first { it.date == d1 }
        assertEquals("macro-${d1.toEpochDay()}", a.id)
        assertNull(a.name)
        assertEquals(listOf(150, 240, 70, 2190), listOf(a.proteinG, a.carbsG, a.fatG, a.kcal))
        assertTrue(a.kcalIsAuto); assertEquals(Instant.ofEpochMilli(1000), a.createdAt)
        val b = all.first { it.date == d2 }
        assertEquals(300, b.kcal); assertFalse(b.kcalIsAuto); assertEquals(12, b.proteinG)
        // New capabilities after the migration: several entries per day, unknown macros.
        db.quickAddDao().upsert(com.lifehub.app.data.model.QuickAddEntry("n", d1, "Pizza out", 1000, null, null, null, false, Instant.ofEpochMilli(3000)))
        assertEquals(2, db.quickAddDao().getDay(d1).size)
        assertNull(db.quickAddDao().get("n")!!.proteinG)
        assertEquals(1, db.foodEntryDao().getAll().size)
        assertEquals(1, db.bodyDao().getAll().size)
        // The old table is gone.
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='macro_days'").use { assertEquals(0, it.count) }
        db.close()
        ctx.deleteDatabase(dbName)
    } }
}
