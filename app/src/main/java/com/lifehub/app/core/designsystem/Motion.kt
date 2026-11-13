package com.lifehub.app.core.designsystem

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object LhMotion {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = FastOutSlowInEasing
    const val short = 150
    const val medium = 300
    const val long = 500

    /** True when the system animator scale is 0 (set by LhTheme). All LhMotion-aware animation snaps. */
    @Volatile var reduced: Boolean = false
}

/** tween that snaps when reduced motion is on. */
fun <T> lhTween(durationMillis: Int = LhMotion.medium, delayMillis: Int = 0, easing: androidx.compose.animation.core.Easing = LhMotion.emphasized): FiniteAnimationSpec<T> =
    if (LhMotion.reduced) snap() else tween(durationMillis, delayMillis, easing)

fun focusStepTransition(forward: Boolean): ContentTransform {
    val s = if (forward) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInHorizontally(tween(300, easing = LhMotion.emphasized)) { s * it / 4 } + fadeIn(tween(220, delayMillis = 80)),
        initialContentExit = slideOutHorizontally(tween(300, easing = LhMotion.emphasized)) { -s * it / 4 } + fadeOut(tween(150)),
        sizeTransform = SizeTransform(clip = false),
    )
}
