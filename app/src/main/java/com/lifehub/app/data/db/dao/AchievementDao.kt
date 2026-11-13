package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lifehub.app.data.model.Achievement
import kotlinx.coroutines.flow.Flow

@Dao interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY achievedOn DESC, createdAt DESC") fun observeAll(): Flow<List<Achievement>>
    @Query("SELECT * FROM achievements ORDER BY achievedOn DESC, createdAt DESC LIMIT :n") fun observeRecent(n: Int): Flow<List<Achievement>>
    @Query("SELECT * FROM achievements WHERE id = :id") suspend fun get(id: String): Achievement?
    @Query("SELECT * FROM achievements") suspend fun getAll(): List<Achievement>
    @Query("SELECT dedupeKey FROM achievements") suspend fun getKeys(): List<String>
    /** Returns rowIds; -1 for rows ignored because of the unique dedupeKey. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(items: List<Achievement>): List<Long>
    @Insert suspend fun insertAll(items: List<Achievement>)
    @Query("DELETE FROM achievements WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM achievements WHERE dedupeKey = :key") suspend fun deleteByKey(key: String)
    @Query("DELETE FROM achievements WHERE goalId = :goalId") suspend fun deleteByGoal(goalId: String)
    @Query("DELETE FROM achievements WHERE sessionId = :sessionId AND kind IN ('PR_REPS','PR_HOLD','PR_WEIGHT')") suspend fun deletePrsBySession(sessionId: String)
    @Query("DELETE FROM achievements") suspend fun deleteAll()
    /** Legacy consistency rows (perfect week/month, protein week, session milestones): replaced by the streak. */
    @Query("DELETE FROM achievements WHERE kind IN ('PERFECT_WEEK_TRAINING','PERFECT_MONTH_TRAINING','PROTEIN_WEEK','SESSION_MILESTONE')") suspend fun deleteConsistency()
}
