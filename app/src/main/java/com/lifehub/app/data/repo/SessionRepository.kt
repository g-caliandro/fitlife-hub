package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ActiveSessionExistsException : IllegalStateException()

class SessionRepository(private val db: LifeHubDatabase) {
    private val dao = db.sessionDao()
    private val planDao = db.planDao()

    /** Sets of [exerciseId] from the most recent completed session in which it was done (any plan). */
    suspend fun previousSets(exerciseId: String): List<SessionSet> = dao.getPreviousSets(exerciseId)
    /** DONE sets of an exercise in completed sessions (live PR detection). */
    suspend fun historySets(exerciseId: String): List<SessionSet> = dao.getDoneSetsOfCompleted(exerciseId)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeInProgress(): Flow<SessionWithSets?> =
        dao.observeInProgress().flatMapLatest { s ->
            if (s == null) flowOf(null) else dao.observeSets(s.id).map { SessionWithSets(s, it) }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeSession(id: String): Flow<SessionWithSets?> =
        dao.observe(id).flatMapLatest { s ->
            if (s == null) flowOf(null) else dao.observeSets(s.id).map { SessionWithSets(s, it) }
        }

    /** all, no sets */
    fun observeCompleted(): Flow<List<WorkoutSession>> = dao.observeCompleted()

    fun observeCompletedWithSets(from: LocalDate, to: LocalDate): Flow<List<SessionWithSets>> =
        combine(dao.observeCompletedBetween(from, to), dao.observeCompletedSetsBetween(from, to)) { sessions, sets ->
            val by = sets.groupBy { it.sessionId }
            sessions.map { SessionWithSets(it, by[it.id].orEmpty().sortedWith(compareBy({ s -> s.exerciseOrder }, { s -> s.setIndex }))) }
        }

    /** F-WO-1. Throws ActiveSessionExistsException if one is IN_PROGRESS. Creates one PENDING SessionSet per planned set. */
    suspend fun startFromPlan(planId: String, now: Instant, today: LocalDate): String = db.withTransaction {
        if (dao.getInProgress() != null) throw ActiveSessionExistsException()
        val plan = checkNotNull(planDao.get(planId)) { "Plan $planId not found" }
        val exById = db.exerciseDao().getAll().associateBy { it.id }
        val sessionId = UUID.randomUUID().toString()
        dao.upsert(
            WorkoutSession(
                id = sessionId, planId = plan.id, planName = plan.name, date = today, startedAt = now,
                endedAt = null, durationSec = null, status = SessionStatus.IN_PROGRESS, rpe = null, notes = null,
                isManual = false, currentSetIndex = 0, restEndsAt = null,
            )
        )
        val sets = planDao.getExercises(planId).sortedBy { it.orderIndex }.flatMapIndexed { order, pe ->
            val name = exById[pe.exerciseId]?.name ?: pe.exerciseId
            (0 until pe.sets).map { i ->
                SessionSet(
                    id = UUID.randomUUID().toString(), sessionId = sessionId, exerciseId = pe.exerciseId,
                    exerciseName = name, exerciseOrder = order, setIndex = i, targetType = pe.targetType,
                    targetValue = pe.targetValue, actualReps = null, actualSeconds = null, weightKg = pe.weightKg,
                    status = SetStatus.PENDING, completedAt = null, restSeconds = pe.restSeconds,
                )
            }
        }
        dao.upsertSets(sets)
        sessionId
    }

    suspend fun discard(sessionId: String) = dao.delete(sessionId)   // sets cascade
    suspend fun updateSession(session: WorkoutSession) = dao.upsert(session)
    suspend fun upsertSet(set: SessionSet) = dao.upsertSet(set)
    suspend fun upsertSets(sets: List<SessionSet>) = dao.upsertSets(sets)
    suspend fun deleteSet(setId: String) = dao.deleteSet(setId)

    /** F-WO-10: PENDING -> SKIPPED, status COMPLETED, endedAt=now, durationSec, restEndsAt=null. */
    suspend fun finish(sessionId: String, notes: String?, rpe: Int?, now: Instant) {
        db.withTransaction {
            val s = dao.get(sessionId) ?: return@withTransaction
            dao.upsertSets(dao.getSets(sessionId).filter { it.status == SetStatus.PENDING }.map { it.copy(status = SetStatus.SKIPPED) })
            dao.upsert(
                s.copy(
                    status = SessionStatus.COMPLETED, endedAt = now,
                    durationSec = Duration.between(s.startedAt, now).seconds.toInt().coerceAtLeast(0),
                    notes = notes, rpe = rpe, restEndsAt = null,
                )
            )
        }
    }

    /** F-WO-8 / F-WO-13: writes session + replaces all its sets in one transaction. */
    suspend fun saveFull(session: WorkoutSession, sets: List<SessionSet>) {
        db.withTransaction {
            dao.upsert(session)
            dao.deleteSets(session.id)
            dao.upsertSets(sets)
        }
    }

    suspend fun delete(sessionId: String) {
        db.withTransaction { db.achievementDao().deletePrsBySession(sessionId); dao.delete(sessionId) }
    }
}
