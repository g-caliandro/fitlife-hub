package com.lifehub.app.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhMotion
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme

/** Press-scale feedback (spring) driven by [src]. */
@Composable
internal fun Modifier.pressScale(src: MutableInteractionSource, pressed: Float = 0.98f, stiffness: Float = 800f): Modifier {
    val isPressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed && !LhMotion.reduced) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness), label = "press",
    )
    return this.scale(s)
}

@Composable
fun LhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    containerColor: Color = LhTheme.colors.card,
    border: Boolean = true,
    shape: Shape = LhRadius.cardShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LhTheme.colors
    val stroke = if (border) BorderStroke(1.dp, c.border) else null
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        val src = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick, interactionSource = src, modifier = modifier.pressScale(src), shape = shape,
            color = containerColor, contentColor = c.foreground,
            border = stroke, tonalElevation = 0.dp, shadowElevation = 0.dp, content = inner,
        )
    } else {
        Surface(
            modifier = modifier, shape = shape, color = containerColor, contentColor = c.foreground,
            border = stroke, tonalElevation = 0.dp, shadowElevation = 0.dp, content = inner,
        )
    }
}

/** Card with zero padding holding grouped LhListItem(standalone = false) rows. */
@Composable
fun LhGroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    LhCard(modifier, contentPadding = PaddingValues(0.dp), content = content)
}
