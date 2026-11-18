package com.lifehub.app.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.db.dao.BodyDao
import com.lifehub.app.data.model.*
import com.lifehub.app.data.prefs.SettingsRepository
import com.lifehub.app.mock.MockDataGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RepositoriesTest {
    private lateinit var ctx: Context
    private lateinit var db: LifeHubDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var goals: GoalRepository
    private lateinit var plans: PlanRepository
    private lateinit var exercises: ExerciseRepository
    private lateinit var sessions: SessionRepository
    private lateinit var macros: QuickAddRepository
    private lateinit var body: BodyDao
    private lateinit var backup: BackupRepository

    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 12)
    private val PULL_UP = "ex-pull-up"
    private val DIP = "ex-dip"

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = LifeHubDatabase.inMemory(ctx)
        settings = SettingsRepository(ctx).also { it.clear() }
        goals = GoalRepository(db); plans = PlanRepository(db); exercises = ExerciseRepository(db)
        sessions = SessionRepository(db); macros = QuickAddRepository(db); body = db.bodyDao()
        backup = BackupRepository(db, settings)
        runBlocking { db.exerciseDao().insertAll(MockDataGenerator.exercises) }
    }

    @After fun tearDown() { db.close() }

    private fun goal(id: String = "g1") = Goal(id, "Muscle Up", null, today, today.plusDays(84), true, GoalState.ACTIVE, null, now)
    private fun plan(id: String = "p1", name: String = "Pull") = Plan(id, name, setOf(1, 3), null, false, now)
    private fun planEx(i: Int, ex: String, sets: Int = 3) =
        PlanExercise("pe$i", "p1", ex, i, sets, TargetType.REPS, 5, null, 90)

    @Test fun freshDatabaseHasNoExercises() = runBlocking {
        val fresh = LifeHubDatabase.inMemory(ctx)
        assertEquals(0, fresh.exerciseDao().count())
        fresh.close()
    }

    @Test fun deleteGoalCascadesLinksKeepsPlans() = runBlocking {
        goals.save(goal())
        plans.save(plan(), listOf(planEx(0, PULL_UP)), listOf(GoalPlanLink("g1", "p1", 10)))
        assertEquals(1, goals.observeGoal("g1").first()!!.links.size)
        goals.delete("g1")
        assertTrue(db.goalDao().getAllLinks().isEmpty())
        assertNotNull(plans.get("p1"))
    }

    @Test fun goalSetState() = runBlocking {
        goals.save(goal())
        goals.setState("g1", GoalState.ACHIEVED, today)
        assertEquals(today, goals.get("g1")!!.achievedAt)
        goals.setState("g1", GoalState.ACTIVE, today)
        assertNull(goals.get("g1")!!.achievedAt)
    }

    @Test fun planSaveRenumbersAndDuplicate() = runBlocking {
        plans.save(plan(), listOf(planEx(5, DIP), planEx(9, PULL_UP)), emptyList())
        val p = plans.get("p1")!!
        assertEquals(listOf(0, 1), p.exercises.map { it.planExercise.orderIndex })
        assertEquals("Dip", p.exercises[0].exercise.name)
        val copyId = plans.duplicate("p1", now)
        val copy = plans.get(copyId)!!
        assertEquals("Pull (copy)", copy.plan.name)
        assertEquals(2, copy.exercises.size)
        assertEquals(2, plans.observePlans().first().size)
    }

    @Test fun deletePlanNullsSessionPlanId() = runBlocking {
        plans.save(plan(), listOf(planEx(0, PULL_UP)), emptyList())
        val sid = sessions.startFromPlan("p1", now, today)
        sessions.finish(sid, null, null, now.plusSeconds(60))
        plans.delete("p1")
        val s = db.sessionDao().get(sid)!!
        assertNull(s.planId)
        assertEquals("Pull", s.planName)
    }

    @Test fun exerciseDeleteResults() = runBlocking {
        val custom = Exercise("c1", "Muscle-up kip", ExerciseCategory.PULL, TargetType.REPS, true, false)
        assertEquals(ExerciseSaveResult.Ok, exercises.save(custom))
        assertEquals(ExerciseSaveResult.DuplicateName, exercises.save(custom.copy(id = "c2", name = " muscle-UP kip ")))
        assertEquals(ExerciseSaveResult.DuplicateName, exercises.save(custom.copy(id = "c3", name = "pull-up")))
        plans.save(plan(), listOf(planEx(0, "c1")), emptyList())
        assertEquals(ExerciseDeleteResult.InUse, exercises.delete("c1"))
        plans.delete("p1")
        assertEquals(ExerciseDeleteResult.Ok, exercises.delete("c1"))
        assertNull(exercises.get("c1"))
    }

    @Test fun startFromPlanCreatesSetsAndRejectsSecond() = runBlocking {
        plans.save(plan(), listOf(planEx(0, PULL_UP, 4), planEx(1, "ex-dead-hang", 2)), emptyList())
        val sid = sessions.startFromPlan("p1", now, today)
        val live = sessions.observeInProgress().first()!!
        assertEquals(sid, live.session.id)
        assertEquals(6, live.sets.size)
        assertTrue(live.sets.all { it.status == SetStatus.PENDING })
        assertEquals(listOf(0, 0, 0, 0, 1, 1), live.sets.map { it.exerciseOrder })
        try { sessions.startFromPlan("p1", now, today); fail("expected exception") } catch (_: ActiveSessionExistsException) {}
        sessions.finish(sid, "ok", 7, now.plusSeconds(1800))
        assertNull(sessions.observeInProgress().first())
        val done = sessions.observeSession(sid).first()!!
        assertEquals(SessionStatus.COMPLETED, done.session.status)
        assertEquals(1800, done.session.durationSec)
        assertTrue(done.sets.all { it.status == SetStatus.SKIPPED })
        assertEquals(1, sessions.observeCompleted().first().size)
        assertEquals(1, sessions.observeCompletedWithSets(today, today).first().size)
    }

    @Test fun quickAddKcalAutoAndUnknownMacros() = runBlocking {
        val auto = macros.save(null, today, null, null, 100, 200, 50, now)
        macros.get(auto.id)!!.let { assertEquals(4 * 100 + 4 * 200 + 9 * 50, it.kcal); assertTrue(it.kcalIsAuto) }
        val kcalOnly = macros.save(null, today, "  Pizza out ", 1100, null, null, null, now.plusSeconds(1))
        macros.get(kcalOnly.id)!!.let {
            assertEquals(1100, it.kcal); assertFalse(it.kcalIsAuto); assertNull(it.proteinG); assertNull(it.carbsG); assertNull(it.fatG)
            assertEquals("Pizza out", it.name); assertEquals("Pizza out", it.displayName)
        }
        assertEquals("Quick add", macros.save(null, today, " ", 50, null, null, null, now.plusSeconds(2)).displayName)
        assertEquals(3, macros.observeBetween(today, today).first().size)
        // kcal may only be left out when all three macros are known.
        assertThrows(IllegalArgumentException::class.java) { runBlocking { macros.save(null, today, null, null, 10, null, 5, now) } }
        Unit
    }

    @Test fun bodyUpsertLatest() = runBlocking {
        body.upsert(BodyEntry(today, 77.0, null, null, now))
        body.upsert(BodyEntry(today.minusDays(3), 78.0, 37.0, 12.0, now))
        assertEquals(today, body.latestDate())
        assertEquals(2, body.observeAll().first().size)
        body.delete(today)
        assertEquals(today.minusDays(3), body.latestDate())
    }

    @Test fun replaceAllAndClearAll() = runBlocking {
        assertTrue(backup.isEmpty())
        goals.save(goal())
        plans.save(plan(), listOf(planEx(0, PULL_UP)), listOf(GoalPlanLink("g1", "p1", 3)))
        val sid = sessions.startFromPlan("p1", now, today)
        sessions.finish(sid, null, null, now.plusSeconds(10))
        macros.save(null, today, null, 120, null, null, null, now)
        body.upsert(BodyEntry(today, 77.0, null, null, now))
        settings.setProteinTarget(180)
        assertFalse(backup.isEmpty())

        val snap = backup.snapshot()
        assertEquals(180, snap.settings.proteinTargetG)
        assertEquals(MockDataGenerator.exercises.size, snap.exercises.size)

        backup.clearAll()
        assertTrue(backup.isEmpty())
        assertEquals(0, db.exerciseDao().count())   // no built-in library to re-seed
        assertEquals(150, settings.current().proteinTargetG)

        backup.replaceAll(snap)
        assertEquals(snap, backup.snapshot())
        assertEquals(180, settings.current().proteinTargetG)
    }
}
