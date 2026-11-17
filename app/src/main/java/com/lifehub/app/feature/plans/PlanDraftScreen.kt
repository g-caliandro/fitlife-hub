package com.lifehub.app.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.copilot.agent.PlanDraftCard
import com.lifehub.app.copilot.capture.IllegalTransitionException
import com.lifehub.app.copilot.plan.DraftCodec
import com.lifehub.app.copilot.plan.PlanDraftStore
import com.lifehub.app.copilot.plan.overlappingPlans
import com.lifehub.app.copilot.tools.PlanTools
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhDialogContainer
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.feature.copilot.DraftContent
import com.lifehub.app.feature.copilot.draftStatusLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Approve confirmation (owner decision 4): lists the active plans on the same weekdays and offers to archive them with an
 * opt-in checkbox, unchecked by default. Without overlapping plans it is a plain confirmation.
 */
@Composable
fun ApproveDraftDialog(overlapping: List<String>, onConfirm: (archiveOverlapping: Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = LhTheme.colors
    var archive by remember { mutableStateOf(false) }
    LhDialogContainer(onDismiss) {
        Text("Approve this plan?", style = LhType.heading, color = c.foreground)
        Text("Its plans become active and appear in Train. Your current plans stay as they are" +
            if (overlapping.isEmpty()) "." else " unless you choose to archive the ones on the same days.",
            style = LhType.body, color = c.mutedForeground, modifier = Modifier.padding(top = 8.dp))
        if (overlapping.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).toggleable(archive, role = Role.Checkbox, onValueChange = { archive = it }).testTag("approve-archive"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(archive, null, colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.mutedForeground, checkmarkColor = c.primaryForeground))
                Column(Modifier.padding(start = 8.dp)) {
                    Text("Archive plans on the same days", style = LhType.body, color = c.foreground)
                    Text(overlapping.joinToString(", "), style = LhType.captionMuted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            LhButton("Cancel", onDismiss, variant = LhButtonVariant.Outline)
            LhButton("Approve", { onConfirm(archive) }, Modifier.testTag("approve-confirm"))
        }
    }
}

data class PlanDraftUiState(
    val loaded: Boolean = false,
    val card: PlanDraftCard? = null,
    val status: PlanDraftStatus = PlanDraftStatus.DRAFT,
    val overlapping: List<String> = emptyList(),
)

class PlanDraftViewModel(private val drafts: PlanDraftStore, plans: PlanRepository, private val time: TimeSource, private val draftId: String) : ViewModel() {
    val state: StateFlow<PlanDraftUiState> = combine(drafts.observe(draftId), plans.observePlans()) { d, ps ->
        if (d == null) PlanDraftUiState(loaded = true)
        else DraftCodec.decode(d).let { p -> PlanDraftUiState(true, PlanTools.card(d, p), d.status, overlappingPlans(p, ps).map { it.plan.name }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDraftUiState())

    fun approve(archive: Boolean) = viewModelScope.launch { drafts.approve(draftId, archive, time.today(), time.now()) }
    fun reject() = viewModelScope.launch { try { drafts.reject(draftId, time.now()) } catch (_: IllegalTransitionException) {} }
    fun delete() = viewModelScope.launch { drafts.delete(draftId) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer {
                val h: SavedStateHandle = createSavedStateHandle()
                PlanDraftViewModel(c.planDrafts, c.plans, c.time, checkNotNull(h.get<String>("draftId")))
            }
        }
    }
}

@Composable
fun PlanDraftRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: PlanDraftViewModel = viewModel(factory = PlanDraftViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    PlanDraftScreen(state, onBack, onApprove = { vm.approve(it) }, onReject = { vm.reject() }, onDelete = { vm.delete(); onBack() })
}

/** A copilot plan draft (workouts screen): its sessions; Approve (with the archive opt-in), Reject, Delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanDraftScreen(
    state: PlanDraftUiState,
    onBack: () -> Unit,
    onApprove: (Boolean) -> Unit = {},
    onReject: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var approving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LhTopBar("Plan draft", onBack = onBack, icon = Icons.Outlined.EditNote)
        val card = state.card
        if (card == null) {
            if (state.loaded) LhEmptyState("Draft not found", Modifier.fillMaxWidth().padding(24.dp), description = "It was deleted.")
            return@Column
        }
        val open = state.status == PlanDraftStatus.DRAFT
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(draftStatusLabel(state.status), style = LhType.heading, color = LhTheme.colors.foreground, modifier = Modifier.weight(1f).testTag("draft-status"))
                if (open) LhBadge("Not active", variant = LhBadgeVariant.Warning)
            }
            Text(card.title, style = LhType.body, color = LhTheme.colors.foreground, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            LhCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) { DraftContent(card) }
            Text(
                if (open) "Proposed by the copilot. Nothing changes in your plans until you approve it; progression then raises the targets as usual."
                else "This draft is closed. The copilot never brings it back.",
                style = LhType.captionMuted, modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (open) {
                    LhButton("Approve", { approving = true }, Modifier.testTag("draft-approve"), leadingIcon = Icons.Outlined.Check)
                    LhButton("Reject", onReject, Modifier.testTag("draft-reject"), variant = LhButtonVariant.Outline, leadingIcon = Icons.Outlined.Close)
                }
                LhButton("Delete", { deleting = true }, Modifier.testTag("draft-delete"), variant = LhButtonVariant.Outline, leadingIcon = Icons.Outlined.DeleteOutline)
            }
        }
    }
    if (approving) ApproveDraftDialog(state.overlapping, onConfirm = { onApprove(it); approving = false }, onDismiss = { approving = false })
    if (deleting) LhConfirmDialog("Delete this draft?", "The draft is removed. Plans you already approved are not affected.", "Delete",
        { deleting = false; onDelete() }, { deleting = false })
}
