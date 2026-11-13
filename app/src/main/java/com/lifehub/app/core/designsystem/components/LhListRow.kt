package com.lifehub.app.core.designsystem.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme

/** Row inside an [LhGroupCard]; leading icon is shown in a 40dp tinted square. */
@Composable
fun LhListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    LhListItem(
        title = title, modifier = modifier, subtitle = subtitle,
        leading = leadingIcon?.let { ic -> { LhIconSquare(ic, size = 40.dp, iconSize = 22.dp) } },
        trailing = trailing, onClick = onClick, standalone = false, showDivider = showDivider,
    )
}

@Composable
fun LhSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val c = LhTheme.colors
    LhListRow(
        title = title, subtitle = subtitle, modifier = modifier, showDivider = showDivider,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(
                checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.primary, checkedThumbColor = c.primaryForeground, checkedBorderColor = c.primary,
                    uncheckedTrackColor = c.muted, uncheckedThumbColor = c.mutedForeground, uncheckedBorderColor = c.input,
                ),
            )
        },
    )
}
