package com.lifehub.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhBottomSheet
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhChip
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhSegmentedControl
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.ExerciseCategory
import com.lifehub.app.data.model.TargetType
import java.util.UUID

private data class ExerciseDraft(
    val name: String = "", val category: ExerciseCategory = ExerciseCategory.PULL, val type: TargetType = TargetType.REPS,
    val error: String? = null,
)

/**
 * Exercise picker. With [onCreate], an empty library shows "No exercises yet" + "Add your first exercise", and typing an
 * unknown name offers "Create"; both open an inline form. [onCreate] must persist the exercise: the sheet then returns
 * to the list, filtered to the new exercise so it can be picked.
 */
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    onCreate: ((Exercise) -> Unit)? = null,
    title: String = "Add exercise",
) {
    val c = LhTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<ExerciseCategory?>(null) }
    var draft by remember { mutableStateOf<ExerciseDraft?>(null) }
    val q = query.trim()
    val visible = exercises.filter { !it.isHidden }
    val filtered = visible.filter { (category == null || it.category == category) && it.name.contains(q, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
    val exact = exercises.any { it.name.equals(q, ignoreCase = true) }
    LhBottomSheet(onDismiss = onDismiss, title = if (draft != null) "New exercise" else title) {
        val d = draft
        if (d != null && onCreate != null) {
            ExerciseFields(d.name, { draft = d.copy(name = it, error = null) }, d.error, d.category, { draft = d.copy(category = it) },
                d.type, { draft = d.copy(type = it) })
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhButton("Cancel", { draft = null }, Modifier.weight(1f), variant = LhButtonVariant.Outline)
                LhButton("Save", {
                    val name = d.name.trim()
                    when {
                        name.isEmpty() -> draft = d.copy(error = "Required")
                        exercises.any { it.name.equals(name, ignoreCase = true) } -> draft = d.copy(error = "Already exists")
                        else -> {
                            onCreate(Exercise(UUID.randomUUID().toString(), name, d.category, d.type, isCustom = true, isHidden = false))
                            query = name; category = null; draft = null
                        }
                    }
                }, Modifier.weight(1f))
            }
        } else if (visible.isEmpty()) {
            LhEmptyState(
                "No exercises yet", Modifier.padding(bottom = 16.dp), description = "Your library is empty. Create the exercises you train.",
                icon = Icons.Outlined.FitnessCenter, actionText = if (onCreate != null) "Add your first exercise" else null,
                onAction = { draft = ExerciseDraft() }, actionIcon = Icons.Outlined.Add,
            )
        } else {
            LhTextField(query, { query = it }, placeholder = "Search exercises")
            LazyRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { LhChip("All", category == null, { category = null }) }
                items(ExerciseCategory.entries.toList()) { cat -> LhChip(cat.label, category == cat, { category = cat }) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                if (onCreate != null && q.isNotEmpty() && !exact) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { draft = ExerciseDraft(name = q, category = category ?: ExerciseCategory.PULL) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = c.accent)
                            Spacer(Modifier.padding(4.dp))
                            Text("Create \"$q\"", style = LhType.body, color = c.accent)
                        }
                    }
                }
                items(filtered, key = { it.id }) { ex ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(ex); onDismiss() }.padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ex.name, style = LhType.body, color = c.foreground)
                        Text(if (ex.defaultTargetType == TargetType.SECONDS) "Seconds" else "Reps", style = LhType.captionMuted)
                    }
                }
                if (filtered.isEmpty() && (onCreate == null || q.isEmpty())) {
                    item { LhEmptyState("No exercises found", icon = Icons.Outlined.SearchOff) }
                }
            }
        }
    }
}

/** Name, category (Pull/Push/Core/Legs) and default type (Reps/Seconds): shared by the exercise editor and the picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseFields(
    name: String, onName: (String) -> Unit, nameError: String?,
    category: ExerciseCategory, onCategory: (ExerciseCategory) -> Unit,
    type: TargetType, onType: (TargetType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LhTextField(name, onName, Modifier.fillMaxWidth(), label = "Name", error = nameError)
        Column {
            Text("Category", style = LhType.label, color = LhTheme.colors.foreground)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseCategory.entries.forEach { LhChip(it.label, it == category, { onCategory(it) }) }
            }
        }
        Column {
            Text("Default type", style = LhType.label, color = LhTheme.colors.foreground)
            LhSegmentedControl(listOf(TargetType.REPS, TargetType.SECONDS), type, onType, Modifier.padding(top = 8.dp),
                label = { if (it == TargetType.REPS) "Reps" else "Seconds" })
        }
    }
}
