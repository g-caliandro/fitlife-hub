package com.lifehub.app.feature.copilot

import android.os.Looper
import com.lifehub.app.copilot.CopilotFixture
import com.lifehub.app.feature.settings.MemoryUiState
import com.lifehub.app.feature.settings.UserMemoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Copilot settings > Memory: view, add, edit (also renaming the key) and delete entries; invalid input shows an error. */
@RunWith(RobolectricTestRunner::class)
class UserMemoryViewModelTest {
    private val f = CopilotFixture(seed = false)
    private val scope = CoroutineScope(Dispatchers.Main)
    @After fun tearDown() { scope.cancel(); f.close() }

    private fun awaitUntil(what: String, cond: () -> Boolean) {
        repeat(400) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(25) }
        throw AssertionError("timeout waiting for $what")
    }

    @Test fun addEditRenameDelete() {
        val vm = UserMemoryViewModel(f.memory, f.time)
        var s: MemoryUiState? = null
        scope.launch { vm.state.collect { s = it } }
        vm.save(null, "Attrezzatura", "sbarra")
        awaitUntil("added") { s?.rows?.size == 1 }
        assertEquals("attrezzatura", s!!.rows.single().key); assertEquals("16 Nov 2026", s!!.rows.single().updated)
        vm.save("attrezzatura", "equipment", "sbarra e anelli")
        awaitUntil("renamed") { s?.rows?.singleOrNull()?.key == "equipment" }
        assertEquals("sbarra e anelli", s!!.rows.single().value)
        vm.save(null, "x", "   ")
        awaitUntil("error") { s?.error != null }
        assertNotNull(s!!.error)
        vm.delete("equipment")
        awaitUntil("deleted") { s?.rows?.isEmpty() == true }
    }
}
