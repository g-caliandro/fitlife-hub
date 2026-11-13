package com.lifehub.app.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.designsystem.fmtTarget
import com.lifehub.app.core.ui.ExercisePickerSheet
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus


/** Full list of the session for editing: add set/exercise, jump to a pending set, swipe to remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveOverviewSheet(ui: LiveUi, currentIndex: Int?, a: LiveActions, onDismiss: () -> Unit) {
    val c = LhTheme.colors
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var newExercise by remember { mutableStateOf<Exercise?>(null) }
    // Exercise cards are collapsible; the current exercise starts expanded (spec §3.9 overview).
    val currentOrder = currentIndex?.let { ui.sets.getOrNull(it)?.exerciseOrder }
    var expanded by remember { mutableStateOf(setOfNotNull(currentOrder ?: ui.sets.firstOrNull()?.exerciseOrder)) }
    val rows = remember(ui.sets, expanded) { buildRows(ui.sets, expanded) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = rows.indexOfFirst { it is Row2.Set && it.index == currentIndex }
        if (i >= 0) listState.scrollToItem((i - 2).coerceAtLeast(0))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card, contentColor = c.foreground, tonalElevation = 0.dp,
        shape = LhRadius.sheetShape, scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(vertical = 8.dp).size(width = 32.dp, height = 4.dp).background(c.border, LhRadius.fullShape)) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Workout", style = LhType.title, color = c.foreground, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp).testTag("live-list"), state = listState) {
                items(rows.size, key = { i ->
                    when (val r = rows[i]) {
                        is Row2.Header -> "h${r.order}"; is Row2.Set -> r.set.id; is Row2.AddSet -> "a${r.order}"
                        Row2.AddExercise -> "addex"; Row2.Spacer -> "spacer"
                    }
                }) { i ->
                    when (val r = rows[i]) {
                        is Row2.Header -> ExerciseHeader(
                            r, r.order in expanded, { expanded = if (r.order in expanded) expanded - r.order else expanded + r.order },
                            { picker = PickerMode.Swap(r.order) }, { a.onAddSet(r.order); expanded = expanded + r.order }, { a.onRemoveExercise(r.order) },
                            Modifier.animateItem(),
                        )
                        is Row2.Set -> SetRow(
                            r.set, current = r.index == currentIndex,
                            onClick = { if (r.set.status != SetStatus.DONE) { a.onFocus(r.index); onDismiss() } },
                            onToggleSkip = { a.onToggleSkip(r.set.id) }, onRemove = { a.onRemoveSet(r.set.id) },
                            previous = ui.previous[r.set.id] ?: "\u2014", onQuickDone = { a.onQuickDone(r.set.id) },
                            modifier = Modifier.animateItem().testTag("set-${r.set.exerciseOrder}-${r.set.setIndex}"),
                        )
                        is Row2.AddSet -> LhAddRow({ a.onAddSet(r.order) }, Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp), label = "Add set")
                        Row2.AddExercise -> LhAddRow({ picker = PickerMode.Add }, Modifier.padding(16.dp), label = "Add exercise")
                        Row2.Spacer -> Box(Modifier.size(8.dp))
                    }
                }
            }
        }
    }
    when (val p = picker) {
        PickerMode.Add -> ExercisePickerSheet(ui.exercises, { newExercise = it }, { picker = null }, onCreate = a.onCreateExercise)
        is PickerMode.Swap -> ExercisePickerSheet(ui.exercises, { a.onSwapExercise(p.order, it) }, { picker = null }, onCreate = a.onCreateExercise, title = "Swap exercise (remaining sets only)")
        null -> {}
    }
    newExercise?.let { ex ->
        AddExerciseSheet(ex, { n, t, v, w -> a.onAddExercise(ex, n, t, v, w); newExercise = null }, { newExercise = null })
    }
}

private sealed interface Row2 {
    data class Header(val order: Int, val name: String, val summary: String) : Row2
    data class Set(val set: SessionSet, val index: Int) : Row2
    data class AddSet(val order: Int) : Row2
    data object AddExercise : Row2
    data object Spacer : Row2
}

private fun buildRows(sets: List<SessionSet>, expanded: Set<Int>): List<Row2> {
    val rows = mutableListOf<Row2>()
    sets.withIndex().groupBy { it.value.exerciseOrder }.toSortedMap().forEach { (order, g) ->
        val first = g.first().value
        val done = g.count { it.value.status == SetStatus.DONE }
        rows += Row2.Header(order, first.exerciseName, "$done/${g.size} · ${fmtTarget(first.targetType, first.targetValue)}")
        if (order in expanded) {
            g.forEach { rows += Row2.Set(it.value, it.index) }
            rows += Row2.AddSet(order)
        }
    }
    rows += Row2.AddExercise
    rows += Row2.Spacer
    return rows
}

private sealed interface PickerMode {
    data object Add : PickerMode
    data class Swap(val order: Int) : PickerMode
}

@Composable
private fun ExerciseHeader(h: Row2.Header, expanded: Boolean, onToggle: () -> Unit, onSwap: () -> Unit, onAddSet: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    Box(modifier) { SwipeToRemove(onRemove) { ExerciseHeaderContent(h, expanded, onToggle, onSwap, onAddSet, onRemove) } }
}

@Composable
private fun ExerciseHeaderContent(h: Row2.Header, expanded: Boolean, onToggle: () -> Unit, onSwap: () -> Unit, onAddSet: () -> Unit, onRemove: () -> Unit) {
    val c = LhTheme.colors
    var menu by remember { mutableStateOf(false) }
    val rot by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, com.lifehub.app.core.designsystem.lhTween(200), label = "chev")
    Column(Modifier.fillMaxWidth().background(c.card).padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                .testTag("ex-header-${h.order}").padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.ExpandMore, null, tint = c.mutedForeground,
                modifier = Modifier.padding(end = 8.dp).size(20.dp).rotate(rot),
            )
            Text(h.name, style = LhType.heading, color = c.foreground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(h.summary, style = LhType.tnum(LhType.caption), color = c.mutedForeground)
            Box {
                LhIconButton(Icons.Outlined.MoreVert, "Exercise options", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                    DropdownMenuItem(text = { Text("Add set") }, onClick = { menu = false; onAddSet() })
                    DropdownMenuItem(text = { Text("Swap exercise") }, onClick = { menu = false; onSwap() })
                    DropdownMenuItem(text = { Text("Remove exercise", color = c.destructive) }, onClick = { menu = false; onRemove() })
                }
            }
        }
        if (expanded) SetColumnHeader() else Box(Modifier.size(8.dp))
    }
}

