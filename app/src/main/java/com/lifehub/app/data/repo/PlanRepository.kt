package com.lifehub.app.data.repo

import androidx.room.withTransaction
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class PlanRepository(private val db: LifeHubDatabase) {
    private val dao = db.planDao()
    private val goalDao = db.goalDao()
    private val exerciseDao = db.exerciseDao()

    private fun assemble(plan: Plan, items: List<PlanExercise>, exById: Map<String, Exercise>) =
        PlanWithExercises(
            plan,
            items.sortedBy { it.orderIndex }.mapNotNull { pe -> exById[pe.exerciseId]?.let { PlanExerciseItem(pe, it) } },
        )

    /** includes archived; UI filters */
    fun observePlans(): Flow<List<PlanWithExercises>> =
        combine(dao.observeAll(), dao.observeAllExercises(), exerciseDao.observeAll()) { plans, pes, exs ->
            val exById = exs.associateBy { it.id }
            val byPlan = pes.groupBy { it.planId }
            plans.map { assemble(it, byPlan[it.id].orEmpty(), exById) }
        }

    fun observePlan(id: String): Flow<PlanWithExercises?> =
        combine(dao.observe(id), dao.observeExercises(id), exerciseDao.observeAll()) { p, pes, exs ->
            p?.let { assemble(it, pes, exs.associateBy { e -> e.id }) }
        }

    suspend fun get(id: String): PlanWithExercises? {
        val p = dao.get(id) ?: return null
        val exById = exerciseDao.getAll().associateBy { it.id }
        return assemble(p, dao.getExercises(id), exById)
    }

    suspend fun getLinks(planId: String): List<GoalPlanLink> = goalDao.getLinksForPlan(planId)

    /** Upserts plan, replaces its PlanExercise rows (orderIndex re-numbered 0..n-1 from list order) and its goal links. */
    suspend fun save(plan: Plan, exercises: List<PlanExercise>, links: List<GoalPlanLink>) {
        db.withTransaction {
            dao.upsert(plan)
            dao.deleteExercises(plan.id)
            dao.insertExercises(exercises.mapIndexed { i, e -> e.copy(planId = plan.id, orderIndex = i) })
            goalDao.deleteLinksForPlan(plan.id)
            links.forEach { goalDao.upsertLink(it.copy(planId = plan.id)) }
        }
    }

    /** returns new id; "<name> (copy)", no links */
    suspend fun duplicate(id: String, now: Instant): String = db.withTransaction {
        val src = checkNotNull(dao.get(id)) { "Plan $id not found" }
        val newId = UUID.randomUUID().toString()
        dao.upsert(src.copy(id = newId, name = "${src.name} (copy)", isArchived = false, createdAt = now))
        dao.insertExercises(dao.getExercises(id).map { it.copy(id = UUID.randomUUID().toString(), planId = newId) })
        newId
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        dao.get(id)?.let { dao.upsert(it.copy(isArchived = archived)) }
    }

    /** sessions keep planName, planId -> NULL */
    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Progressive overload memory: raises the plan's targets to what was achieved in [sets] (a finished session of
     * [planId]), see [com.lifehub.app.domain.Progression]. Returns short labels of the raised targets.
     */
    suspend fun raiseTargets(planId: String, sets: List<com.lifehub.app.data.model.SessionSet>): List<String> {
        val raised = com.lifehub.app.domain.Progression.raise(dao.getExercises(planId), sets)
        if (raised.isNotEmpty()) dao.upsertExercises(raised.map { it.updated })
        return raised.map { it.label }
    }
}
