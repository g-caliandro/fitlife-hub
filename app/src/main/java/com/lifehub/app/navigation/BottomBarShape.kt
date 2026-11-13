package com.lifehub.app.navigation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Bottom-bar outline with a circular cradle cut into its top edge, at its horizontal center, for a FAB whose center
 * sits on that edge. The cradle has radius [cradleRadius] (FAB radius + gap) and meets the
 * top edge through two [shoulderRadius] fillets, each tangent to both the top edge and the cradle, so the outline
 * is G1-continuous (no corners).
 */
class CradleBarShape(
    private val cradleRadius: Dp,
    private val shoulderRadius: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = topEdgePath(size, layoutDirection, density)
        p.lineTo(size.width, size.height)
        p.lineTo(0f, size.height)
        p.close()
        return Outline.Generic(p)
    }

    /** Open path of just the top outline (left edge -> shoulder -> cradle -> shoulder -> right edge), for a hairline. */
    fun topEdgePath(size: Size, layoutDirection: LayoutDirection, density: Density): Path = with(density) {
        val r = cradleRadius.toPx()
        val s = shoulderRadius.toPx()
        val cx = size.width / 2
        // Fillet centers sit at (cx ± dx, s): tangent to y=0 from below and externally tangent to the cradle.
        val dx = sqrt((r + s) * (r + s) - s * s)
        val theta = Math.toDegrees(atan2(s, dx).toDouble()).toFloat()
        Path().apply {
            moveTo(0f, 0f)
            lineTo(cx - dx, 0f)
            arcTo(Rect(cx - dx - s, 0f, cx - dx + s, 2 * s), -90f, 90f - theta, false)
            arcTo(Rect(cx - r, -r, cx + r, r), 180f - theta, -(180f - 2 * theta), false)
            arcTo(Rect(cx + dx - s, 0f, cx + dx + s, 2 * s), 180f + theta, 90f - theta, false)
            lineTo(size.width, 0f)
        }
    }
}
