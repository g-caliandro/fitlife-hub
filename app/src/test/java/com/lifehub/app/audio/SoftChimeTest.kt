package com.lifehub.app.audio

import com.lifehub.app.core.audio.SoftChime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoftChimeTest {
    private val pcm = SoftChime.samples()

    @Test fun lengthMatchesDuration() = assertEquals(SoftChime.SAMPLE_RATE * SoftChime.DURATION_MS / 1000, pcm.size)

    @Test fun quietAndNeverClips() {
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= (SoftChime.GAIN * Short.MAX_VALUE).toInt() + 1)
        assertTrue(peak > 3000)
    }

    @Test fun smoothAttackAndEndsAtZero() {
        assertEquals(0, pcm[0].toInt())
        assertEquals(0, pcm.last().toInt())
        // within the 20 ms attack the envelope rises: late-attack energy > first 2 ms energy
        val sr = SoftChime.SAMPLE_RATE
        fun energy(from: Int, to: Int) = (from until to).sumOf { (pcm[it].toDouble()) * pcm[it] }
        assertTrue(energy(sr * 15 / 1000, sr * 20 / 1000) > 20 * energy(0, sr * 2 / 1000))
    }

    @Test fun decaysExponentially() {
        val sr = SoftChime.SAMPLE_RATE
        fun peak(fromMs: Int, toMs: Int) = (sr * fromMs / 1000 until sr * toMs / 1000).maxOf { abs(pcm[it].toInt()) }
        assertTrue(peak(25, 75) > peak(150, 200))
        assertTrue(peak(150, 200) > peak(380, 430))
    }

    @Test fun containsBothPartials() {
        fun goertzel(f: Double): Double {
            val w = 2 * Math.PI * f / SoftChime.SAMPLE_RATE
            val coeff = 2 * Math.cos(w)
            var q1 = 0.0; var q2 = 0.0
            for (x in pcm) { val q0 = coeff * q1 - q2 + x; q2 = q1; q1 = q0 }
            return q1 * q1 + q2 * q2 - coeff * q1 * q2
        }
        val a = goertzel(392.0); val b = goertzel(588.0); val off = goertzel(1200.0)
        assertTrue(a > 20 * off); assertTrue(b > 20 * off)
    }
}
