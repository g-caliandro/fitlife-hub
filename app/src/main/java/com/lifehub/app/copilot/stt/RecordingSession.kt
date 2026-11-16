package com.lifehub.app.copilot.stt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Live level of a recording in progress. */
data class RecordingLevel(val elapsedMs: Long, val level: Float)

/**
 * One dictation: starts the [recorder] into a new capture file, samples the amplitude every [sampleMs] for the level
 * indicator and calls [onAutoStop] when [detector] says the user stopped talking. Shared by the quick capture screen and
 * the chat mic. The caller then calls [stop] (file to save) or [cancel].
 */
class RecordingSession(
    private val recorder: VoiceRecorder,
    private val files: CaptureAudioFiles,
    private val scope: CoroutineScope,
    private val onAutoStop: () -> Unit,
    private val detector: SilenceDetector = SilenceDetector(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sampleMs: Long = 200,
) {
    private var file: File? = null
    private var job: Job? = null
    private val _level = MutableStateFlow<RecordingLevel?>(null)
    val level: StateFlow<RecordingLevel?> = _level
    val isRecording: Boolean get() = file != null

    fun start(): Boolean {
        if (file != null) return false
        val f = files.newFile()
        if (!recorder.start(f)) return false
        file = f
        val t0 = clock()
        _level.value = RecordingLevel(0, 0f)
        job = scope.launch {
            while (isActive) {
                delay(sampleMs)
                val amp = recorder.maxAmplitude()
                val now = clock()
                _level.value = RecordingLevel(now - t0, (amp / 12_000f).coerceIn(0f, 1f))
                if (detector.onSample(amp, now)) { onAutoStop(); break }
            }
        }
        return true
    }

    /** The finished file, or null when nothing usable was recorded. */
    fun stop(): File? {
        val f = file ?: return null
        file = null; job?.cancel(); _level.value = null
        return if (recorder.stop()) f else null
    }

    fun cancel() {
        file ?: return
        file = null; job?.cancel(); _level.value = null
        recorder.cancel()
    }
}
