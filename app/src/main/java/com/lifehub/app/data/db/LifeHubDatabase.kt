package com.lifehub.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lifehub.app.copilot.food.HouseholdMeasureSeed
import com.lifehub.app.data.db.dao.AchievementDao
import com.lifehub.app.data.db.dao.BodyDao
import com.lifehub.app.data.db.dao.BaseFoodDao
import com.lifehub.app.data.db.dao.CaptureDao
import com.lifehub.app.data.db.dao.ChatDao
import com.lifehub.app.data.db.dao.PendingInputDao
import com.lifehub.app.data.db.dao.PortionDao
import com.lifehub.app.data.model.BaseFood
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureWrite
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.HouseholdMeasure
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.db.dao.ExerciseDao
import com.lifehub.app.data.db.dao.FoodDao
import com.lifehub.app.data.db.dao.FoodEntryDao
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.db.dao.GoalDao
import com.lifehub.app.data.db.dao.QuickAddDao
import com.lifehub.app.data.db.dao.PlanDao
import com.lifehub.app.data.db.dao.SessionDao
import com.lifehub.app.data.model.Achievement
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.ChatMessage
import com.lifehub.app.data.model.ChatThread
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.ExerciseCategory
import com.lifehub.app.data.model.Goal
import com.lifehub.app.data.model.GoalPlanLink
import com.lifehub.app.data.model.QuickAddEntry
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.PlanExercise
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.WorkoutSession

@Database(
    entities = [Goal::class, Plan::class, GoalPlanLink::class, Exercise::class, PlanExercise::class,
        WorkoutSession::class, SessionSet::class, QuickAddEntry::class, BodyEntry::class, Achievement::class, Food::class, FoodEntry::class,
        ChatThread::class, ChatMessage::class,
        Capture::class, CaptureWrite::class, PendingInput::class, FoodPortion::class, HouseholdMeasure::class, BaseFood::class],
    version = 9, exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LifeHubDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun planDao(): PlanDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun quickAddDao(): QuickAddDao
    abstract fun bodyDao(): BodyDao
    abstract fun achievementDao(): AchievementDao
    abstract fun foodDao(): FoodDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun chatDao(): ChatDao
    abstract fun captureDao(): CaptureDao
    abstract fun pendingInputDao(): PendingInputDao
    abstract fun portionDao(): PortionDao
    abstract fun baseFoodDao(): BaseFoodDao

    companion object {
        fun build(context: Context): LifeHubDatabase =
            Room.databaseBuilder(context, LifeHubDatabase::class.java, "lifehub.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(SEED_CALLBACK).build()

        fun inMemory(context: Context): LifeHubDatabase =
            Room.inMemoryDatabaseBuilder(context, LifeHubDatabase::class.java).allowMainThreadQueries().addCallback(SEED_CALLBACK).build()

        /** Fresh installs get the default household measures (upgrades get them from MIGRATION_8_9). */
        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                HouseholdMeasureSeed.insertSql().forEach(db::execSQL)
            }
        }

        /**
         * v9 (copilot phase 3): captures + capture_writes (Undo), pending_inputs, food_portions, household_measures
         * (seeded with [HouseholdMeasureSeed]), base_foods (filled at runtime from the bundled asset by BaseFoodRepository);
         * nullable/defaulted columns on foods (state, aliases, baseFoodId, incomplete) and food_entries (loggedAt, source,
         * captureId, servings, portionLabel, portionGrams). Existing rows are untouched (new columns read NULL / 0).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `state` TEXT")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `aliases` TEXT")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `baseFoodId` TEXT")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `incomplete` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `loggedAt` INTEGER")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `source` TEXT")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `captureId` TEXT")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `servings` REAL")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `portionLabel` TEXT")
                db.execSQL("ALTER TABLE `food_entries` ADD COLUMN `portionGrams` REAL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entries_captureId` ON `food_entries` (`captureId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `captures` (`id` TEXT NOT NULL, `threadId` TEXT, `origin` TEXT NOT NULL, `status` TEXT NOT NULL, `inputText` TEXT, `audioPath` TEXT, `transcript` TEXT, `attachedFoodId` TEXT, `zoneId` TEXT NOT NULL, `mealAt` INTEGER, `outcome` TEXT, `errorCode` TEXT, `errorMessage` TEXT, `attempts` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_status` ON `captures` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_threadId` ON `captures` (`threadId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `capture_writes` (`id` TEXT NOT NULL, `captureId` TEXT NOT NULL, `idemKey` TEXT, `tableName` TEXT NOT NULL, `recordId` TEXT NOT NULL, `op` TEXT NOT NULL, `prevJson` TEXT, `newJson` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`captureId`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_capture_writes_captureId_tableName_recordId` ON `capture_writes` (`captureId`, `tableName`, `recordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_capture_writes_captureId_idemKey` ON `capture_writes` (`captureId`, `idemKey`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_inputs` (`id` TEXT NOT NULL, `captureId` TEXT NOT NULL, `threadId` TEXT, `kind` TEXT NOT NULL, `prompt` TEXT NOT NULL, `optionsJson` TEXT NOT NULL, `itemJson` TEXT, `foodRef` TEXT, `status` TEXT NOT NULL, `answerJson` TEXT, `createdAt` INTEGER NOT NULL, `resolvedAt` INTEGER, `notifiedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`captureId`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_inputs_status` ON `pending_inputs` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_inputs_captureId` ON `pending_inputs` (`captureId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `food_portions` (`id` TEXT NOT NULL, `foodId` TEXT NOT NULL, `label` TEXT NOT NULL, `grams` REAL NOT NULL, `isDefault` INTEGER NOT NULL, `kind` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`foodId`) REFERENCES `foods`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_portions_foodId` ON `food_portions` (`foodId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `household_measures` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `grams` REAL NOT NULL, `foodId` TEXT, `baseFoodId` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `base_foods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `aliases` TEXT, `state` TEXT, `kcal100` REAL NOT NULL, `protein100` REAL NOT NULL, `carbs100` REAL NOT NULL, `fat100` REAL NOT NULL, `sourceRef` TEXT, PRIMARY KEY(`id`))")
                HouseholdMeasureSeed.insertSql().forEach(db::execSQL)
            }
        }

        /** v8: copilot chat threads + messages (new tables only; existing data untouched). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_threads` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `origin` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_threads_updatedAt` ON `chat_threads` (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `threadId` TEXT NOT NULL, `seq` INTEGER NOT NULL, `role` TEXT NOT NULL, `text` TEXT, `toolCallsJson` TEXT, `toolCallId` TEXT, `toolName` TEXT, `cardType` TEXT, `cardJson` TEXT, `captureId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`threadId`) REFERENCES `chat_threads`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_messages_threadId_seq` ON `chat_messages` (`threadId`, `seq`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_captureId` ON `chat_messages` (`captureId`)")
            }
        }

        /**
         * v7: quick add becomes a list. The per-day `macro_days` row (P/C/F/kcal, all required) turns into one
         * `quick_add_entries` row (no name, same values, createdAt = updatedAt); macros are nullable from now on (null = unknown).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `quick_add_entries` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `name` TEXT, `kcal` INTEGER NOT NULL, `proteinG` INTEGER, `carbsG` INTEGER, `fatG` INTEGER, `kcalIsAuto` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quick_add_entries_date` ON `quick_add_entries` (`date`)")
                db.execSQL(
                    """INSERT INTO quick_add_entries (id, date, name, kcal, proteinG, carbsG, fatG, kcalIsAuto, createdAt)
                       SELECT 'macro-' || date, date, NULL, kcal, proteinG, carbsG, fatG, kcalIsAuto, updatedAt FROM macro_days""",
                )
                db.execSQL("DROP TABLE macro_days")
            }
        }

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
