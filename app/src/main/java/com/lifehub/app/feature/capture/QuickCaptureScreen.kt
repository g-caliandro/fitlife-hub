package com.lifehub.app.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhIconButton

/** States of the quick capture screen. */
sealed interface QuickCaptureState {
    /** Waiting for the microphone permission dialog. */
    data object AskingPermission : QuickCaptureState
    /** Microphone denied: nothing is recorded. */
    data object PermissionDenied : QuickCaptureState
    /** Not recording (e.g. the screen was recreated): recording starts only with the Start button. */
    data object Idle : QuickCaptureState
    /** [level] 0..1 from the microphone amplitude. */
    data class Recording(val elapsedMs: Long, val level: Float) : QuickCaptureState
    data object Saving : QuickCaptureState
    data object Saved : QuickCaptureState
    data class Failed(val message: String) : QuickCaptureState
}

/**
 * Minimal "Talk to copilot" screen (design doc §11): recording indicator + Done. It never opens the chat; it closes
 * itself after saving and the result arrives as a notification.
 */
@Composable
fun QuickCaptureScreen(
    state: QuickCaptureState,
    onDone: () -> Unit = {},
    onCancel: () -> Unit = {},
    onStart: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val c = LhTheme.colors
    Box(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().testTag("quick-capture")) {
        LhIconButton(Icons.Outlined.Close, "Cancel", onCancel, Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("capture-cancel"))
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("Talk to copilot", style = LhType.title, color = c.foreground)
            Spacer(Modifier.height(32.dp))
            when (state) {
                is QuickCaptureState.Recording -> {
                    MicDot(active = true, level = state.level)
                    Spacer(Modifier.height(20.dp))
                    Text("Listening… " + elapsed(state.elapsedMs), style = LhType.heading, color = c.foreground, modifier = Modifier.testTag("capture-recording"))
                    Spacer(Modifier.height(4.dp))
                    Text("Say what you ate, e.g. “90 g di riso e 180 g di pollo”. Tap Done when you finish; a pause also ends it.",
                        style = LhType.captionMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    LhButton("Done", onDone, Modifier.fillMaxWidth().testTag("capture-done"), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.Check)
                }
                QuickCaptureState.Saving, QuickCaptureState.AskingPermission -> {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = c.mutedForeground)
                    Spacer(Modifier.height(16.dp))
                    Text(if (state == QuickCaptureState.Saving) "Saving…" else "Waiting for microphone permission…", style = LhType.body, color = c.mutedForeground)
                }
                QuickCaptureState.Saved -> {
                    MicDot(active = false, level = 0f, icon = Icons.Outlined.Check)
                    Spacer(Modifier.height(16.dp))
                    Text("Saved. You'll get a notification.", style = LhType.heading, color = c.foreground, modifier = Modifier.testTag("capture-saved"))
                }
                QuickCaptureState.Idle -> {
                    MicDot(active = false, level = 0f)
                    Spacer(Modifier.height(24.dp))
                    LhButton("Start recording", onStart, Modifier.fillMaxWidth().testTag("capture-start"), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.Mic)
                }
                QuickCaptureState.PermissionDenied -> {
                    MicDot(active = false, level = 0f, icon = Icons.Outlined.MicOff)
                    Spacer(Modifier.height(16.dp))
                    Text("Microphone access is needed to talk to the copilot.", style = LhType.body, color = c.foreground, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    LhButton("Open app settings", onOpenSettings, Modifier.fillMaxWidth(), variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.Settings)
                }
                is QuickCaptureState.Failed -> {
                    MicDot(active = false, level = 0f, icon = Icons.Outlined.MicOff)
                    Spacer(Modifier.height(16.dp))
                    Text(state.message, style = LhType.body, color = c.foreground, textAlign = TextAlign.Center, modifier = Modifier.testTag("capture-failed"))
                    Spacer(Modifier.height(24.dp))
                    LhButton("Try again", onStart, Modifier.fillMaxWidth(), variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.Mic)
                }
            }
        }
    }
}

@Composable
private fun MicDot(active: Boolean, level: Float, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Mic) {
    val c = LhTheme.colors
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        if (active) Box(Modifier.size(132.dp).scale(0.72f + 0.28f * level.coerceIn(0f, 1f)).background(c.destructive.copy(alpha = 0.18f), LhRadius.fullShape))
        Box(Modifier.size(88.dp).background(if (active) c.destructive else c.muted, LhRadius.fullShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = if (active) c.destructiveForeground else c.mutedForeground)
        }
    }
}

internal fun elapsed(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
