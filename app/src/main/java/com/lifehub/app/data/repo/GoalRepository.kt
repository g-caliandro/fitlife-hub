package com.lifehub.app.data.repo

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import com.lifehub.app.domain.AchievementEngine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface GoalRepository {
    fun observeGoals(): Flow<List<GoalWithLinks>>
    fun observeGoal(id: String): Flow<GoalWithLinks?>
    suspend fun get(id: String): Goal?
    suspend fun save(goal: Goal)                                         // upsert
    suspend fun setState(id: String, state: GoalState, today: LocalDate) // ACHIEVED sets achievedAt=today; ACTIVE clears it
    suspend fun delete(id: String)                                       // links cascade; plans untouched
    suspend fun upsertLink(link: GoalPlanLink)
    suspend fun removeLink(goalId: String, planId: String)
}

class GoalRepositoryImpl(private val db: LifeHubDatabase) : GoalRepository {
    private val dao = db.goalDao()

    override fun observeGoals(): Flow<List<GoalWithLinks>> =
        combine(dao.observeAll(), dao.observeAllLinks()) { goals, links ->
            val byGoal = links.groupBy { it.goalId }
            goals.map { GoalWithLinks(it, byGoal[it.id].orEmpty()) }
        }

    override fun observeGoal(id: String): Flow<GoalWithLinks?> =
        combine(dao.observe(id), dao.observeLinksForGoal(id)) { g, l -> g?.let { GoalWithLinks(it, l) } }

    override suspend fun get(id: String): Goal? = dao.get(id)
    override suspend fun save(goal: Goal) = dao.upsert(goal)

    override suspend fun setState(id: String, state: GoalState, today: LocalDate) {
        val g = dao.get(id) ?: return
        val achieved = when (state) {
            GoalState.ACHIEVED -> today
            GoalState.ACTIVE -> null
            GoalState.ARCHIVED -> g.achievedAt
        }
        val updated = g.copy(state = state, achievedAt = achieved)
        db.withTransaction {
            dao.upsert(updated)
            val ach = db.achievementDao()
            when (state) {
                GoalState.ACHIEVED -> ach.insertIgnore(listOf(
                    AchievementEngine.goalCandidate(updated, today).toEntity(UUID.randomUUID().toString(), Instant.now()),
                ))
                GoalState.ACTIVE -> ach.deleteByKey("GOAL:$id")
                GoalState.ARCHIVED -> {}
            }
        }
    }

    override suspend fun delete(id: String) {
        db.withTransaction { db.achievementDao().deleteByGoal(id); dao.delete(id) }
    }
    override suspend fun upsertLink(link: GoalPlanLink) = dao.upsertLink(link)
    override suspend fun removeLink(goalId: String, planId: String) = dao.deleteLink(goalId, planId)
}
