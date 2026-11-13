package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface SessionDao {
    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' LIMIT 1") fun observeInProgress(): Flow<WorkoutSession?>
    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' LIMIT 1") suspend fun getInProgress(): WorkoutSession?
    @Query("SELECT * FROM sessions WHERE id = :id") fun observe(id: String): Flow<WorkoutSession?>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun get(id: String): WorkoutSession?
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' ORDER BY date DESC, startedAt DESC") fun observeCompleted(): Flow<List<WorkoutSession>>
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND date BETWEEN :from AND :to ORDER BY date, startedAt")
    fun observeCompletedBetween(from: LocalDate, to: LocalDate): Flow<List<WorkoutSession>>
    @Upsert suspend fun upsert(session: WorkoutSession)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseOrder, setIndex") fun observeSets(sessionId: String): Flow<List<SessionSet>>
    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseOrder, setIndex") suspend fun getSets(sessionId: String): List<SessionSet>
    @Query("""SELECT ss.* FROM session_sets ss JOIN sessions s ON s.id = ss.sessionId
              WHERE s.status = 'COMPLETED' AND s.date BETWEEN :from AND :to ORDER BY s.date, ss.exerciseOrder, ss.setIndex""")
    fun observeCompletedSetsBetween(from: LocalDate, to: LocalDate): Flow<List<SessionSet>>
    @Query("""SELECT ss.* FROM session_sets ss JOIN sessions s ON s.id = ss.sessionId
              WHERE s.status = 'COMPLETED' AND ss.status = 'DONE' AND ss.exerciseId = :exerciseId""")
    suspend fun getDoneSetsOfCompleted(exerciseId: String): List<SessionSet>
    /** Sets of [exerciseId] in the most recent COMPLETED session that has a DONE set of it. */
    @Query("""SELECT ss.* FROM session_sets ss WHERE ss.exerciseId = :exerciseId AND ss.sessionId = (
              SELECT s.id FROM sessions s JOIN session_sets x ON x.sessionId = s.id
              WHERE s.status = 'COMPLETED' AND x.exerciseId = :exerciseId AND x.status = 'DONE'
              ORDER BY s.date DESC, s.startedAt DESC LIMIT 1) ORDER BY ss.exerciseOrder, ss.setIndex""")
    suspend fun getPreviousSets(exerciseId: String): List<SessionSet>
    @Upsert suspend fun upsertSet(set: SessionSet)
    @Upsert suspend fun upsertSets(sets: List<SessionSet>)
    @Query("DELETE FROM session_sets WHERE id = :id") suspend fun deleteSet(id: String)
    @Query("DELETE FROM session_sets WHERE sessionId = :sessionId") suspend fun deleteSets(sessionId: String)

    @Query("SELECT * FROM sessions") suspend fun getAll(): List<WorkoutSession>
    @Query("SELECT * FROM session_sets") suspend fun getAllSets(): List<SessionSet>
    @Insert suspend fun insertAll(items: List<WorkoutSession>)
    @Insert suspend fun insertAllSets(items: List<SessionSet>)
    @Query("DELETE FROM sessions") suspend fun deleteAll()
    @Query("DELETE FROM session_sets") suspend fun deleteAllSets()
}
