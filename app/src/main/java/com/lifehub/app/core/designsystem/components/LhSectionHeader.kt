package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

/**
 * Standard section header. Callers provide the 16dp horizontal page padding (put it inside a padded column)
 * and, via the built-in top 24dp, the spacing between sections (in a LazyColumn with 12dp item spacing pass
 * topPadding = 12.dp so the visual gap stays 24dp). [action] is usually [LhSeeAllChip].
 */
@Composable
fun LhSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 24.dp,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = topPadding, bottom = 4.dp).defaultMinSize(minHeight = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                androidx.compose.material3.Icon(icon, null, Modifier.size(24.dp), tint = LhTheme.colors.foreground)
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            }
            Text(title, style = LhType.section, color = LhTheme.colors.foreground)
        }
        action?.invoke()
    }
}

/** Legacy overload: text action is rendered as a [LhSeeAllChip]-style chip. */
@Composable
fun LhSectionHeader(title: String, modifier: Modifier = Modifier, actionText: String?, onAction: (() -> Unit)?) {
    LhSectionHeader(title, modifier, action = if (actionText != null && onAction != null) ({ LhSeeAllChip(onAction, label = actionText) }) else null)
}
