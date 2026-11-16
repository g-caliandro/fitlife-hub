package com.lifehub.app.feature.copilot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lifehub.app.copilot.capture.CaptureStage
import com.lifehub.app.copilot.stt.RecordingLevel
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.copilot.agent.DailySummaryCard
import com.lifehub.app.copilot.agent.MealLoggedCard
import com.lifehub.app.copilot.agent.PendingInputCard
import com.lifehub.app.copilot.agent.ProductCard
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconAction
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.navigation.DiaryJump
import com.lifehub.app.navigation.Routes
import java.time.LocalDate

@Composable
fun ChatRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshKey() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stopRecording() }  // leaving the chat saves what was said
    AskNotificationPermissionOnFirstUse()
    val ctx = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.startRecording() }
    ChatScreen(
        state, onBack, onSend = vm::send, onRetry = vm::retry,
        onMic = { if (hasMicPermission(ctx)) vm.startRecording() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
        onStopMic = vm::stopRecording, onCancelMic = vm::cancelRecording,
        onOpenInbox = { onNavigate(Routes.COPILOT_INBOX) },
        onOpenSettings = { onNavigate(Routes.SETTINGS_COPILOT) },
        onOpenDiary = { d -> DiaryJump.request(d); onNavigate(Routes.CALENDAR) },
        actions = CardActions(
            onUndo = vm::undo, onChoose = { id, i -> vm.answer(id, option = i) },
            onReply = { id, t -> vm.answer(id, text = t) }, onDiscard = vm::discard,
        ),
    )
}

/** Copilot chat: text bubbles + native cards, composer (text only in phase 2), explicit working / error / no-key states. */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onSend: (String) -> Boolean = { true },
    onRetry: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDiary: (LocalDate) -> Unit = {},
    initialDraft: String = "",
    actions: CardActions = CardActions(),
    onMic: () -> Unit = {},
    onStopMic: () -> Unit = {},
    onCancelMic: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
) {
    val c = LhTheme.colors
    val list = rememberLazyListState()
    val rows = state.items.size + (if (state.working) 1 else 0) + (if (state.problem != null) 1 else 0)
    LaunchedEffect(rows) { if (rows > 0) list.animateScrollToItem(rows - 1) }
    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        LhTopBar("Copilot", onBack = onBack, icon = Icons.Outlined.AutoAwesome, actions = {
            InboxAction(state.inboxCount, onOpenInbox)
            LhIconAction(Icons.Outlined.Settings, "Copilot settings", onOpenSettings, Modifier.testTag("chat-settings"))
        })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.items.isEmpty() && !state.working && state.problem == null) {
                LhEmptyState(
                    "Ask your copilot",
                    Modifier.align(Alignment.Center).padding(24.dp),
                    description = "Log meals and ask about your food diary, targets, goals, workouts and weight. Try “90 g di riso basmati e 180 g di petto di pollo” or “Quante proteine mi mancano oggi?”",
                    icon = Icons.Outlined.AutoAwesome,
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().testTag("chat-list"), state = list,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.User -> Bubble(item.text, mine = true)
                        is ChatItem.Assistant -> Bubble(item.text, mine = false)
                        is ChatItem.Card -> when (val card = item.card) {
                            is DailySummaryCard -> DailySummaryCardView(card, state.today, onOpenDiary)
                            is MealLoggedCard -> MealLoggedCardView(card, card.captureId in state.undoneCaptures, state.today, actions.onUndo, onOpenDiary)
                            is PendingInputCard -> PendingInputCardView(card, state.pending[card.pendingInputId], enabled = !state.working, actions)
                            is ProductCard -> ProductCardView(card)
                        }
                    }
                }
                if (state.working) item(key = "working") { Working(state.stage) }
                state.problem?.let { p -> item(key = "problem") { Problem(p, onRetry, onOpenSettings) } }
            }
        }
        HorizontalDivider(color = c.border, thickness = 1.dp)
        Composer(
            enabled = !state.working && state.problem != ChatProblem.MissingKey, onSend = onSend, initialDraft = initialDraft,
            recording = state.recording, onMic = onMic, onStopMic = onStopMic, onCancelMic = onCancelMic,
        )
    }
}

/** Plain text bubble: model text is short plain text, never rendered as markdown. */
@Composable
private fun Bubble(text: String, mine: Boolean) {
    val c = LhTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text, style = LhType.body,
            color = if (mine) c.onPrimaryContainer else c.foreground,
            modifier = Modifier.widthIn(max = 300.dp)
                .background(if (mine) c.primaryContainer else c.card, LhRadius.lgShape)
                .then(if (mine) Modifier else Modifier.border(1.dp, c.border, LhRadius.lgShape))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag(if (mine) "bubble-user" else "bubble-assistant"),
        )
    }
}

@Composable
private fun Working(stage: CaptureStage) {
    val c = LhTheme.colors
    Row(Modifier.testTag(if (stage == CaptureStage.TRANSCRIBING) "chat-transcribing" else "chat-working"), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.mutedForeground)
        Spacer(Modifier.width(8.dp))
        Text(if (stage == CaptureStage.TRANSCRIBING) "Transcribing…" else "Thinking…", style = LhType.captionMuted)
    }
}

/** Inbox icon with the number of open requests + failed captures. */
@Composable
private fun InboxAction(count: Int, onClick: () -> Unit) {
    val c = LhTheme.colors
    Box {
        LhIconAction(Icons.Outlined.Inbox, "Inbox", onClick, Modifier.testTag("chat-inbox"))
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(16.dp).background(c.destructive, LhRadius.fullShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (count > 9) "9+" else "$count", style = LhType.labelSmall, color = c.destructiveForeground) }
        }
    }
}

@Composable
private fun Problem(p: ChatProblem, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val c = LhTheme.colors
    LhCard(Modifier.fillMaxWidth().testTag("chat-problem"), contentPadding = PaddingValues(16.dp)) {
        when (p) {
            ChatProblem.MissingKey -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Key, null, tint = c.mutedForeground, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No API key set", style = LhType.heading, color = c.foreground)
                }
                Spacer(Modifier.padding(top = 4.dp))
                Text("Add your language model key in Copilot settings to start chatting. The rest of the app works without it.", style = LhType.body, color = c.mutedForeground)
                Spacer(Modifier.padding(top = 12.dp))
                LhButton("Open Copilot settings", onOpenSettings, Modifier.testTag("chat-open-settings"), size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Settings)
            }
            is ChatProblem.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (p.network) Icons.Outlined.WifiOff else Icons.Outlined.ErrorOutline, null, tint = c.destructive, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (p.network) "Connection problem" else "Something went wrong", style = LhType.heading, color = c.foreground)
                }
                Spacer(Modifier.padding(top = 4.dp))
                Text(p.message, style = LhType.body, color = c.mutedForeground)
                if (p.retryable) {
                    Spacer(Modifier.padding(top = 12.dp))
                    LhButton("Retry", onRetry, Modifier.testTag("chat-retry"), variant = LhButtonVariant.Tonal, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Refresh)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    enabled: Boolean, onSend: (String) -> Boolean, initialDraft: String,
    recording: RecordingLevel? = null, onMic: () -> Unit = {}, onStopMic: () -> Unit = {}, onCancelMic: () -> Unit = {},
) {
    val c = LhTheme.colors
    var text by rememberSaveable { mutableStateOf(initialDraft) }
    val send = { if (onSend(text)) text = "" }
    Row(
        Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recording != null) {
            // Recording: visible state, tap stop to send, X to throw it away.
            LhIconButton(Icons.Outlined.Close, "Cancel recording", onCancelMic, Modifier.testTag("chat-mic-cancel"))
            Row(Modifier.weight(1f).padding(horizontal = 8.dp).testTag("chat-recording"), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp + 6.dp * recording.level).background(c.destructive, LhRadius.fullShape))
                Spacer(Modifier.width(10.dp))
                Text("Recording… " + com.lifehub.app.feature.capture.elapsed(recording.elapsedMs), style = LhType.label, color = c.foreground)
            }
            LhIconButton(Icons.Outlined.Stop, "Stop and send", onStopMic, Modifier.testTag("chat-mic-stop"), variant = LhButtonVariant.Destructive)
            return@Row
        }
        Box(Modifier.weight(1f)) {
            LhTextField(
                text, { text = it }, Modifier.fillMaxWidth().testTag("chat-input"),
                placeholder = "Ask the copilot", singleLine = false, maxLength = 2000,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) send() }),
                enabled = enabled,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (text.isBlank()) {
            LhIconButton(Icons.Outlined.Mic, "Record a voice message", onMic, Modifier.testTag("chat-mic"), variant = LhButtonVariant.Tonal, enabled = enabled)
        } else {
            LhIconButton(
                Icons.AutoMirrored.Outlined.Send, "Send", { send() }, Modifier.testTag("chat-send"),
                variant = LhButtonVariant.Primary, enabled = enabled,
            )
        }
    }
}
