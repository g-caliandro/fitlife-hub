package com.lifehub.app.copilot.plan

import androidx.room.withTransaction
import com.lifehub.app.copilot.capture.IllegalTransitionException
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.ExerciseCategory
import com.lifehub.app.data.model.Goal
import com.lifehub.app.data.model.GoalPlanLink
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.PlanDraft
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.data.model.PlanExercise
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.model.TargetType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Goal of a draft (owner decision 3): an existing goal ([existingGoalId]) or a new one named [name] whose deadline is
 * [weeks] weeks after the day the draft is approved.
 */
@Serializable
data class DraftGoal(val name: String, val weeks: Int? = null, val existingGoalId: String? = null)

/** One exercise of a draft plan, in the app's real model (sets 1..20, REPS / MAX / SECONDS target, rest 0..600 s). */
@Serializable
data class DraftExercise(
    val name: String,
    val category: ExerciseCategory,
    /** The app's exercise with this name (case-insensitive) when the draft was saved; null = created on Approve. */
    val exerciseId: String? = null,
    val sets: Int,
    val targetType: TargetType,
    val targetValue: Int? = null,
    val weightKg: Double? = null,
    val restSeconds: Int = 90,
) {
    val isNew: Boolean get() = exerciseId == null
}

/** One weekday plan: a single session template repeated on [weekdays] (ISO 1..7), like the app's plans. */
@Serializable
data class DraftPlan(
    val name: String,
    val weekdays: List<Int>,
    val dayType: DayType? = null,
    val notes: String? = null,
    val exercises: List<DraftExercise>,
)

@Serializable
data class DraftPayload(val goal: DraftGoal? = null, val plans: List<DraftPlan>) {
    val weekdays: Set<Int> get() = plans.flatMap { it.weekdays }.toSet()
}

object DraftCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(p: DraftPayload): String = json.encodeToString(DraftPayload.serializer(), p)
    fun decode(d: PlanDraft): DraftPayload = runCatching { json.decodeFromString(DraftPayload.serializer(), d.payloadJson) }
        .getOrDefault(DraftPayload(null, emptyList()))
}

/** Active (non-archived) plans that share at least one weekday with [draft]: the ones the Approve checkbox may archive. */
fun overlappingPlans(draft: DraftPayload, plans: List<PlanWithExercises>): List<PlanWithExercises> =
    plans.filter { !it.plan.isArchived && it.plan.weekdays.any { d -> d in draft.weekdays } }

sealed interface ApproveResult {
    data class Approved(val planIds: List<String>, val archivedPlanIds: List<String>, val goalId: String?) : ApproveResult
    /** Already approved, rejected or superseded: nothing changes. */
    data class NotADraft(val status: PlanDraftStatus) : ApproveResult
    data object NotFound : ApproveResult
}

/**
 * Plan drafts (design doc §7 "Bozze di piano"). [propose] only ever writes `plan_drafts`; [approve] is the ONLY way a draft
 * reaches the `plans` table, in one transaction. Nothing here turns a decided draft back into a DRAFT.
 */
interface PlanDraftStore {
    fun observeAll(): Flow<List<PlanDraft>>
    fun observe(id: String): Flow<PlanDraft?>
    suspend fun all(): List<PlanDraft>
    suspend fun get(id: String): PlanDraft?
    suspend fun byCapture(captureId: String): List<PlanDraft>
    /** Saves a new DRAFT; [replaces] (an open draft) becomes SUPERSEDED in the same transaction. */
    suspend fun propose(title: String, payload: DraftPayload, threadId: String?, captureId: String?, now: Instant, replaces: String? = null): PlanDraft
    /**
     * Turns a DRAFT into active plans: missing exercises are created as custom exercises (by case-insensitive name), a new
     * goal is created (start [today], deadline today + weeks) or the existing one linked, the plans and their exercises are
     * added. Existing plans on the same weekdays are archived only when [archiveOverlapping] (owner decision 4).
     */
    suspend fun approve(id: String, archiveOverlapping: Boolean, today: LocalDate, now: Instant): ApproveResult
    /** DRAFT -> REJECTED. Throws [IllegalTransitionException] for a decided draft. */
    suspend fun reject(id: String, now: Instant): PlanDraft
    /** Removes the draft row (workouts screen "Delete"); plans already created by an Approve stay. */
    suspend fun delete(id: String)
}

class PlanDraftStoreImpl(private val db: LifeHubDatabase) : PlanDraftStore {
    private val dao = db.planDraftDao()

    override fun observeAll() = dao.observeAll()
    override fun observe(id: String) = dao.observe(id)
    override suspend fun all() = dao.all()
    override suspend fun get(id: String) = dao.get(id)
    override suspend fun byCapture(captureId: String) = dao.byCapture(captureId)

    override suspend fun propose(title: String, payload: DraftPayload, threadId: String?, captureId: String?, now: Instant, replaces: String?): PlanDraft = db.withTransaction {
        replaces?.let { old ->
            dao.get(old)?.takeIf { it.status == PlanDraftStatus.DRAFT }?.let { dao.upsert(it.copy(status = PlanDraftStatus.SUPERSEDED, decidedAt = now)) }
        }
        val d = PlanDraft(
            UUID.randomUUID().toString(), threadId, captureId, title, payload.goal?.name, payload.goal?.weeks,
            DraftCodec.encode(payload), PlanDraftStatus.DRAFT, now,
        )
        dao.upsert(d)
        d
    }

    override suspend fun approve(id: String, archiveOverlapping: Boolean, today: LocalDate, now: Instant): ApproveResult = db.withTransaction {
        val d = dao.get(id) ?: return@withTransaction ApproveResult.NotFound
        if (d.status != PlanDraftStatus.DRAFT) return@withTransaction ApproveResult.NotADraft(d.status)
        val payload = DraftCodec.decode(d)
        val planDao = db.planDao(); val exDao = db.exerciseDao(); val goalDao = db.goalDao()

        // Overlap is computed before the new plans exist, so they are never archived themselves.
        val archived = if (!archiveOverlapping) emptyList() else
            planDao.getAll().filter { !it.isArchived && it.weekdays.any { w -> w in payload.weekdays } }
                .onEach { planDao.upsert(it.copy(isArchived = true)) }.map { it.id }

        val goalId = payload.goal?.let { g ->
            g.existingGoalId?.let { goalDao.get(it)?.id } ?: run {
                val goal = Goal(
                    UUID.randomUUID().toString(), g.name, null, today, today.plusWeeks((g.weeks ?: DEFAULT_GOAL_WEEKS).toLong()),
                    deadlineIsAuto = false, state = GoalState.ACTIVE, achievedAt = null, createdAt = now,
                )
                goalDao.upsert(goal)
                goal.id
            }
        }

        val planIds = payload.plans.map { p ->
            val planId = UUID.randomUUID().toString()
            planDao.upsert(Plan(planId, p.name, p.weekdays.toSet(), p.notes, false, now, p.dayType))
            planDao.insertExercises(p.exercises.mapIndexed { i, e ->
                val ex = exDao.findByName(e.name.trim()) ?: Exercise(
                    UUID.randomUUID().toString(), e.name.trim(), e.category,
                    if (e.targetType == TargetType.SECONDS) TargetType.SECONDS else TargetType.REPS, isCustom = true, isHidden = false,
                ).also { exDao.upsert(it) }
                PlanExercise(UUID.randomUUID().toString(), planId, ex.id, i, e.sets, e.targetType,
                    if (e.targetType == TargetType.MAX) null else e.targetValue, e.weightKg, e.restSeconds)
            })
            goalId?.let { goalDao.upsertLink(GoalPlanLink(it, planId, null)) }
            planId
        }
        dao.upsert(d.copy(status = PlanDraftStatus.APPROVED, decidedAt = now, approvedPlanIds = planIds.joinToString(",")))
        ApproveResult.Approved(planIds, archived, goalId)
    }

    override suspend fun reject(id: String, now: Instant): PlanDraft {
        val d = dao.get(id) ?: throw IllegalTransitionException("Unknown draft $id")
        if (d.status == PlanDraftStatus.REJECTED) return d
        if (d.status != PlanDraftStatus.DRAFT) throw IllegalTransitionException("Draft already ${d.status.name.lowercase()}")
        val next = d.copy(status = PlanDraftStatus.REJECTED, decidedAt = now)
        dao.upsert(next)
        return next
    }

    override suspend fun delete(id: String) = dao.delete(id)

    companion object {
        /** A new goal without a duration gets this many weeks (the tool always asks for one; safety net only). */
        const val DEFAULT_GOAL_WEEKS = 4
    }
}
