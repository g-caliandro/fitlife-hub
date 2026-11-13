package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

/** Big icon (in a muted circle) + title + optional body + optional tonal action button. Old param names kept. */
@Composable
fun LhEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
) {
    val c = LhTheme.colors
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(Modifier.size(72.dp).background(c.primaryContainer, LhRadius.fullShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(32.dp), tint = c.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = LhType.heading, color = c.foreground, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(description, style = LhType.body, color = c.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            LhButton(actionText, onAction, variant = LhButtonVariant.Tonal, leadingIcon = actionIcon)
        }
    }
}
