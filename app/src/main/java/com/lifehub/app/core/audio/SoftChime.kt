package com.lifehub.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Soft, round "rest is over" tone: two sine partials (a fifth apart) with a smooth 20 ms attack and an
 * exponential decay, played quietly through the sonification stream.
 */
object SoftChime {
    const val SAMPLE_RATE = 22_050
    const val DURATION_MS = 450
    const val FREQ_1 = 392.0
    const val FREQ_2 = 588.0
    const val ATTACK_MS = 20
    const val GAIN = 0.25

    /** Pure PCM-16 mono synthesis (unit tested). */
    fun samples(
        sampleRate: Int = SAMPLE_RATE, durationMs: Int = DURATION_MS, f1: Double = FREQ_1, f2: Double = FREQ_2,
        attackMs: Int = ATTACK_MS, gain: Double = GAIN,
    ): ShortArray {
        val n = sampleRate * durationMs / 1000
        val attack = (sampleRate * attackMs / 1000).coerceAtLeast(1)
        val decayPerSec = 7.0 // exp(-7 * 0.45) ~ 0.04 at the end, then a short linear fade to exactly 0
        val fade = (sampleRate * 0.01).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val env = (if (i < attack) 0.5 * (1 - kotlin.math.cos(PI * i / attack)) else 1.0) * exp(-decayPerSec * t)
            val tail = if (i >= n - fade) (n - 1 - i).toDouble() / fade else 1.0
            val wave = (sin(2 * PI * f1 * t) + 0.6 * sin(2 * PI * f2 * t)) / 1.6
            (wave * env * tail * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** Plays the chime once on a background thread; the track is released after playback. Never throws. */
    fun play() {
        Thread {
            var track: AudioTrack? = null
            try {
                val pcm = samples()
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track = t
                t.write(pcm, 0, pcm.size)
                t.play()
                Thread.sleep(DURATION_MS + 80L)
            } catch (_: Throwable) {
            } finally {
                try { track?.release() } catch (_: Throwable) {}
            }
        }.apply { isDaemon = true }.start()
    }
}
