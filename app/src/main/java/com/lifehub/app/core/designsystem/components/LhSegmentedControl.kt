package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

/** Pill segmented control. Wraps its content by default; pass Modifier.fillMaxWidth() for equal full-width segments. */
@Composable
fun <T> LhSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
) {
    val c = LhTheme.colors
    Row(
        modifier.width(IntrinsicSize.Max).height(40.dp).clip(LhRadius.pill).background(c.card).border(1.dp, c.border, LhRadius.pill),
    ) {
        options.forEachIndexed { i, opt ->
            val sel = opt == selected
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
            Box(
                Modifier.weight(1f, fill = true).defaultMinSize(minWidth = 56.dp).fillMaxHeight()
                    .then(if (sel) Modifier.background(c.muted) else Modifier)
                    .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(opt) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(opt), style = LhType.label.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal), color = c.foreground, maxLines = 1)
            }
        }
    }
}
