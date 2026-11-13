package com.lifehub.app.core.ui.charts

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MonotoneInterpolationTest {
    /** Samples the cubic Hermite segments exactly as [monotonePath] builds them (Bezier controls at dx/3). */
    private fun assertNoOvershoot(pts: List<Offset>) {
        val m = monotoneTangents(pts)
        for (i in 0 until pts.size - 1) {
            val p0 = pts[i]; val p3 = pts[i + 1]
            val dx = (p3.x - p0.x) / 3f
            val y1 = p0.y + m[i] * dx; val y2 = p3.y - m[i + 1] * dx
            val lo = minOf(p0.y, p3.y); val hi = maxOf(p0.y, p3.y)
            for (k in 0..100) {
                val t = k / 100f; val u = 1 - t
                val y = u * u * u * p0.y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * p3.y
                assertTrue("segment $i overshoots at t=$t: $y !in [$lo, $hi]", y >= lo - 1e-3f && y <= hi + 1e-3f)
            }
        }
    }

    @Test fun sharpSpikeAndPlateau() = assertNoOvershoot(
        listOf(Offset(0f, 10f), Offset(10f, 10f), Offset(20f, 100f), Offset(25f, 100f), Offset(60f, 0f), Offset(61f, 50f), Offset(90f, 49f)),
    )

    @Test fun unevenlySpacedRandomData() {
        val r = Random(7)
        repeat(200) {
            var x = 0f
            val pts = List(2 + r.nextInt(12)) { x += 1f + r.nextFloat() * 40f; Offset(x, r.nextFloat() * 200f) }
            assertNoOvershoot(pts)
        }
    }
}
