package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.prefs.SettingsRepository

class BackupRepository(
    private val db: LifeHubDatabase,
    private val settings: SettingsRepository,
) {

    suspend fun snapshot(): DataSnapshot = db.withTransaction {
        DataSnapshot(
            settings = settings.current(),
            goals = db.goalDao().getAll(), links = db.goalDao().getAllLinks(),
            plans = db.planDao().getAll(), planExercises = db.planDao().getAllExercises(),
            exercises = db.exerciseDao().getAll(),
            sessions = db.sessionDao().getAll(), sets = db.sessionDao().getAllSets(),
            quickAdds = db.quickAddDao().getAll(), bodyEntries = db.bodyDao().getAll(),
            achievements = db.achievementDao().getAll(),
            foods = db.foodDao().getAll(), foodEntries = db.foodEntryDao().getAll(),
            portions = db.portionDao().allPortions(), measures = db.portionDao().measures(),
            memory = db.userMemoryDao().all(),
        )
    }

    private suspend fun wipe() {
        db.achievementDao().deleteAll()
        db.foodEntryDao().deleteAll()
        db.portionDao().deleteAllPortions()
        db.foodDao().deleteAll()
        db.sessionDao().deleteAllSets()
        db.sessionDao().deleteAll()
        db.planDao().deleteAllExercises()
        db.goalDao().deleteAllLinks()
        db.goalDao().deleteAll()
        db.planDao().deleteAll()
        db.exerciseDao().deleteAll()
        db.quickAddDao().deleteAll()
        db.bodyDao().deleteAll()
    }

    /** Single transaction: deleteAll children->parents, insertAll parents->children, then settings.replace(). */
    suspend fun replaceAll(snapshot: DataSnapshot) {
        db.withTransaction {
            wipe()
            db.exerciseDao().insertAll(snapshot.exercises)
            db.planDao().insertAll(snapshot.plans)
            db.planDao().insertExercises(snapshot.planExercises)
            db.goalDao().insertAll(snapshot.goals)
            db.goalDao().insertAllLinks(snapshot.links)
            db.sessionDao().insertAll(snapshot.sessions)
            db.sessionDao().insertAllSets(snapshot.sets)
            db.quickAddDao().insertAll(snapshot.quickAdds)
            db.bodyDao().insertAll(snapshot.bodyEntries)
            // Legacy consistency rows (older exports) are dropped: the streak replaced them.
            db.achievementDao().insertAll(snapshot.achievements.filterNot { it.kind.isConsistency })
            db.foodDao().insertAll(snapshot.foods)
            db.foodEntryDao().insertAll(snapshot.foodEntries)
            val foodIds = snapshot.foods.map { it.id }.toSet()
            db.portionDao().insertPortions(snapshot.portions.filter { it.foodId in foodIds })
            // Household measures are settings-like: replaced only when the file has them; "Delete all data" keeps them.
            snapshot.measures?.let { db.portionDao().deleteAllMeasures(); db.portionDao().insertMeasures(it) }
            // Copilot user memory: same rule (replaced only when the file has it; "Delete all data" keeps it, like chats).
            snapshot.memory?.let { db.userMemoryDao().deleteAll(); db.userMemoryDao().insertAll(it) }
        }
        settings.replace(snapshot.settings)
    }

    /** F-SET-6: wipe all tables + settings.clear() (the exercise library starts empty). */
    suspend fun clearAll() {
        db.withTransaction { wipe() }
        settings.clear()
    }

    /** No goals, plans, sessions, quick adds, body entries or food entries. */
    suspend fun isEmpty(): Boolean = db.withTransaction {
        db.goalDao().getAll().isEmpty() && db.planDao().getAll().isEmpty() && db.sessionDao().getAll().isEmpty() &&
            db.quickAddDao().getAll().isEmpty() && db.bodyDao().getAll().isEmpty() &&
            db.foodEntryDao().getAll().isEmpty()
    }
}
