package com.lifehub.app.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifehub.app.data.model.DayType

/** Minimal 24-grid line icons (stroke 1.75, round caps/joins, no fill) for the plan day types. */
object DayTypeIcons {
    private fun build(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
    }

    /**
     * Pull-up bar: horizontal bar on two ceiling mounts, with a tiny figure at the top of a pull-up
     * (chin over the bar, arms bent down to the chest, torso and legs hanging).
     */
    val Pull: ImageVector by lazy {
        build("DayPull") {
            moveTo(2.5f, 10f); lineTo(21.5f, 10f)
            moveTo(4.5f, 10f); lineTo(4.5f, 3f)
            moveTo(19.5f, 10f); lineTo(19.5f, 3f)
            circle(12f, 6f, 1.8f)
            moveTo(8f, 10f); lineTo(8.5f, 14f); lineTo(12f, 12.5f); lineTo(15.5f, 14f); lineTo(16f, 10f)
            moveTo(12f, 12.5f); lineTo(12f, 17.5f)
            moveTo(10.5f, 21f); lineTo(12f, 17.5f); lineTo(13.5f, 21f)
        }
    }

    /** Push-up: plank body, straight arm, head and floor line. */
    val Push: ImageVector by lazy {
        build("DayPush") {
            moveTo(3f, 20f); lineTo(21f, 20f)
            moveTo(4f, 16.5f); lineTo(16f, 11.5f)
            moveTo(16f, 11.5f); lineTo(16f, 20f)
            circle(19.5f, 9.5f, 1.5f)
        }
    }

    /** Side view of a bent leg (squat): thigh, shin, foot. */
    val Legs: ImageVector by lazy {
        build("DayLegs") {
            circle(9.5f, 4.5f, 1.5f)
            moveTo(8f, 8f); lineTo(5.5f, 13f); lineTo(14.5f, 13f); lineTo(14.5f, 20.5f); lineTo(19f, 20.5f)
        }
    }

    /** Torso outline with abs segments. */
    val Core: ImageVector by lazy {
        build("DayCore") {
            moveTo(9f, 3f); lineTo(15f, 3f)
            curveTo(17f, 3f, 18f, 4.5f, 18f, 6.5f); lineTo(18f, 14f)
            curveTo(18f, 18f, 15.5f, 21f, 12f, 21f)
            curveTo(8.5f, 21f, 6f, 18f, 6f, 14f); lineTo(6f, 6.5f)
            curveTo(6f, 4.5f, 7f, 3f, 9f, 3f)
            moveTo(12f, 3f); lineTo(12f, 21f)
            moveTo(6f, 9f); lineTo(18f, 9f)
            moveTo(6.3f, 14.5f); lineTo(17.7f, 14.5f)
        }
    }

    fun of(t: DayType): ImageVector = when (t) {
        DayType.PULL -> Pull; DayType.PUSH -> Push; DayType.LEGS -> Legs; DayType.CORE -> Core
    }
}

fun DayType.label(): String = when (this) {
    DayType.PULL -> "Pull"; DayType.PUSH -> "Push"; DayType.LEGS -> "Legs"; DayType.CORE -> "Core"
}

fun LhColors.dayType(t: DayType): Color = when (t) {
    DayType.PULL -> pull; DayType.PUSH -> push; DayType.LEGS -> legs; DayType.CORE -> core
}

fun LhColors.dayTypeContainer(t: DayType): Color = dayType(t).copy(alpha = if (isDark) 0.20f else 0.14f)

/** Tiny day-type icon tinted with the type color (or [tint]). */
@Composable
fun DayTypeIcon(type: DayType, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = LhTheme.colors.dayType(type)) {
    Icon(remember(type) { DayTypeIcons.of(type) }, null, modifier.size(size), tint = tint)
}
