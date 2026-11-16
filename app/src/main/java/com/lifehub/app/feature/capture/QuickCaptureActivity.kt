package com.lifehub.app.feature.capture

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.lifehub.app.container
import com.lifehub.app.copilot.stt.RecordingSession
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.feature.copilot.hasMicPermission
import com.lifehub.app.notifications.Notifier
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * "Talk to copilot" (design doc §11). Started by the static shortcut, the dedicated launcher entry or the Samsung side key.
 * Records from this visible activity (owner decision 18: no foreground service), also over the lock screen
 * (`setShowWhenLocked`: shown on top of the keyguard without unlocking; `setTurnScreenOn`: screen on when it resumes).
 * Opening the screen is the user's gesture that starts the recording; a recreated screen does NOT start recording by
 * itself (it shows a Start button). Done, a long pause or leaving the screen (e.g. locking the phone) stops and SAVES the
 * recording: the audio is on disk and the capture is RECORDED and queued before the screen closes. It never opens the chat.
 */
class QuickCaptureActivity : ComponentActivity() {
    private val state = MutableStateFlow<QuickCaptureState>(QuickCaptureState.Idle)
    private var session: RecordingSession? = null
    private var saving = false

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasMicPermission(this)) startRecording() else state.value = QuickCaptureState.PermissionDenied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setContent {
            LhTheme {
                val s by state.collectAsState()
                QuickCaptureScreen(s, onDone = ::done, onCancel = ::cancel, onStart = ::startRecording, onOpenSettings = ::openAppSettings)
            }
        }
        if (savedInstanceState == null) begin()
    }

    private fun begin() {
        if (hasMicPermission(this)) { startRecording(); return }
        // First launch: ask the microphone (and, on Android 13+, the notification permission for the result) together.
        val settings = container.copilotSettings
        val ask = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(this@QuickCaptureActivity) &&
                !settings.permissionAsked(Manifest.permission.POST_NOTIFICATIONS)) {
                settings.markPermissionAsked(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        state.value = QuickCaptureState.AskingPermission
        permissions.launch(ask.toTypedArray())
    }

    private fun startRecording() {
        if (session?.isRecording == true || saving) return
        if (!hasMicPermission(this)) { begin(); return }
        val c = container
        val s = RecordingSession(c.newVoiceRecorder(), c.captureAudio, lifecycleScope, onAutoStop = { done() })
        if (!s.start()) { state.value = QuickCaptureState.Failed("The microphone is not available right now."); return }
        session = s
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            s.level.collectLatest { l -> if (l != null) state.value = QuickCaptureState.Recording(l.elapsedMs, l.level) }
        }
    }

    /** Done / long pause: save, show "Saved" briefly, close. */
    private fun done() {
        val s = session ?: return
        session = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val file = s.stop()
        if (file == null) { state.value = QuickCaptureState.Failed("Nothing was recorded. Try again."); return }
        saving = true
        state.value = QuickCaptureState.Saving
        // Saved in the app scope: completes even if this screen is destroyed meanwhile.
        val saved = container.appScope.async { container.voiceCaptures.saveQuick(file) }
        lifecycleScope.launch {
            val ok = runCatching { saved.await() }.isSuccess
            saving = false
            if (ok) { state.value = QuickCaptureState.Saved; delay(700); finish() }
            else state.value = QuickCaptureState.Failed("Couldn't save the recording.")
        }
    }

    private fun cancel() {
        session?.cancel(); session = null
        finish()
    }

    /** Leaving the screen mid-recording (lock, home, another app): Android would silence the mic, so save what we have. */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        val s = session ?: return
        session = null
        val file = s.stop() ?: run { finish(); return }
        val c = container
        c.appScope.launch { runCatching { c.voiceCaptures.saveQuick(file) } }
        finish()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        finish()
    }
}
