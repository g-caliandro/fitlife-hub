package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.prefs.SettingsRepository

interface BackupRepository {
    suspend fun snapshot(): DataSnapshot
    /** Single transaction: deleteAll children->parents, insertAll parents->children, then settings.replace(). */
    suspend fun replaceAll(snapshot: DataSnapshot)
    /** F-SET-6: wipe all tables + settings.clear() (the exercise library starts empty). */
    suspend fun clearAll()
    suspend fun isEmpty(): Boolean      // no goals, plans, sessions, macros, body entries
}

class BackupRepositoryImpl(
    private val db: LifeHubDatabase,
    private val settings: SettingsRepository,
) : BackupRepository {

    override suspend fun snapshot(): DataSnapshot = db.withTransaction {
        DataSnapshot(
            settings = settings.current(),
            goals = db.goalDao().getAll(), links = db.goalDao().getAllLinks(),
            plans = db.planDao().getAll(), planExercises = db.planDao().getAllExercises(),
            exercises = db.exerciseDao().getAll(),
            sessions = db.sessionDao().getAll(), sets = db.sessionDao().getAllSets(),
            macros = db.macroDao().getAll(), bodyEntries = db.bodyDao().getAll(),
            achievements = db.achievementDao().getAll(),
            foods = db.foodDao().getAll(), foodEntries = db.foodEntryDao().getAll(),
        )
    }

    private suspend fun wipe() {
        db.achievementDao().deleteAll()
        db.foodEntryDao().deleteAll()
        db.foodDao().deleteAll()
        db.sessionDao().deleteAllSets()
        db.sessionDao().deleteAll()
        db.planDao().deleteAllExercises()
        db.goalDao().deleteAllLinks()
        db.goalDao().deleteAll()
        db.planDao().deleteAll()
        db.exerciseDao().deleteAll()
        db.macroDao().deleteAll()
        db.bodyDao().deleteAll()
    }

    override suspend fun replaceAll(snapshot: DataSnapshot) {
        db.withTransaction {
            wipe()
            db.exerciseDao().insertAll(snapshot.exercises)
            db.planDao().insertAll(snapshot.plans)
            db.planDao().insertExercises(snapshot.planExercises)
            db.goalDao().insertAll(snapshot.goals)
            db.goalDao().insertAllLinks(snapshot.links)
            db.sessionDao().insertAll(snapshot.sessions)
            db.sessionDao().insertAllSets(snapshot.sets)
            db.macroDao().insertAll(snapshot.macros)
            db.bodyDao().insertAll(snapshot.bodyEntries)
            // Legacy consistency rows (older exports) are dropped: the streak replaced them.
            db.achievementDao().insertAll(snapshot.achievements.filterNot { it.kind.isConsistency })
            db.foodDao().insertAll(snapshot.foods)
            db.foodEntryDao().insertAll(snapshot.foodEntries)
        }
        settings.replace(snapshot.settings)
    }

    override suspend fun clearAll() {
        db.withTransaction { wipe() }
        settings.clear()
    }

    override suspend fun isEmpty(): Boolean = db.withTransaction {
        db.goalDao().getAll().isEmpty() && db.planDao().getAll().isEmpty() && db.sessionDao().getAll().isEmpty() &&
            db.macroDao().getAll().isEmpty() && db.bodyDao().getAll().isEmpty() &&
            db.foodEntryDao().getAll().isEmpty()
    }
}
