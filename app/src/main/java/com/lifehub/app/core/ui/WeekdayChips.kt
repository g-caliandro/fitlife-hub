package com.lifehub.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

private val LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** 7 square 36dp chips (ISO 1..7). onToggle null = read-only. */
@Composable
fun WeekdayChips(selected: Set<Int>, onToggle: ((Int) -> Unit)?, modifier: Modifier = Modifier, highlightToday: Int? = null) {
    val c = LhTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (iso in 1..7) {
            val sel = iso in selected
            val bg = if (sel) c.primary else if (onToggle != null) c.background else c.muted
            val fg = if (sel) c.primaryForeground else if (onToggle != null) c.foreground else c.mutedForeground
            val border = when {
                iso == highlightToday && !sel -> BorderStroke(1.5.dp, c.foreground)
                onToggle != null && !sel -> BorderStroke(1.dp, c.input)
                else -> null
            }
            val body: @Composable () -> Unit = {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(LETTERS[iso - 1], style = LhType.label, color = fg)
                }
            }
            if (onToggle != null) {
                Surface(onClick = { onToggle(iso) }, shape = LhRadius.mdShape, color = bg, border = border, tonalElevation = 0.dp, shadowElevation = 0.dp, content = body)
            } else {
                Surface(shape = LhRadius.mdShape, color = bg, border = border, tonalElevation = 0.dp, shadowElevation = 0.dp, content = body)
            }
        }
    }
}
