package com.lifehub.app.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.data.repo.*
import com.lifehub.app.mock.MockDataGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class ExportImportTest {
    private lateinit var db: LifeHubDatabase
    private lateinit var backup: BackupRepositoryImpl
    private lateinit var settings: SettingsRepository
    private val zone = ZoneId.of("UTC")
    private val at = ZonedDateTime.of(2026, 11, 12, 10, 0, 0, 0, zone)
    private val snap = MockDataGenerator.generate(LocalDate.of(2026, 11, 12), zone)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = LifeHubDatabase.inMemory(ctx)
        settings = SettingsRepository(ctx).also { it.clear() }
        backup = BackupRepositoryImpl(db, settings)
    }
    @After fun tearDown() { db.close() }

    @Test fun jsonRoundTrip() {
        assertEquals(snap, JsonCodec.decode(JsonCodec.encode(snap, at, "0.1.0")))
    }

    @Test fun jsonHeaderAndKeyOrder() {
        val text = JsonCodec.encode(snap, at, "0.1.0")
        val keys = listOf("schemaVersion", "exportedAt", "app", "readme", "units", "settings", "goals", "plans", "exercises", "sessions", "macros", "bodyEntries", "achievements", "foods", "foodEntries")
        val idx = keys.map { text.indexOf("\n    \"$it\"") }
        assertTrue(idx.all { it >= 0 }); assertEquals(idx.sorted(), idx)
        assertTrue(text.contains("\"fatPct\""))
    }

    @Test fun dbRoundTrip() = runBlocking {
        backup.replaceAll(snap)
        val s1 = backup.snapshot()
        val decoded = JsonCodec.decode(JsonCodec.encode(s1, at, "0.1.0"))
        backup.replaceAll(decoded)
        val s2 = backup.snapshot()
        fun DataSnapshot.norm() = copy(
            goals = goals.sortedBy { it.id }, links = links.sortedBy { it.goalId + it.planId }, plans = plans.sortedBy { it.id },
            planExercises = planExercises.sortedBy { it.id }, exercises = exercises.sortedBy { it.id },
            sessions = sessions.sortedBy { it.id }, sets = sets.sortedBy { it.id },
            macros = macros.sortedBy { it.date }, bodyEntries = bodyEntries.sortedBy { it.date }, achievements = achievements.sortedBy { it.id },
            foods = foods.sortedBy { it.id }, foodEntries = foodEntries.sortedBy { it.id },
        )
        assertEquals(s1.norm(), s2.norm())
        assertEquals(snap.sessions.size, s2.sessions.size)
        assertEquals(snap.sets.size, s2.sets.size)
        assertTrue(snap.achievements.isNotEmpty())
        assertEquals(snap.achievements.size, s2.achievements.size)
        assertTrue(snap.foodEntries.isNotEmpty() && snap.foods.isNotEmpty())
        assertEquals(snap.foodEntries.size, s2.foodEntries.size)
        assertEquals(snap.foods.size, s2.foods.size)
        backup.clearAll()
        assertTrue(backup.snapshot().achievements.isEmpty())
        assertTrue(backup.snapshot().foodEntries.isEmpty() && backup.snapshot().foods.isEmpty())
    }

    @Test fun rejectsBadSchemaAndGarbage() {
        val bad = JsonCodec.encode(snap, at, "x").replaceFirst("\"schemaVersion\": 6", "\"schemaVersion\": 99")
        assertThrows(ImportException::class.java) { JsonCodec.decode(bad) }
        assertThrows(ImportException::class.java) { JsonCodec.decode("not json") }
        assertThrows(ImportException::class.java) { JsonCodec.decode("{\"foo\":1}") }
    }

    @Test fun missingOptionalFieldsImport() {
        val minimal = """{"schemaVersion":1,"exportedAt":"2026-11-12T10:00:00Z","app":{},"readme":"r",
            "macros":[{"date":"2026-11-01","proteinG":100,"carbsG":200,"fatG":60,"kcal":1740,"updatedAt":0}]}"""
        val s = JsonCodec.decode(minimal)
        assertEquals(1, s.macros.size); assertTrue(s.macros[0].kcalIsAuto)
    }

    @Test fun v1ImportStillWorks() {
        val v1 = JsonCodec.encode(snap, at, "x").replaceFirst("\"schemaVersion\": 6", "\"schemaVersion\": 1")
        val s = JsonCodec.decode(v1)
        assertEquals(snap.achievements.size, s.achievements.size)
        val minimal = """{"schemaVersion":1,"exportedAt":"2026-11-12T10:00:00Z","app":{},"readme":"r"}"""
        assertTrue(JsonCodec.decode(minimal).achievements.isEmpty())
    }

    @Test fun v3ImportHasNoFood() {
        val v3 = JsonCodec.encode(snap, at, "x").replaceFirst("\"schemaVersion\": 6", "\"schemaVersion\": 3")
        val s = JsonCodec.decode(v3)   // unknown/extra keys are ignored; older files simply lack foods
        assertEquals(snap.macros.size, s.macros.size)
        val old = """{"schemaVersion":3,"exportedAt":"2026-11-12T10:00:00Z","app":{},"readme":"r"}"""
        val o = JsonCodec.decode(old)
        assertTrue(o.foods.isEmpty() && o.foodEntries.isEmpty()); assertNull(o.settings.kcalTarget)
    }

    @Test fun v5ImportMapsRemovedCategoriesAndDropsUnusedBuiltIns() {
        fun ex(id: String, name: String, cat: String, custom: Boolean) =
            """{"id":"$id","name":"$name","category":"$cat","defaultTargetType":"SECONDS","isCustom":$custom}"""
        val v5 = """{"schemaVersion":5,"exportedAt":"2026-11-12T10:00:00Z","app":{},"readme":"r",
            "plans":[{"id":"p1","name":"Pull","createdAt":0,"exercises":[
              {"id":"pe1","planId":"p1","exerciseId":"ex-front-lever","orderIndex":0,"sets":3,"targetType":"SECONDS","targetValue":10},
              {"id":"pe2","planId":"p1","exerciseId":"ex-l-sit","orderIndex":1,"sets":3,"targetType":"SECONDS","targetValue":10},
              {"id":"pe3","planId":"p1","exerciseId":"ex-lateral-raise","orderIndex":2,"sets":3,"targetType":"REPS","targetValue":10}]}],
            "exercises":[${ex("ex-front-lever", "Front lever", "ISOMETRIC", false)},${ex("ex-l-sit", "L-sit", "ISOMETRIC", false)},
              ${ex("ex-lateral-raise", "Lateral raise", "ACCESSORY", false)},${ex("ex-back-lever", "Back lever", "ISOMETRIC", false)},
              ${ex("c1", "Wall handstand", "ISOMETRIC", true)},${ex("c2", "Band curl", "ACCESSORY", true)}]}"""
        val byId = JsonCodec.decode(v5).exercises.associateBy { it.id }
        assertEquals(setOf("ex-front-lever", "ex-l-sit", "ex-lateral-raise", "c1", "c2"), byId.keys)   // unused built-in dropped
        assertTrue(byId.values.all { it.isCustom })
        assertEquals(com.lifehub.app.data.model.ExerciseCategory.PULL, byId.getValue("ex-front-lever").category)
        assertEquals(com.lifehub.app.data.model.ExerciseCategory.CORE, byId.getValue("ex-l-sit").category)
        assertEquals(com.lifehub.app.data.model.ExerciseCategory.PUSH, byId.getValue("ex-lateral-raise").category)
        assertEquals(com.lifehub.app.data.model.ExerciseCategory.CORE, byId.getValue("c1").category)
        assertEquals(com.lifehub.app.data.model.ExerciseCategory.PUSH, byId.getValue("c2").category)
    }

    @Test fun csvZipContents() {
        val out = ByteArrayOutputStream()
        CsvExporter.writeZip(snap, at, out)
        val names = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            generateSequence { z.nextEntry }.forEach { names[it.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(setOf("goals.csv", "goal_plan_links.csv", "plans.csv", "plan_exercises.csv", "exercises.csv", "sessions.csv",
            "session_sets.csv", "achievements.csv", "foods.csv", "food_entries.csv", "macros.csv", "body_entries.csv", "settings.csv", "README.txt"), names.keys)
        assertTrue(names["macros.csv"]!!.startsWith("date,proteinG,carbsG,fatG,kcal,kcalIsAuto\n"))
        assertEquals(snap.macros.size + 1, names["macros.csv"]!!.trimEnd().lines().size)
        assertTrue(names["food_entries.csv"]!!.startsWith("id,date,foodId,name,brand,grams,kcal,proteinG,carbsG,fatG,createdAt\n"))
        assertEquals(snap.foodEntries.size + 1, names["food_entries.csv"]!!.trimEnd().lines().size)
        assertEquals(snap.foods.size + 1, names["foods.csv"]!!.trimEnd().lines().size)
        assertEquals("a,\"b,\"\"c\"\"\",\n", CsvExporter.csv("a", "b,\"c\"", null))
    }
}
