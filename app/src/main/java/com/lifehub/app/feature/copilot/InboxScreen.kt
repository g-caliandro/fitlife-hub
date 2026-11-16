package com.lifehub.app.feature.copilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.copilot.capture.PendingCards
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.data.model.Capture
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.navigation.Routes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InboxRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: InboxViewModel = viewModel(factory = InboxViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    InboxScreen(
        state, onBack, zone = c.time.zone,
        actions = CardActions(onChoose = vm::choose, onReply = vm::reply, onDiscard = vm::discard),
        onRetry = vm::retry, onOpenThread = { id -> onNavigate(Routes.copilot(id)) },
    )
}

/** Open requests (answer / reply / discard), failed captures (Retry) and captures still queued. */
@Composable
fun InboxScreen(
    state: InboxUiState,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    actions: CardActions = CardActions(),
    onRetry: (String) -> Unit = {},
    onOpenThread: (String) -> Unit = {},
) {
    val c = LhTheme.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            LhTopBar("Inbox", onBack = onBack, icon = Icons.Outlined.Inbox)
            if (state.loaded && state.isEmpty) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LhEmptyState("Nothing waiting", Modifier.padding(24.dp),
                        description = "Questions from the copilot and voice notes that could not be processed show up here.", icon = Icons.Outlined.Inbox)
                }
                return@Column
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("inbox-list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.requests.isNotEmpty()) {
                    item(key = "h-req") { LhSectionHeader("Questions", topPadding = 8.dp) }
                    items(state.requests, key = { "p-" + it.id }) { p ->
                        PendingInputCardView(PendingCards.from(p), p, enabled = true, actions = actions,
                            modifier = Modifier.testTag("inbox-request"))
                    }
                }
                if (state.failed.isNotEmpty()) {
                    item(key = "h-failed") { LhSectionHeader("Failed") }
                    items(state.failed, key = { "f-" + it.id }) { cap -> CaptureRow(cap, zone, onRetry, onOpenThread) }
                }
                if (state.queued.isNotEmpty()) {
                    item(key = "h-queued") { LhSectionHeader("In queue") }
                    items(state.queued, key = { "q-" + it.id }) { cap -> CaptureRow(cap, zone, onRetry, onOpenThread) }
                }
            }
        }
    }
}

@Composable
private fun CaptureRow(cap: Capture, zone: ZoneId, onRetry: (String) -> Unit, onOpenThread: (String) -> Unit) {
    val c = LhTheme.colors
    val failed = cap.status == CaptureStatus.FAILED
    LhCard(Modifier.fillMaxWidth().testTag(if (failed) "inbox-failed" else "inbox-queued"), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule, null,
                tint = if (failed) c.destructive else c.mutedForeground, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    failed -> "Couldn't process"
                    cap.transcript == null && cap.inputText == null -> "Waiting to be transcribed"
                    else -> "Waiting for the copilot"
                },
                style = LhType.heading, color = c.foreground, modifier = Modifier.weight(1f),
            )
            Text(TIME.format(cap.createdAt.atZone(zone)), style = LhType.captionMuted)
        }
        (cap.transcript ?: cap.inputText)?.let {
            Spacer(Modifier.height(4.dp))
            Text("“$it”", style = LhType.body, color = c.foreground)
        } ?: run { Spacer(Modifier.height(4.dp)); Text("Voice note (audio saved)", style = LhType.body, color = c.mutedForeground) }
        if (failed) cap.errorMessage?.let { Spacer(Modifier.height(4.dp)); Text(it, style = LhType.caption, color = c.mutedForeground) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failed) LhButton("Retry", { onRetry(cap.id) }, Modifier.testTag("inbox-retry"), variant = LhButtonVariant.Tonal,
                size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Refresh)
            cap.threadId?.let { t ->
                LhButton("Open chat", { onOpenThread(t) }, variant = LhButtonVariant.Outline, size = LhButtonSize.Sm,
                    leadingIcon = Icons.AutoMirrored.Outlined.Chat)
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)
