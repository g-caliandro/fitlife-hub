package com.lifehub.app.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.feature.body.restoreBody
import com.lifehub.app.feature.nutrition.copyLabel
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
    private lateinit var macros: QuickAddRepositoryImpl
    private lateinit var body: BodyRepositoryImpl
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val d = LocalDate.of(2026, 11, 12)

    @Before fun setUp() {
        db = LifeHubDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        macros = QuickAddRepositoryImpl(db); body = BodyRepositoryImpl(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun latestBeforePicksMostRecentEarlierDay() = runBlocking {
        macros.save(null, d.minusDays(5), null, null, 100, 200, 50, now)
        macros.save(null, d.minusDays(2), "Lunch", 2500, 150, null, null, now)
        macros.save(null, d, null, 300, null, null, null, now)
        assertEquals(d.minusDays(2), macros.latestBefore(d)!!.date)
        assertNull(macros.latestBefore(d.minusDays(5)))
    }

    @Test fun quickAddUndoRestoresPreviousOrDeletes() = runBlocking {
        val first = macros.save(null, d, "Snack", null, 100, 200, 50, now)
        val prev = macros.get(first.id)!!
        macros.save(first.id, d, "Snack 2", 3000, null, null, null, now.plusSeconds(5))
        assertEquals(1, macros.getDay(d).size)                      // edit, not a new row
        assertEquals(now, macros.get(first.id)!!.createdAt)         // keeps its place in the list
        macros.restore(prev)
        val r = macros.get(first.id)!!
        assertEquals(100, r.proteinG); assertTrue(r.kcalIsAuto); assertEquals("Snack", r.name)
        macros.delete(first.id)
        assertNull(macros.get(first.id))
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
