package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

@Composable
fun LhChip(text: String, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val bg = if (selected) c.primary else c.card
    val fg = if (selected) c.primaryForeground else c.foreground
    val border = if (selected) null else BorderStroke(1.dp, c.border)
    val body: @Composable () -> Unit = {
        Box(Modifier.height(36.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = LhType.label, maxLines = 1)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = LhRadius.pill, color = bg, contentColor = fg, border = border, tonalElevation = 0.dp, shadowElevation = 0.dp, content = body)
    } else {
        Surface(modifier = modifier, shape = LhRadius.pill, color = bg, contentColor = fg, border = border, tonalElevation = 0.dp, shadowElevation = 0.dp, content = body)
    }
}

/** Pill with leading icon, tnum text and optional trailing edit icon ("76.7 kg ✎"). */
@Composable
fun LhValueChip(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color = LhTheme.colors.primary,
    trailingIcon: ImageVector? = Icons.Outlined.Edit, onClick: (() -> Unit)? = null,
) {
    val c = LhTheme.colors
    val body: @Composable () -> Unit = {
        Row(Modifier.height(40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = iconTint); Spacer(Modifier.width(8.dp)) }
            Text(text, style = LhType.tnum(LhType.label).copy(fontSize = 15.sp), color = c.foreground, maxLines = 1)
            if (trailingIcon != null) { Spacer(Modifier.width(6.dp)); Icon(trailingIcon, null, Modifier.size(16.dp), tint = c.mutedForeground) }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = LhRadius.pill, color = c.card, contentColor = c.foreground, border = BorderStroke(1.dp, c.border), tonalElevation = 0.dp, shadowElevation = 0.dp, content = body)
    } else {
        Surface(modifier = modifier, shape = LhRadius.pill, color = c.card, contentColor = c.foreground, border = BorderStroke(1.dp, c.border), tonalElevation = 0.dp, shadowElevation = 0.dp, content = body)
    }
}

/** "2,432/2,600 kcal" summary pill. */
@Composable
fun LhKcalChip(text: String, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    Surface(modifier = modifier, shape = LhRadius.pill, color = c.muted, contentColor = c.foreground, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = LhType.stat, maxLines = 1)
        }
    }
}
