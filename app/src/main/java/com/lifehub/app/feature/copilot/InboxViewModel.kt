package com.lifehub.app.feature.copilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.copilot.capture.CaptureStore
import com.lifehub.app.copilot.capture.PendingInputStore
import com.lifehub.app.copilot.notify.CopilotActions
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Inbox content: open requests (oldest first) and captures waiting for the worker or failed (newest first). */
data class InboxUiState(
    val requests: List<PendingInput> = emptyList(),
    val queued: List<Capture> = emptyList(),
    val failed: List<Capture> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = requests.isEmpty() && queued.isEmpty() && failed.isEmpty()
}

/** Inbox badge: open requests + failed captures (things that need the user). */
fun inboxBadge(pending: PendingInputStore, captures: CaptureStore): Flow<Int> =
    combine(pending.observeOpenCount(), captures.observeByStatus(listOf(CaptureStatus.FAILED))) { open, failed -> open + failed.size }

/**
 * Design doc §7/§8 inbox: answers and discards go through [CopilotActions] (the same store as the chat and the notification
 * actions, so answering here closes the request everywhere); the resumed turn runs in the CaptureWorker.
 */
class InboxViewModel(
    pending: PendingInputStore,
    captures: CaptureStore,
    private val actions: CopilotActions,
) : ViewModel() {
    val state: StateFlow<InboxUiState> = combine(
        pending.observeOpen(),
        captures.observeByStatus(listOf(CaptureStatus.RECORDED, CaptureStatus.PROCESSING, CaptureStatus.FAILED)),
    ) { open, caps ->
        InboxUiState(
            requests = open,
            queued = caps.filter { it.status == CaptureStatus.RECORDED || it.status == CaptureStatus.PROCESSING },
            failed = caps.filter { it.status == CaptureStatus.FAILED },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun choose(pendingId: String, option: Int) { viewModelScope.launch { actions.answer(pendingId, option = option) } }
    fun reply(pendingId: String, text: String) { if (text.isNotBlank()) viewModelScope.launch { actions.answer(pendingId, text = text) } }
    fun discard(pendingId: String) { viewModelScope.launch { actions.discard(pendingId) } }
    fun retry(captureId: String) { viewModelScope.launch { actions.retry(captureId) } }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { InboxViewModel(c.pendingInputs, c.captures, c.copilotActions) } }
    }
}
