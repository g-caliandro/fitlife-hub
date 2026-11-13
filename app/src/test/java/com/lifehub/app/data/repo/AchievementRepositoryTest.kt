package com.lifehub.app.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.db.LifeHubDatabase
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class AchievementRepositoryTest {
    private lateinit var db: LifeHubDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repo: AchievementRepositoryImpl
    private lateinit var goals: GoalRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl
    private val today = LocalDate.of(2026, 11, 12)
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val pu = "ex-pull-up"

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = LifeHubDatabase.inMemory(ctx)
        settings = SettingsRepository(ctx).also { it.clear() }
        repo = AchievementRepositoryImpl(db, settings); goals = GoalRepositoryImpl(db); sessions = SessionRepositoryImpl(db)
        runBlocking { db.exerciseDao().insertAll(MockDataGenerator.exercises) }
    }
    @After fun tearDown() { db.close() }

    private suspend fun completed(id: String, date: LocalDate, reps: Int, w: Double? = null) {
        val s = WorkoutSession(id, null, "P", date, date.atTime(18, 0).toInstant(ZoneOffset.UTC), date.atTime(19, 0).toInstant(ZoneOffset.UTC), 3600,
            SessionStatus.COMPLETED, null, null, false, 0, null)
        sessions.saveFull(s, listOf(SessionSet("$id-0", id, pu, "Pull-up", 0, 0, TargetType.REPS, 5, reps, null, w, SetStatus.DONE, null, 90)))
    }

    @Test fun goalAchievedCreatesBigAchievementAndUnmarkRemoves() = runBlocking {
        goals.save(Goal("g1", "Muscle Up", null, today.minusDays(30), today.plusDays(30), false, GoalState.ACTIVE, null, now))
        goals.setState("g1", GoalState.ACHIEVED, today)
        val a = repo.getAll().single()
        assertEquals(AchievementKind.GOAL_ACHIEVED, a.kind); assertEquals("GOAL:g1", a.dedupeKey); assertEquals("Muscle Up", a.title); assertEquals(today, a.achievedOn)
        goals.setState("g1", GoalState.ACHIEVED, today)   // idempotent
        assertEquals(1, repo.getAll().size)
        goals.setState("g1", GoalState.ACTIVE, today)
        assertTrue(repo.getAll().isEmpty())
        goals.setState("g1", GoalState.ACHIEVED, today); goals.delete("g1")
        assertTrue(repo.getAll().isEmpty())
    }

    @Test fun manualSessionDetectsPrIdempotentlyAndEditResetsStale() = runBlocking {
        completed("old", today.minusDays(10), 8)
        completed("man", today.minusDays(2), 12)
        repeat(2) { repo.onSessionFinished("man", now) }
        assertEquals(1, repo.getAll().count { it.kind == AchievementKind.PR_REPS })
        // edit: actual lowered below the previous best -> stale PR removed after reset + re-detect
        val s = db.sessionDao().get("man")!!
        sessions.saveFull(s, listOf(SessionSet("man-0", "man", pu, "Pull-up", 0, 0, TargetType.REPS, 5, 7, null, null, SetStatus.DONE, null, 90)))
        repo.resetSession("man"); repo.onSessionFinished("man", now)
        assertEquals(0, repo.getAll().count { it.kind == AchievementKind.PR_REPS })
    }

    @Test fun finishPersistsPrOnceAndDiscardedSessionLeavesNothing() = runBlocking {
        completed("s1", today.minusDays(14), 8)
        completed("s2", today.minusDays(7), 8)
        assertTrue(repo.onSessionFinished("s2", now).isEmpty())
        completed("s3", today.minusDays(1), 10)
        val first = repo.onSessionFinished("s3", now)
        assertEquals(listOf("PR_REPS:ex=$pu:value=10"), first.map { it.dedupeKey })
        assertEquals("s3", first.single().sessionId)
        assertTrue(repo.onSessionFinished("s3", now).isEmpty())          // idempotent
        assertEquals(1, repo.getAll().size)
        sessions.delete("s3")                                              // deleting the session drops its PR
        assertTrue(repo.getAll().isEmpty())
    }

    @Test fun deletedAchievementDoesNotComeBackButRecalculateRestoresIt() = runBlocking {
        completed("s1", today.minusDays(14), 8); completed("s2", today.minusDays(7), 9)
        assertEquals(1, repo.recalculate(today, 150, ZoneOffset.UTC, now).size)
        val a = repo.getAll().single()
        repo.delete(a.id)
        assertTrue(repo.detectPeriods(today, 150, ZoneOffset.UTC, now).isEmpty() && repo.getAll().isEmpty())
        assertEquals(1, repo.recalculate(today, 150, ZoneOffset.UTC, now).size)
        assertEquals(0, repo.recalculate(today, 150, ZoneOffset.UTC, now).size)
        assertEquals(1, repo.observeRecent(3).first().size)
    }

    @Test fun detectPeriodsStoresNoConsistencyAndPurgesLegacyRows() = runBlocking {
        val mon = LocalDate.of(2026, 11, 2)
        db.macroDao().insertAll((0..6).map { MacroDay(mon.plusDays(it.toLong()), 160, 200, 70, 2000, true, now) })
        db.achievementDao().insertAll(listOf(Achievement("old", AchievementKind.PROTEIN_WEEK, "Protein target hit all week", null, 150.0, "g",
            null, null, null, mon.plusDays(6), now, "PROTEIN_WEEK:2026-W45")))
        assertTrue(repo.detectPeriods(today, 150, ZoneOffset.UTC, now).isEmpty())
        assertTrue(repo.getAll().isEmpty())
    }
}
