package com.lifehub.app.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.feature.body.restoreBody
import com.lifehub.app.feature.nutrition.copyLabel
import com.lifehub.app.feature.nutrition.restore
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
class FormUndoTest {
    private lateinit var db: LifeHubDatabase
    private lateinit var macros: MacroRepositoryImpl
    private lateinit var body: BodyRepositoryImpl
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val d = LocalDate.of(2026, 11, 12)

    @Before fun setUp() {
        db = LifeHubDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        macros = MacroRepositoryImpl(db); body = BodyRepositoryImpl(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun latestBeforePicksMostRecentEarlierDay() = runBlocking {
        macros.upsert(d.minusDays(5), 100, 200, 50, null, now)
        macros.upsert(d.minusDays(2), 150, 250, 60, 2500, now)
        macros.upsert(d, 1, 1, 1, null, now)
        assertEquals(d.minusDays(2), macros.latestBefore(d)!!.date)
        assertNull(macros.latestBefore(d.minusDays(5)))
    }

    @Test fun macroUndoRestoresPreviousOrDeletes() = runBlocking {
        macros.upsert(d, 100, 200, 50, null, now)
        val prev = macros.get(d)
        macros.upsert(d, 150, 250, 60, 3000, now)
        macros.restore(d, prev, now)
        val r = macros.get(d)!!
        assertEquals(100, r.proteinG); assertTrue(r.kcalIsAuto)
        macros.restore(d, null, now)
        assertNull(macros.get(d))
    }

    @Test fun bodyUndo() = runBlocking {
        val prev = BodyEntry(d, 77.0, 37.0, 12.0, now)
        body.upsert(prev)
        body.upsert(BodyEntry(d, 78.0, null, null, now))
        restoreBody(body, d, prev)
        assertEquals(77.0, body.get(d)!!.weightKg, 1e-9)
        restoreBody(body, d, null)
        assertNull(body.get(d))
    }

    @Test fun copyLabels() {
        assertEquals("Copy yesterday", copyLabel(d.minusDays(1), d))
        assertEquals("Copy Mon", copyLabel(LocalDate.of(2026, 11, 9), d))
    }
}
