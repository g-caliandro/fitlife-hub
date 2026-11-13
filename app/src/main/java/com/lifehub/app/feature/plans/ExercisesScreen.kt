package com.lifehub.app.feature.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.ExerciseIcon
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhGroupCard
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhListItem
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.data.repo.ExerciseRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExercisesViewModel(repo: ExerciseRepository) : ViewModel() {
    val exercises: StateFlow<List<Exercise>?> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { ExercisesViewModel(c.exercises) } }
    }
}

@Composable
fun ExercisesRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: ExercisesViewModel = viewModel(factory = ExercisesViewModel.factory(c))
    val list by vm.exercises.collectAsState()
    list?.let { ExercisesScreen(it, onBack, onAdd = { onNavigate(Routes.exerciseEdit()) }, onEdit = { id -> onNavigate(Routes.exerciseEdit(id)) }) }
}

@Composable
fun ExercisesScreen(exercises: List<Exercise>, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val shown = exercises.filter { it.name.contains(q, ignoreCase = true) }
    val grouped = shown.groupBy { it.category }.toSortedMap()
    Column(Modifier.fillMaxSize()) {
        LhTopBar("Exercises", onBack = onBack, actions = { LhIconButton(Icons.Outlined.Add, "Add exercise", onAdd) })
        if (exercises.isEmpty()) {
            LhEmptyState(
                "No exercises yet", Modifier.fillMaxWidth().padding(24.dp), description = "Create the exercises you train, then use them in your plans.",
                icon = Icons.Outlined.FitnessCenter, actionText = "Add your first exercise", onAction = onAdd, actionIcon = Icons.Outlined.Add,
            )
            return@Column
        }
        LhTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), placeholder = "Search exercises")
        if (shown.isEmpty()) {
            LhEmptyState("No exercises found", Modifier.fillMaxWidth().padding(24.dp), icon = Icons.Outlined.SearchOff, actionText = "Add exercise", onAction = onAdd, actionIcon = Icons.Outlined.Add)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                grouped.forEach { (cat, list) ->
                    item(key = "h-$cat") { LhSectionHeader(cat.label) }
                    item(key = "g-$cat") {
                        LhGroupCard {
                            list.forEachIndexed { i, ex ->
                                LhListItem(
                                    ex.name, Modifier.alpha(if (ex.isHidden) 0.5f else 1f), standalone = false, showDivider = i < list.lastIndex,
                                    subtitle = if (ex.defaultTargetType == TargetType.SECONDS) "Seconds" else "Reps",
                                    leading = { ExerciseIcon(ex, 40.dp) },
                                    onClick = { onEdit(ex.id) },
                                    trailing = { if (ex.isHidden) LhBadge("Hidden", variant = LhBadgeVariant.Outline) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
