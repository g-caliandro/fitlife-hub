package com.lifehub.app.copilot.stt

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Records one dictation to a file. Started only by an explicit user gesture (mic tap in the chat, or opening the quick
 * capture screen), from a visible activity (owner decision 18: no foreground service).
 */
interface VoiceRecorder {
    /** Starts recording into [file]; false if the microphone could not be opened. */
    fun start(file: File): Boolean
    /** Peak amplitude since the previous call (0..32767), for the level indicator and silence detection. */
    fun maxAmplitude(): Int
    /** Stops and finalises the file; false when nothing usable was recorded (the file is deleted). */
    fun stop(): Boolean
    /** Stops and deletes the file. */
    fun cancel()
    val isRecording: Boolean
}

/**
 * Platform [MediaRecorder]: AAC-LC in MPEG-4 (`.m4a`, MIME audio/mp4), 16 kHz mono, 32 kbps (phase 0 research §6:
 * ~240 KB per minute, accepted as-is by Groq/OpenAI `audio/transcriptions`, available from minSdk 26).
 */
class MediaVoiceRecorder(private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    override val isRecording: Boolean get() = recorder != null

    override fun start(file: File): Boolean {
        if (recorder != null) return false
        file.parentFile?.mkdirs()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r; this.file = file
            true
        } catch (_: Exception) {
            runCatching { r.release() }
            file.delete()
            false
        }
    }

    override fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    override fun stop(): Boolean {
        val r = recorder ?: return false
        val f = file
        recorder = null; file = null
        // stop() throws when no valid audio was captured (e.g. stopped right after start).
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok || f == null || !f.isFile || f.length() == 0L) { f?.delete(); return false }
        return true
    }

    override fun cancel() {
        val r = recorder ?: return
        val f = file
        recorder = null; file = null
        runCatching { r.stop() }; runCatching { r.release() }
        f?.delete()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val BIT_RATE = 32_000
    }
}

/**
 * Decides when a dictation is over: [silenceMs] of continuous level below [threshold] after some speech was heard, or
 * [noSpeechMs] without any speech at all, or [maxMs] in total. Pure; fed every ~200 ms with the recorder's amplitude.
 * The threshold is a first guess (research §6) to calibrate on the phone.
 */
class SilenceDetector(
    private val threshold: Int = 1_500,
    private val silenceMs: Long = 3_000,
    private val noSpeechMs: Long = 8_000,
    private val maxMs: Long = 180_000,
) {
    private var startedAt = -1L
    private var lastLoudAt = -1L
    var heardSpeech = false
        private set

    /** Returns true when the recording should stop now. */
    fun onSample(amplitude: Int, atMs: Long): Boolean {
        if (startedAt < 0) startedAt = atMs
        if (amplitude >= threshold) { heardSpeech = true; lastLoudAt = atMs }
        val elapsed = atMs - startedAt
        if (elapsed >= maxMs) return true
        return if (heardSpeech) atMs - lastLoudAt >= silenceMs else elapsed >= noSpeechMs
    }
}

/** Audio files of captures: `filesDir/captures/<uuid>.m4a` (kept until the owner deletes data; never in the export). */
class CaptureAudioFiles(private val dir: File) {
    fun newFile(): File = File(dir, UUID.randomUUID().toString() + ".m4a").also { dir.mkdirs() }

    companion object {
        fun forContext(context: Context) = CaptureAudioFiles(File(context.applicationContext.filesDir, "captures"))
    }
}
