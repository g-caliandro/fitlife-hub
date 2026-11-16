package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.*
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.domain.AchievementCandidate
import com.lifehub.app.domain.AchievementEngine
import com.lifehub.app.domain.AchievementHistory
import com.lifehub.app.domain.Bests
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

interface AchievementRepository {
    fun observeAll(): Flow<List<Achievement>>
    fun observeRecent(n: Int): Flow<List<Achievement>>
    suspend fun getAll(): List<Achievement>
    suspend fun get(id: String): Achievement?
    /** User delete: the key is remembered so automatic detection does not bring it back. */
    suspend fun delete(id: String)
    /** DONE sets of an exercise in completed sessions (live PR detection). */
    suspend fun historySets(exerciseId: String): List<SessionSet>
    /** Persist PRs of a just finished session. Returns the newly stored ones. */
    suspend fun onSessionFinished(sessionId: String, now: Instant): List<Achievement>
    /** Removes PR achievements tied to [sessionId] (before re-detection after an edit). */
    suspend fun resetSession(sessionId: String)
    /** Idempotent detection of goal achievements; also purges legacy consistency rows (now shown as the streak). */
    suspend fun detectPeriods(today: LocalDate, proteinTargetG: Int, zone: ZoneId, now: Instant): List<Achievement>
    /** Full backfill from history (after import); forgets user deletions. */
    suspend fun recalculate(today: LocalDate, proteinTargetG: Int, zone: ZoneId, now: Instant): List<Achievement>
}

class AchievementRepositoryImpl(private val db: LifeHubDatabase, private val settings: SettingsRepository) : AchievementRepository {
    private val dao = db.achievementDao()
    private val sessionDao = db.sessionDao()

    override fun observeAll() = dao.observeAll()
    override fun observeRecent(n: Int) = dao.observeRecent(n)
    override suspend fun getAll() = dao.getAll()
    override suspend fun get(id: String) = dao.get(id)

    override suspend fun delete(id: String) {
        val a = dao.get(id) ?: return
        settings.dismissAchievementKey(a.dedupeKey)
        dao.delete(id)
    }

    override suspend fun resetSession(sessionId: String) = dao.deletePrsBySession(sessionId)

    override suspend fun historySets(exerciseId: String) = sessionDao.getDoneSetsOfCompleted(exerciseId)

    private suspend fun store(cands: List<AchievementCandidate>, now: Instant, respectDismissed: Boolean): List<Achievement> {
        val dismissed = if (respectDismissed) settings.dismissedAchievementKeys() else emptySet()
        val rows = cands.filter { it.dedupeKey !in dismissed }
            .map { it.toEntity(UUID.randomUUID().toString(), now) }
        if (rows.isEmpty()) return emptyList()
        val ids = dao.insertIgnore(rows)
        return rows.filterIndexed { i, _ -> ids[i] != -1L }
    }

    private suspend fun history(today: LocalDate, protein: Int, zone: ZoneId) = AchievementHistory(
        today = today, sessions = sessionDao.getAll(), sets = sessionDao.getAllSets(), plans = db.planDao().getAll(),
        macros = com.lifehub.app.domain.mergeNutrition(db.quickAddDao().getAll(), db.foodEntryDao().getAll()).map { it.asMacroDay() }, goals = db.goalDao().getAll(), proteinTargetG = protein, zone = zone,
    )

    override suspend fun onSessionFinished(sessionId: String, now: Instant): List<Achievement> = db.withTransaction {
        val session = sessionDao.get(sessionId)
        if (session == null || session.status != SessionStatus.COMPLETED) return@withTransaction emptyList()
        val all = sessionDao.getAll().filter { it.status == SessionStatus.COMPLETED }
        val before = all.filter { it.id != session.id && (it.date < session.date || (it.date == session.date && it.startedAt <= session.startedAt)) }
            .map { it.id }.toSet()
        val allSets = sessionDao.getAllSets()
        val priors = HashMap<String, Bests>()
        allSets.filter { it.sessionId in before }.forEach { priors[it.exerciseId] = (priors[it.exerciseId] ?: Bests()).plus(it) }
        val prs = AchievementEngine.sessionPrs(session, allSets.filter { it.sessionId == session.id }, priors)
        store(prs, now, respectDismissed = true)
    }

    override suspend fun detectPeriods(today: LocalDate, proteinTargetG: Int, zone: ZoneId, now: Instant): List<Achievement> =
        db.withTransaction {
            dao.deleteConsistency()
            val h = history(today, proteinTargetG, zone)
            store(AchievementEngine.goals(h), now, respectDismissed = true)
        }

    override suspend fun recalculate(today: LocalDate, proteinTargetG: Int, zone: ZoneId, now: Instant): List<Achievement> =
        db.withTransaction {
            settings.clearDismissedAchievementKeys()
            dao.deleteConsistency()
            store(AchievementEngine.backfill(history(today, proteinTargetG, zone)), now, respectDismissed = false)
        }
}
