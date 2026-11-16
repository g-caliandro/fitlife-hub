package com.lifehub.app.export

import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatRole
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.FoodPortion
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.data.model.PortionKind
import com.lifehub.app.data.repo.BackupRepositoryImpl
import com.lifehub.app.data.repo.NewChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZonedDateTime

/** Decision 7: export has portions, household measures and entry provenance; never chats or captures. */
@RunWith(RobolectricTestRunner::class)
class CopilotExportTest {
    private val f = CopilotFixture(seed = false)
    @After fun tearDown() = f.close()

    @Test fun exportIncludesPortionsMeasuresProvenanceAndExcludesChatsAndCaptures() = runBlocking {
        val thread = f.chat.createThread("Segreto", ChatOrigin.CHAT, f.now).id
        f.chat.append(thread, NewChatMessage(ChatRole.USER, "messaggio privato della chat"), f.now)
        val cap = f.captures.create(thread, ChatOrigin.CHAT, "testo della cattura", f.zone, f.now)
        f.db.foodDao().insert(Food("f1", FoodSource.OFF, "8001", "Merluzzo", "Findus", 150.0, 11.0, 9.0, 7.6, null, false, f.now, aliases = "il solito"))
        f.db.portionDao().upsertPortion(FoodPortion("p1", "f1", "scatola", 400.0, true, PortionKind.PACKAGE, f.now))
        f.db.foodEntryDao().insert(FoodEntry("e1", f.today, "f1", "Merluzzo", "Findus", 200.0, 300.0, 22.0, 18.0, 15.2, f.now,
            loggedAt = f.now, source = EntrySource.HISTORY, captureId = cap.id, servings = 0.5, portionLabel = "scatola", portionGrams = 400.0))
        val backup = BackupRepositoryImpl(f.db, f.settings)
        val text = JsonCodec.encode(backup.snapshot(), ZonedDateTime.now(f.zone), "x")
        assertTrue(text.contains("\"foodPortions\"")); assertTrue(text.contains("\"householdMeasures\""))
        assertTrue(text.contains("\"portionLabel\": \"scatola\"")); assertTrue(text.contains("\"source\": \"HISTORY\""))
        assertFalse(text.contains("messaggio privato")); assertFalse(text.contains("testo della cattura")); assertFalse(text.contains(cap.id))

        // Round trip restores the provenance (capture link excluded) and the portion.
        val decoded = JsonCodec.decode(text)
        backup.replaceAll(decoded)
        val e = f.db.foodEntryDao().getAll().single()
        assertEquals(0.5, e.servings!!, 0.0); assertEquals(f.now, e.loggedAt); assertEquals(null, e.captureId)
        assertEquals(400.0, f.db.portionDao().portions("f1").single().grams, 0.0)
        assertEquals("il solito", f.db.foodDao().get("f1")!!.aliases)
        assertTrue(f.db.portionDao().measures().isNotEmpty())
    }
}
