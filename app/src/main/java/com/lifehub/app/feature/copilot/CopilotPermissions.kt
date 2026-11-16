package com.lifehub.app.feature.copilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.lifehub.app.container
import com.lifehub.app.notifications.Notifier

fun hasMicPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Returns a function that asks POST_NOTIFICATIONS (Android 13+) when missing; a no-op below 13 or when granted. */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(ctx)) {
            ctx.container.copilotSettings.markPermissionAsked(Manifest.permission.POST_NOTIFICATIONS)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Asks the notification permission once, on the first use of the copilot (research §7.4: in context, not at app start;
 * never insist after a refusal). Results stay visible in the inbox when it is denied.
 */
@Composable
fun AskNotificationPermissionOnFirstUse() {
    val ctx = LocalContext.current
    val ask = rememberNotificationPermissionRequest()
    LaunchedEffect(Unit) {
        val s = ctx.container.copilotSettings
        if (s.current().notificationsEnabled && !s.permissionAsked(Manifest.permission.POST_NOTIFICATIONS)) ask()
    }
}
