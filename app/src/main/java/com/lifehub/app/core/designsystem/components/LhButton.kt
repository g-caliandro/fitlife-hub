package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhSize
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

enum class LhButtonVariant { Primary, Tonal, Secondary, Outline, Ghost, Destructive }
enum class LhButtonSize { Sm, Md, Lg }

private data class BtnColors(val bg: Color, val fg: Color, val border: BorderStroke?)

@Composable
private fun btnColors(variant: LhButtonVariant, pressed: Boolean): BtnColors {
    val c = LhTheme.colors
    fun p(bg: Color, fg: Color) = if (pressed) lerp(bg, c.foreground, 0.10f) else bg
    return when (variant) {
        LhButtonVariant.Primary -> BtnColors(p(c.primary, c.primaryForeground), c.primaryForeground, null)
        LhButtonVariant.Tonal -> BtnColors(p(c.primaryContainer, c.onPrimaryContainer), c.onPrimaryContainer, null)
        LhButtonVariant.Secondary -> BtnColors(p(c.muted, c.foreground), c.foreground, null)
        LhButtonVariant.Outline -> BtnColors(if (pressed) c.muted else Color.Transparent, c.foreground, BorderStroke(1.dp, c.input))
        LhButtonVariant.Ghost -> BtnColors(if (pressed) c.muted else Color.Transparent, c.foreground, null)
        LhButtonVariant.Destructive -> BtnColors(p(c.destructive, c.destructiveForeground), c.destructiveForeground, null)
    }
}

@Composable
fun LhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: LhButtonVariant = LhButtonVariant.Primary,
    size: LhButtonSize = LhButtonSize.Md,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val col = btnColors(variant, pressed)
    val h = when (size) { LhButtonSize.Sm -> LhSize.buttonSm; LhButtonSize.Md -> LhSize.buttonMd; LhButtonSize.Lg -> LhSize.buttonLg }
    val hPad = if (size == LhButtonSize.Sm) 14.dp else 20.dp
    val fs = if (size == LhButtonSize.Sm) 14.sp else 15.sp
    Surface(
        onClick = onClick, enabled = enabled, interactionSource = src,
        modifier = modifier.minimumInteractiveComponentSize().height(h).pressScale(src, 0.97f).alpha(if (enabled) 1f else 0.4f),
        shape = LhRadius.pill, color = col.bg, contentColor = col.fg, border = col.border,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = h).padding(horizontal = hPad),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = LhType.label.copy(fontWeight = FontWeight.SemiBold, fontSize = fs), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LhIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: LhButtonVariant = LhButtonVariant.Ghost,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val col = btnColors(variant, pressed)
    Surface(
        onClick = onClick, enabled = enabled, interactionSource = src,
        modifier = modifier.minimumInteractiveComponentSize().size(48.dp).pressScale(src, 0.97f).alpha(if (enabled) 1f else 0.4f),
        shape = LhRadius.pill, color = col.bg, contentColor = col.fg, border = col.border,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Row(Modifier.size(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription, Modifier.size(24.dp))
        }
    }
}
