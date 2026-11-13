package com.lifehub.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lifehub.app.data.db.dao.AchievementDao
import com.lifehub.app.data.db.dao.BodyDao
import com.lifehub.app.data.db.dao.ExerciseDao
import com.lifehub.app.data.db.dao.FoodDao
import com.lifehub.app.data.db.dao.FoodEntryDao
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.db.dao.GoalDao
import com.lifehub.app.data.db.dao.MacroDao
import com.lifehub.app.data.db.dao.PlanDao
import com.lifehub.app.data.db.dao.SessionDao
import com.lifehub.app.data.model.Achievement
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.ExerciseCategory
import com.lifehub.app.data.model.Goal
import com.lifehub.app.data.model.GoalPlanLink
import com.lifehub.app.data.model.MacroDay
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.PlanExercise
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.WorkoutSession

@Database(
    entities = [Goal::class, Plan::class, GoalPlanLink::class, Exercise::class, PlanExercise::class,
        WorkoutSession::class, SessionSet::class, MacroDay::class, BodyEntry::class, Achievement::class, Food::class, FoodEntry::class],
    version = 6, exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LifeHubDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun planDao(): PlanDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun macroDao(): MacroDao
    abstract fun bodyDao(): BodyDao
    abstract fun achievementDao(): AchievementDao
    abstract fun foodDao(): FoodDao
    abstract fun foodEntryDao(): FoodEntryDao

    companion object {
        fun build(context: Context): LifeHubDatabase =
            Room.databaseBuilder(context, LifeHubDatabase::class.java, "lifehub.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

        fun inMemory(context: Context): LifeHubDatabase =
            Room.inMemoryDatabaseBuilder(context, LifeHubDatabase::class.java).allowMainThreadQueries().build()

        /**
         * v6: no built-in exercise library and only 4 categories (schema unchanged, data only).
         * ISOMETRIC/ACCESSORY are remapped (ExerciseCategory.fromLegacy rules); built-ins (isCustom = 0) that nothing
         * references are deleted, the referenced ones become the user's own (isCustom = 1) so history is kept.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun names(l: List<String>) = l.joinToString(",") { "'" + it.replace("'", "''") + "'" }
                val legacy = "category NOT IN ('PULL','PUSH','CORE','LEGS')"
                // `name` has NOCASE collation, so these IN comparisons are case-insensitive.
                db.execSQL("UPDATE exercises SET category = 'PULL' WHERE $legacy AND name IN (${names(ExerciseCategory.LEGACY_PULL_NAMES)})")
                db.execSQL("UPDATE exercises SET category = 'PUSH' WHERE $legacy AND name IN (${names(ExerciseCategory.LEGACY_PUSH_NAMES)})")
                db.execSQL("UPDATE exercises SET category = 'PUSH' WHERE category = 'ACCESSORY'")
                db.execSQL("UPDATE exercises SET category = 'CORE' WHERE $legacy")
                db.execSQL(
                    """DELETE FROM exercises WHERE isCustom = 0
                       AND id NOT IN (SELECT exerciseId FROM plan_exercises)
                       AND id NOT IN (SELECT exerciseId FROM session_sets)
                       AND id NOT IN (SELECT exerciseId FROM achievements WHERE exerciseId IS NOT NULL)""",
                )
                db.execSQL("UPDATE exercises SET isCustom = 1 WHERE isCustom = 0")
            }
        }

        /** v5: foods/food_entries.imageUrl (Open Food Facts image), plans.iconKey (all nullable). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `imageUrl` TEXT")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `imageUrl` TEXT")
                db.execSQL("ALTER TABLE `plans` ADD COLUMN `iconKey` TEXT")
            }
        }

        /** v4: foods + food_entries (Food feature). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `foods` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `barcode` TEXT, `name` TEXT NOT NULL, `brand` TEXT, `kcal100` REAL NOT NULL, `protein100` REAL NOT NULL, `carbs100` REAL NOT NULL, `fat100` REAL NOT NULL, `servingSizeG` REAL, `favorite` INTEGER NOT NULL, `lastUsedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_barcode` ON `foods` (`barcode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_lastUsedAt` ON `foods` (`lastUsedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `food_entries` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `foodId` TEXT, `name` TEXT NOT NULL, `brand` TEXT, `grams` REAL NOT NULL, `kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_date` ON `food_entries` (`date`)")
            }
        }

        /** v3: plans.dayType (nullable). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plans` ADD COLUMN `dayType` TEXT")
            }
        }

        /** v2: achievements table (F-ACH-1). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `achievements` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `detail` TEXT, `value` REAL, `unit` TEXT, `exerciseId` TEXT, `goalId` TEXT, `sessionId` TEXT, `achievedOn` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `dedupeKey` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_achievements_dedupeKey` ON `achievements` (`dedupeKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_achievements_achievedOn` ON `achievements` (`achievedOn`)")
            }
        }
    }
}
