package com.lifehub.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.model.ExerciseCategory
import com.lifehub.app.data.model.ExerciseCategory.CORE
import com.lifehub.app.data.model.ExerciseCategory.LEGS
import com.lifehub.app.data.model.ExerciseCategory.PULL
import com.lifehub.app.data.model.ExerciseCategory.PUSH
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

/** Builds a real v5 database from schemas/5.json, then opens it with Room through MIGRATION_5_6 (Room validates the schema). */
@RunWith(RobolectricTestRunner::class)
class Migration5To6Test {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-5-6.db"

    private fun createV5(seed: SupportSQLiteDatabase.() -> Unit) {
        ctx.deleteDatabase(dbName)
        val schema = Json.parseToJsonElement(File("schemas/com.lifehub.app.data.db.LifeHubDatabase/5.json").readText())
            .jsonObject.getValue("database").jsonObject
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
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

    private fun SupportSQLiteDatabase.ex(id: String, name: String, category: String, custom: Boolean, type: String = "SECONDS") =
        execSQL("INSERT INTO exercises (id, name, category, defaultTargetType, isCustom, isHidden) VALUES ('$id', '$name', '$category', '$type', ${if (custom) 1 else 0}, 0)")

    @Test fun migratesCategoriesAndPrunesUnusedBuiltIns() { runBlocking {
        createV5 {
            ex("ex-pull-up", "Pull-up", "PULL", false, "REPS")          // in a plan
            ex("ex-squat", "Squat", "LEGS", false, "REPS")              // unused -> deleted
            ex("ex-front-lever", "Front lever", "ISOMETRIC", false)     // in a session -> PULL
            ex("ex-planche-lean", "Planche lean", "ISOMETRIC", false)   // in a plan -> PUSH
            ex("ex-l-sit", "L-sit", "ISOMETRIC", false)                 // only in an achievement -> CORE, kept
            ex("ex-face-pull", "Face pull", "ACCESSORY", false, "REPS") // in a plan -> PULL
            ex("ex-lateral-raise", "Lateral raise", "ACCESSORY", false, "REPS") // unused -> deleted
            ex("ex-dead-hang", "Dead hang", "ISOMETRIC", false)         // unused -> deleted
            ex("c1", "Wall handstand", "ISOMETRIC", true)               // custom -> CORE
            ex("c2", "Band curl", "ACCESSORY", true, "REPS")            // custom -> PUSH
            ex("c3", "BICEPS CURL", "ACCESSORY", true, "REPS")          // name match is case-insensitive -> PULL
            ex("c4", "Nordic", "LEGS", true, "REPS")                    // untouched
            execSQL("INSERT INTO plans (id, name, weekdays, notes, isArchived, createdAt) VALUES ('p1', 'Pull', '1', NULL, 0, 0)")
            listOf("ex-pull-up", "ex-planche-lean", "ex-face-pull").forEachIndexed { i, e ->
                execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, sets, targetType, targetValue, weightKg, restSeconds) VALUES ('pe$i', 'p1', '$e', $i, 3, 'REPS', 5, NULL, 90)")
            }
            execSQL("INSERT INTO sessions (id, planId, planName, date, startedAt, endedAt, durationSec, status, rpe, notes, isManual, currentSetIndex, restEndsAt) VALUES ('s1', 'p1', 'Pull', 20000, 0, 60, 60, 'COMPLETED', NULL, NULL, 0, 0, NULL)")
            execSQL("INSERT INTO session_sets (id, sessionId, exerciseId, exerciseName, exerciseOrder, setIndex, targetType, targetValue, actualReps, actualSeconds, weightKg, status, completedAt, restSeconds) VALUES ('ss1', 's1', 'ex-front-lever', 'Front lever', 0, 0, 'SECONDS', 10, NULL, 12, NULL, 'DONE', 60, 90)")
            execSQL("INSERT INTO achievements (id, kind, title, detail, value, unit, exerciseId, goalId, sessionId, achievedOn, createdAt, dedupeKey) VALUES ('a1', 'PR_HOLD', 'L-sit', NULL, 20, 's', 'ex-l-sit', NULL, NULL, 20000, 0, 'PR:l-sit')")
            execSQL("INSERT INTO achievements (id, kind, title, detail, value, unit, exerciseId, goalId, sessionId, achievedOn, createdAt, dedupeKey) VALUES ('a2', 'SESSION_MILESTONE', '10 sessions', NULL, 10, NULL, NULL, NULL, NULL, 20000, 0, 'MS:10')")
        }
        val db = Room.databaseBuilder(ctx, LifeHubDatabase::class.java, dbName)
            .addMigrations(LifeHubDatabase.MIGRATION_5_6, LifeHubDatabase.MIGRATION_6_7, LifeHubDatabase.MIGRATION_7_8, LifeHubDatabase.MIGRATION_8_9).allowMainThreadQueries().build()
        val byId = db.exerciseDao().getAll().associateBy { it.id }
        val expected = mapOf(
            "ex-pull-up" to PULL, "ex-front-lever" to PULL, "ex-planche-lean" to PUSH, "ex-l-sit" to CORE, "ex-face-pull" to PULL,
            "c1" to CORE, "c2" to PUSH, "c3" to PULL, "c4" to LEGS,
        )
        assertEquals(expected, byId.mapValues { it.value.category })
        assertTrue(byId.values.all { it.isCustom })
        assertEquals(3, db.planDao().getAllExercises().size)
        assertEquals(1, db.sessionDao().getAllSets().size)
        assertEquals(2, db.achievementDao().getAll().size)
        db.close()
        ctx.deleteDatabase(dbName)
    } }

    @Test fun legacyCategoryMapping() {
        fun m(raw: String, name: String) = ExerciseCategory.fromLegacy(raw, name)
        assertEquals(PULL, m("PULL", "Anything"))
        assertEquals(LEGS, m("LEGS", "Front lever"))   // current categories are kept as is
        for (n in listOf("Tuck front lever", "Advanced tuck front lever", "Front lever", "Tuck back lever", "Back lever", "Dead hang")) assertEquals(n, PULL, m("ISOMETRIC", n))
        for (n in listOf("Planche lean", "Tuck planche", "Support hold")) assertEquals(n, PUSH, m("ISOMETRIC", n))
        for (n in listOf("L-sit", "Hollow body hold", "My hold")) assertEquals(n, CORE, m("ISOMETRIC", n))
        assertEquals(PULL, m("ACCESSORY", "Biceps curl")); assertEquals(PULL, m("ACCESSORY", " face PULL "))
        assertEquals(PUSH, m("ACCESSORY", "Triceps pushdown")); assertEquals(PUSH, m("ACCESSORY", "Lateral raise"))
        assertEquals(PUSH, m("ACCESSORY", "Band curl"))
        assertEquals(CORE, m("GARBAGE", "x"))
    }
}
