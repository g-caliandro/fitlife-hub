package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.DayTypeIcons
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.dayType
import com.lifehub.app.core.designsystem.dayTypeContainer
import com.lifehub.app.core.designsystem.tint
import com.lifehub.app.data.model.DayType

/** Tinted rounded square holding an icon (ONT list-item leading). */
@Composable
fun LhIconSquare(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LhTheme.colors.primary,
    container: Color = LhTheme.colors.tint(tint),
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    Box(modifier.size(size).background(container, LhRadius.itemShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun LhIconSquare(
    dayType: DayType,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val c = LhTheme.colors
    LhIconSquare(DayTypeIcons.of(dayType), modifier, c.dayType(dayType), c.dayTypeContainer(dayType), size, iconSize)
}

/** Square with a letter (food initial). */
@Composable
fun LhLetterSquare(letter: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val c = LhTheme.colors
    Box(modifier.size(size).background(c.primaryContainer, LhRadius.itemShape), contentAlignment = Alignment.Center) {
        Text(letter.take(1).uppercase(), style = LhType.heading, color = c.onPrimaryContainer)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LhListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = LhTheme.colors.primary,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    standalone: Boolean = true,
    showDivider: Boolean = false,
) {
    val c = LhTheme.colors
    val shape = LhRadius.cardShape
    val src = remember { MutableInteractionSource() }
    var m = modifier.fillMaxWidth()
    if (standalone) {
        m = m.then(if (onClick != null) Modifier.pressScale(src) else Modifier)
            .clip(shape).background(c.card, shape).border(BorderStroke(1.dp, c.border), shape)
    }
    Column(m) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp)
                .then(
                    if (onClick != null || onLongClick != null)
                        Modifier.combinedClickable(interactionSource = src, indication = androidx.compose.material3.ripple(), role = Role.Button, onLongClick = onLongClick, onClick = { onClick?.invoke() })
                    else Modifier,
                )
                .padding(horizontal = if (standalone) 12.dp else 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(16.dp)) }
            else if (icon != null) { LhIconSquare(icon, tint = iconTint); Spacer(Modifier.width(16.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = LhType.bodyLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = LhType.body, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
            else if (trailingText != null) {
                Spacer(Modifier.width(12.dp))
                Text(trailingText, style = LhType.tnum(LhType.label), color = c.foreground, maxLines = 1)
            }
        }
        if (!standalone && showDivider) HorizontalDivider(color = c.border, thickness = 1.dp)
    }
}
