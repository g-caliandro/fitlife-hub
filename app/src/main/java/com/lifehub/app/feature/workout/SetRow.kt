package com.lifehub.app.feature.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhSize
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.fmtTarget
import com.lifehub.app.core.designsystem.fmtWeight
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType

/** One set line: SET | TARGET | KG | DONE value | status. Current = accentMuted + 3dp accent bar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetRow(
    set: SessionSet, current: Boolean, onClick: () -> Unit, onToggleSkip: () -> Unit, onRemove: () -> Unit,
    modifier: Modifier = Modifier, previous: String = "\u2014", onQuickDone: () -> Unit = {},
) {
    SwipeToRemove(onRemove, modifier) { SetRowContent(set, current, previous, onClick, onToggleSkip, onRemove, onQuickDone) }
}

/** Swipe left to remove (item 18). Always snaps back; the row disappears when the data changes (undo restores it). */
@Composable
fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LhTheme.colors
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onRemove(); false } else false })
    SwipeToDismissBox(
        state = state, modifier = modifier, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(c.destructive).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Outlined.Delete, "Remove", tint = c.destructiveForeground)
            }
        },
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetRowContent(
    set: SessionSet, current: Boolean, previous: String, onClick: () -> Unit, onToggleSkip: () -> Unit, onRemove: () -> Unit,
    onQuickDone: () -> Unit,
) {
    val c = LhTheme.colors
    var menu by remember { mutableStateOf(false) }
    val skipped = set.status == SetStatus.SKIPPED
    val bg = when {
        current -> c.primaryContainer
        set.status == SetStatus.DONE -> c.successMuted
        else -> c.card
    }
    val textColor = if (skipped) c.mutedForeground else c.foreground
    Box {
        Row(
            Modifier.fillMaxWidth().height(LhSize.setRow).background(bg)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(if (current) c.primary else bg))
            Text("${set.setIndex + 1}", style = LhType.tnum(LhType.label), color = textColor, modifier = Modifier.width(41.dp).padding(start = 13.dp))
            Text(previous, style = LhType.tnum(LhType.body), color = c.mutedForeground, maxLines = 1, modifier = Modifier.width(76.dp).testTag("set-prev"))
            Text(
                fmtTarget(set.targetType, set.targetValue), style = LhType.tnum(LhType.body), color = textColor,
                textDecoration = if (skipped) TextDecoration.LineThrough else null, modifier = Modifier.width(52.dp),
            )
            Text(set.weightKg?.takeIf { it > 0 }?.let { fmtWeight(it) } ?: "–", style = LhType.tnum(LhType.body), color = c.mutedForeground, modifier = Modifier.width(40.dp))
            val actual = when {
                set.status == SetStatus.DONE ->
                    (if (set.targetType == TargetType.SECONDS) set.actualSeconds?.let { "$it s" } else set.actualReps?.toString()) ?: "–"
                skipped -> "skipped"
                set.targetType == TargetType.MAX -> "·"
                else -> WorkoutOps.prefill(set).value.takeIf { it.isNotEmpty() }
                    ?.let { if (set.targetType == TargetType.SECONDS) "$it s" else it } ?: "·"
            }
            val muted = skipped || set.status == SetStatus.PENDING
            Text(actual, style = LhType.tnum(LhType.body), color = if (muted) c.mutedForeground else c.foreground, modifier = Modifier.weight(1f))
            val isDone = set.status == SetStatus.DONE
            Box(
                Modifier.size(48.dp).clickable(
                    role = Role.Button, onClickLabel = if (isDone) "Edit set" else "Complete set",
                    onClick = if (isDone || set.targetType == TargetType.MAX) onClick else onQuickDone,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(32.dp).background(if (isDone) c.success else c.muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check, if (isDone) "Done" else "Complete set",
                        tint = if (isDone) c.background else c.mutedForeground, modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        DropdownMenu(menu, { menu = false }, containerColor = c.card) {
            DropdownMenuItem(text = { Text(if (skipped) "Unskip" else "Skip") }, onClick = { menu = false; onToggleSkip() })
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onClick() })
            DropdownMenuItem(text = { Text("Remove set") }, onClick = { menu = false; onRemove() })
        }
    }
}

@Composable
fun SetColumnHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SET", style = LhType.labelSmall, color = LhTheme.colors.mutedForeground, modifier = Modifier.width(41.dp).padding(start = 13.dp))
        Text("PREV", style = LhType.labelSmall, color = LhTheme.colors.mutedForeground, modifier = Modifier.width(76.dp))
        Text("TARGET", style = LhType.labelSmall, color = LhTheme.colors.mutedForeground, modifier = Modifier.width(52.dp))
        Text("KG", style = LhType.labelSmall, color = LhTheme.colors.mutedForeground, modifier = Modifier.width(40.dp))
        Text("DONE", style = LhType.labelSmall, color = LhTheme.colors.mutedForeground, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        Box(Modifier.width(48.dp))
    }
}
