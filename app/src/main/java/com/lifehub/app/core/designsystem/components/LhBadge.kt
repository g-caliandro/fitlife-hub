package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

enum class LhBadgeVariant { Default, Secondary, Outline, Accent, Success, Warning, Destructive }

@Composable
fun LhBadge(text: String, modifier: Modifier = Modifier, variant: LhBadgeVariant = LhBadgeVariant.Secondary) {
    val c = LhTheme.colors
    val (bg, fg, border) = when (variant) {
        LhBadgeVariant.Default -> Triple(c.primary, c.primaryForeground, null)
        LhBadgeVariant.Secondary -> Triple(c.secondary, c.secondaryForeground, null)
        LhBadgeVariant.Outline -> Triple(Color.Transparent, c.foreground, BorderStroke(1.dp, c.border))
        LhBadgeVariant.Accent -> Triple(c.primaryContainer, c.onPrimaryContainer, null)
        LhBadgeVariant.Success -> Triple(c.successMuted, c.success, null)
        LhBadgeVariant.Warning -> Triple(c.muted, c.warning, null)
        LhBadgeVariant.Destructive -> Triple(c.destructive, c.destructiveForeground, null)
    }
    Surface(modifier, shape = LhRadius.fullShape, color = bg, contentColor = fg, border = border, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Box(Modifier.height(22.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = LhType.labelSmall, maxLines = 1)
        }
    }
}
