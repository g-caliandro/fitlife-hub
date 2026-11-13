package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

enum class DeltaGood { Up, Down, Neutral }   // which direction is "good"

@Composable
fun LhStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    delta: Double? = null,
    deltaText: String? = null,
    good: DeltaGood = DeltaGood.Up,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    deltaDecimals: Int = 1,
) {
    val c = LhTheme.colors
    LhCard(modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Text(label, style = LhType.captionMuted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = LhType.stat, color = c.foreground, maxLines = 1, modifier = Modifier.alignByBaseline())
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = LhType.body, color = c.mutedForeground, modifier = Modifier.alignByBaseline())
            }
        }
        // No delta (e.g. no previous period) -> no delta row at all (never a lone "—").
        if (delta != null) LhDeltaRow(delta, deltaText, good, deltaDecimals, Modifier.fillMaxWidth())
        if (caption != null) Text(caption, style = LhType.captionMuted, maxLines = 1)
    }
}

/** "↑ +12 g vs prev" in success / destructive / muted per [good]; colour and arrow use the displayed (rounded) value. */
@Composable
fun LhDeltaRow(
    delta: Double,
    deltaText: String? = null,
    good: DeltaGood = DeltaGood.Up,
    deltaDecimals: Int = 1,
    modifier: Modifier = Modifier,
) {
    val c = LhTheme.colors
    // Decide arrow/colour on the displayed (rounded) value so "+0.0" is never coloured.
    val scale = Math.pow(10.0, deltaDecimals.toDouble())
    val shown = Math.round(delta * scale) / scale
    val color = when {
        shown == 0.0 || good == DeltaGood.Neutral -> c.mutedForeground
        (shown > 0 && good == DeltaGood.Up) || (shown < 0 && good == DeltaGood.Down) -> c.success
        else -> c.destructive
    }
    val icon: ImageVector = when {
        shown == 0.0 -> Icons.Outlined.Remove
        shown > 0 -> Icons.Outlined.ArrowUpward
        else -> Icons.Outlined.ArrowDownward
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(2.dp))
        Text((deltaText ?: com.lifehub.app.core.designsystem.fmtDelta(delta)) + " vs prev", style = LhType.labelSmall, color = color, maxLines = 1)
    }
}
