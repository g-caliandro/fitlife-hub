package com.lifehub.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.copilot.memory.MemorySaveResult
import com.lifehub.app.copilot.memory.UserMemoryStore
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.UserMemory
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MemoryRow(val key: String, val value: String, val updated: String)

data class MemoryUiState(val rows: List<MemoryRow> = emptyList(), val loaded: Boolean = false, val error: String? = null) {
    val full: Boolean get() = rows.size >= UserMemoryStore.MAX_ENTRIES
}

fun memoryRows(entries: List<UserMemory>, zone: ZoneId): List<MemoryRow> {
    val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    return entries.sortedBy { it.key }.map { MemoryRow(it.key, it.value, it.updatedAt.atZone(zone).toLocalDate().format(f)) }
}

class UserMemoryViewModel(private val store: UserMemoryStore, private val time: TimeSource) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val state: StateFlow<MemoryUiState> = combine(store.observe().map { memoryRows(it, time.zone) }, error) { rows, e -> MemoryUiState(rows, true, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryUiState())

    /** Saves [key] = [value]; renaming an entry ([oldKey] != new key) deletes the old one after the new one is saved. */
    fun save(oldKey: String?, key: String, value: String) = viewModelScope.launch {
        when (val r = store.save(key, value, time.now())) {
            is MemorySaveResult.Invalid -> error.value = r.message
            is MemorySaveResult.Saved -> { error.value = null; if (oldKey != null && oldKey != r.entry.key) store.delete(oldKey) }
        }
    }

    fun delete(key: String) = viewModelScope.launch { store.delete(key) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { UserMemoryViewModel(c.userMemory, c.time) } }
    }
}

@Composable
fun UserMemoryRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: UserMemoryViewModel = viewModel(factory = UserMemoryViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    UserMemoryScreen(state, onBack, onSave = { old, k, v -> vm.save(old, k, v) }, onDelete = { vm.delete(it) })
}

/** User memory (design doc §10): what the copilot remembers about you; view, edit, add and delete entries. */
@Composable
fun UserMemoryScreen(
    state: MemoryUiState,
    onBack: () -> Unit,
    onSave: (oldKey: String?, key: String, value: String) -> Unit = { _, _, _ -> },
    onDelete: (String) -> Unit = {},
) {
    var editing by remember { mutableStateOf<MemoryRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LhTopBar("Memory", onBack = onBack, icon = Icons.Outlined.Psychology)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Short facts the copilot keeps about you and sends with every request: goals, equipment, training days, " +
                    "preferences, foods you don't like, your level on key exercises. It saves them only in the chat, never from " +
                    "voice captures. List disliked foods under disliked_foods, separated by commas. Included in your data export.",
                style = LhType.captionMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            )
            state.error?.let { Text(it, style = LhType.caption, color = LhTheme.colors.destructive, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) }
            if (state.loaded && state.rows.isEmpty()) {
                Text("Nothing saved yet.", style = LhType.captionMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp).testTag("memory-empty"))
            } else if (state.rows.isNotEmpty()) {
                LhGroupCard(Modifier.fillMaxWidth().testTag("memory-list")) {
                    state.rows.forEachIndexed { i, r ->
                        LhListRow(r.key, Modifier.testTag("memory-row"), subtitle = r.value, onClick = { editing = r }, showDivider = i < state.rows.lastIndex,
                            trailing = { Text(r.updated, style = LhType.captionMuted) })
                    }
                }
            }
            if (!state.full) LhAddRow({ adding = true }, Modifier.padding(top = 8.dp).testTag("memory-add"), label = "Add fact")
            else Text("Memory is full (${UserMemoryStore.MAX_ENTRIES} facts): delete one to add another.", style = LhType.captionMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
    if (adding || editing != null) MemoryDialog(editing, onDismiss = { adding = false; editing = null },
        onSave = { k, v -> onSave(editing?.key, k, v); adding = false; editing = null },
        onDelete = editing?.let { r -> { onDelete(r.key); editing = null } })
}

@Composable
private fun MemoryDialog(row: MemoryRow?, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: (() -> Unit)?) {
    var key by remember(row) { mutableStateOf(row?.key ?: "") }
    var value by remember(row) { mutableStateOf(row?.value ?: "") }
    LhDialogContainer(onDismiss) {
        Text(if (row == null) "New fact" else "Edit fact", style = LhType.heading, color = LhTheme.colors.foreground)
        Spacer(Modifier.height(16.dp))
        LhTextField(key, { key = it }, Modifier.fillMaxWidth().testTag("memory-key"), label = "Key", placeholder = "e.g. disliked_foods", maxLength = UserMemoryStore.MAX_KEY)
        Spacer(Modifier.height(12.dp))
        LhTextField(value, { value = it }, Modifier.fillMaxWidth().testTag("memory-value"), label = "Value", placeholder = "e.g. funghi, tonno", singleLine = false, maxLength = UserMemoryStore.MAX_VALUE)
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (onDelete != null) LhButton("Delete", onDelete, Modifier.testTag("memory-delete"), variant = LhButtonVariant.Outline)
            Spacer(Modifier.weight(1f))
            LhButton("Cancel", onDismiss, variant = LhButtonVariant.Outline)
            LhButton("Save", { onSave(key, value) }, Modifier.testTag("memory-save"), enabled = key.isNotBlank() && value.isNotBlank())
        }
    }
}
