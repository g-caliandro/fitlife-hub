package com.lifehub.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY deadline ASC") fun observeAll(): Flow<List<Goal>>
    @Query("SELECT * FROM goals WHERE id = :id") fun observe(id: String): Flow<Goal?>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun get(id: String): Goal?
    @Upsert suspend fun upsert(goal: Goal)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM goal_plan_links") fun observeAllLinks(): Flow<List<GoalPlanLink>>
    @Query("SELECT * FROM goal_plan_links WHERE goalId = :goalId") fun observeLinksForGoal(goalId: String): Flow<List<GoalPlanLink>>
    @Query("SELECT * FROM goal_plan_links WHERE planId = :planId") suspend fun getLinksForPlan(planId: String): List<GoalPlanLink>
    @Upsert suspend fun upsertLink(link: GoalPlanLink)
    @Query("DELETE FROM goal_plan_links WHERE goalId = :goalId AND planId = :planId") suspend fun deleteLink(goalId: String, planId: String)
    @Query("DELETE FROM goal_plan_links WHERE planId = :planId") suspend fun deleteLinksForPlan(planId: String)

    @Query("SELECT * FROM goals") suspend fun getAll(): List<Goal>
    @Query("SELECT * FROM goal_plan_links") suspend fun getAllLinks(): List<GoalPlanLink>
    @Insert suspend fun insertAll(items: List<Goal>)
    @Insert suspend fun insertAllLinks(items: List<GoalPlanLink>)
    @Query("DELETE FROM goals") suspend fun deleteAll()
    @Query("DELETE FROM goal_plan_links") suspend fun deleteAllLinks()
}
