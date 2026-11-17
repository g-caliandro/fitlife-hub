package com.lifehub.app.feature.copilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
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
import com.lifehub.app.copilot.agent.dayLabel
import com.lifehub.app.copilot.capture.IllegalTransitionException
import com.lifehub.app.copilot.capture.PendingInputStore
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconAction
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.ChatThread
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.repo.ChatStore
import com.lifehub.app.di.AppContainer
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One conversation in the list: title, last update, where it started and how many of its requests are still open. */
data class ThreadRow(val id: String, val title: String, val updatedAt: Instant, val origin: ChatOrigin, val openRequests: Int)

/** Threads, most recently updated first, with their open-request count (requests are linked to a thread). Pure. */
fun threadRows(threads: List<ChatThread>, open: List<PendingInput>): List<ThreadRow> {
    val byThread = open.groupingBy { it.threadId }.eachCount()
    return threads.sortedByDescending { it.updatedAt }.map { ThreadRow(it.id, it.title, it.updatedAt, it.origin, byThread[it.id] ?: 0) }
}

/** "Today · 14:05", "Yesterday · 20:30", "Sun 15 Nov · 09:12". */
fun threadTime(at: Instant, zone: ZoneId, today: LocalDate): String {
    val t = at.atZone(zone)
    return dayLabel(t.toLocalDate(), today) + " · " + t.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Thread chosen in the conversations list, handed to the chat screen underneath (one-shot, like ScanPick). */
object ThreadPick {
    private val _threadId = MutableStateFlow<String?>(null)
    val threadId: StateFlow<String?> = _threadId.asStateFlow()
    fun pick(id: String) { _threadId.value = id }
    fun consume() { _threadId.value = null }
}

/**
 * Deleting a conversation: its open requests are discarded first (through the same path as the Discard button, so their
 * notifications go away too), then the thread and its messages are removed. Captures and the diary are not touched.
 */
class ThreadDeleter(private val chat: ChatStore, private val pending: PendingInputStore, private val discard: suspend (String) -> Unit) {
    suspend fun delete(threadId: String) {
        pending.observeByThread(threadId).first().filter { it.status == com.lifehub.app.data.model.PendingStatus.OPEN }.forEach {
            try { discard(it.id) } catch (_: IllegalTransitionException) {}
        }
        chat.deleteThread(threadId)
    }
}

data class ThreadsUiState(val rows: List<ThreadRow> = emptyList(), val today: LocalDate, val zone: ZoneId, val loaded: Boolean = false)

class ThreadsViewModel(chat: ChatStore, pending: PendingInputStore, private val deleter: ThreadDeleter, private val time: TimeSource) : ViewModel() {
    val state: StateFlow<ThreadsUiState> = combine(chat.observeThreads(), pending.observeOpen()) { t, p ->
        ThreadsUiState(threadRows(t, p), time.today(), time.zone, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadsUiState(today = time.today(), zone = time.zone))

    fun delete(id: String) { viewModelScope.launch { deleter.delete(id) } }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { ThreadsViewModel(c.chat, c.pendingInputs, ThreadDeleter(c.chat, c.pendingInputs) { c.captureProcessor.discard(it) }, c.time) }
        }
    }
}

@Composable
fun ThreadsRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: ThreadsViewModel = viewModel(factory = ThreadsViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    // The chat screen is underneath: hand it the chosen thread and go back to it.
    ThreadsScreen(state, onBack, onOpen = { ThreadPick.pick(it); onNavigate(Routes.BACK) },
        onNew = { ThreadPick.pick(ChatViewModel.NEW_THREAD); onNavigate(Routes.BACK) }, onDelete = vm::delete)
}

/** Past conversations (design doc §10): open one, delete one, start a new one. Badge = open requests of that thread. */
@Composable
fun ThreadsScreen(
    state: ThreadsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    onNew: () -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    val c = LhTheme.colors
    var confirm by remember { mutableStateOf<ThreadRow?>(null) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar("Conversations", onBack = onBack, icon = Icons.Outlined.Forum, actions = {
            LhIconAction(Icons.Outlined.Add, "New conversation", onNew, Modifier.testTag("threads-new"))
        })
        if (state.loaded && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LhEmptyState("No conversations yet", Modifier.padding(24.dp), description = "Your chats with the copilot and your voice captures appear here.",
                    icon = Icons.Outlined.Forum, actionText = "New conversation", onAction = onNew, actionIcon = Icons.Outlined.Add)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().testTag("threads-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.rows, key = { it.id }) { r -> ThreadRowView(r, state, onOpen, onDelete = { confirm = r }) }
        }
    }
    confirm?.let { r ->
        LhConfirmDialog(
            "Delete this conversation?",
            "The messages are removed from this phone." + (if (r.openRequests > 0) " Its ${r.openRequests} open question(s) will be discarded." else "") +
                " Meals and weights already logged stay in your diary.",
            "Delete", { onDelete(r.id); confirm = null }, { confirm = null },
        )
    }
}

@Composable
private fun ThreadRowView(r: ThreadRow, state: ThreadsUiState, onOpen: (String) -> Unit, onDelete: () -> Unit) {
    val c = LhTheme.colors
    LhCard(Modifier.fillMaxWidth().testTag("thread-row-${r.id}"), onClick = { onOpen(r.id) }, contentPadding = PaddingValues(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (r.origin == ChatOrigin.QUICK) Icons.Outlined.Mic else Icons.Outlined.ChatBubbleOutline, null, tint = c.mutedForeground, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title.ifBlank { "Conversation" }, style = LhType.bodyLarge, color = c.foreground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(threadTime(r.updatedAt, state.zone, state.today), style = LhType.captionMuted)
            }
            if (r.openRequests > 0) {
                Box(Modifier.testTag("thread-badge-${r.id}").size(22.dp).background(c.destructive, LhRadius.fullShape), contentAlignment = Alignment.Center) {
                    Text(if (r.openRequests > 9) "9+" else "${r.openRequests}", style = LhType.labelSmall, color = c.destructiveForeground)
                }
            }
            LhIconButton(Icons.Outlined.DeleteOutline, "Delete conversation", onDelete, Modifier.testTag("thread-delete-${r.id}"))
        }
    }
}
