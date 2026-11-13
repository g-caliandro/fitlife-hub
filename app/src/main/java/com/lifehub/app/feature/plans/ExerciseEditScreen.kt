package com.lifehub.app.feature.plans

import com.lifehub.app.core.designsystem.components.LhCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.ui.platform.testTag
import com.lifehub.app.core.designsystem.components.LhSaveBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.ui.ExerciseFields
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.ExerciseCategory
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.data.repo.ExerciseDeleteResult
import com.lifehub.app.data.repo.ExerciseRepository
import com.lifehub.app.data.repo.ExerciseSaveResult
import com.lifehub.app.di.AppContainer
import com.lifehub.app.navigation.LocalSnackbarHostState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ExerciseForm(
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.PULL,
    val type: TargetType = TargetType.REPS,
    val isHidden: Boolean = false,
    val nameError: String? = null,
    val isNew: Boolean = true,
)

class ExerciseEditViewModel(private val repo: ExerciseRepository, handle: SavedStateHandle) : ViewModel() {
    private val exerciseId: String? = handle["exerciseId"]
    private val _form = MutableStateFlow(ExerciseForm())
    val form = _form.asStateFlow()

    init {
        if (exerciseId != null) viewModelScope.launch {
            repo.get(exerciseId)?.let { _form.value = ExerciseForm(it.name, it.category, it.defaultTargetType, isHidden = it.isHidden, isNew = false) }
        }
    }

    fun setName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun setCategory(c: ExerciseCategory) = _form.update { it.copy(category = c) }
    fun setType(t: TargetType) = _form.update { it.copy(type = t) }

    /** Hidden exercises stay in history but are left out of the pickers (the way out for in-use exercises). Applied on save. */
    fun toggleHidden() = _form.update { it.copy(isHidden = !it.isHidden) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) { _form.value = f.copy(nameError = "Required"); return }
        viewModelScope.launch {
            val ex = Exercise(exerciseId ?: UUID.randomUUID().toString(), f.name.trim(), f.category, f.type, isCustom = true, isHidden = f.isHidden)
            when (repo.save(ex)) {
                ExerciseSaveResult.Ok -> onDone()
                ExerciseSaveResult.DuplicateName -> _form.update { it.copy(nameError = "Already exists") }
            }
        }
    }

    fun delete(onResult: (ExerciseDeleteResult) -> Unit) {
        val id = exerciseId ?: return
        viewModelScope.launch { onResult(repo.delete(id)) }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { ExerciseEditViewModel(c.exercises, createSavedStateHandle()) } }
    }
}

@Composable
fun ExerciseEditRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: ExerciseEditViewModel = viewModel(factory = ExerciseEditViewModel.factory(c))
    val form by vm.form.collectAsState()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    ExerciseEditScreen(
        form, onBack, vm::setName, vm::setCategory, vm::setType, onSave = { vm.save(onBack) },
        onToggleHidden = vm::toggleHidden,
        onDelete = {
            vm.delete { r ->
                if (r == ExerciseDeleteResult.InUse) scope.launch { snackbar.showSnackbar("Used in plans or sessions — can't delete. Hide it instead.") }
                else onBack()
            }
        },
    )
}

@Composable
fun ExerciseEditScreen(
    form: ExerciseForm, onBack: () -> Unit, onName: (String) -> Unit, onCategory: (ExerciseCategory) -> Unit,
    onType: (TargetType) -> Unit, onSave: () -> Unit, onDelete: () -> Unit, onToggleHidden: () -> Unit = {},
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LhTopBar(if (form.isNew) "New exercise" else "Edit exercise", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          LhCard(Modifier.fillMaxWidth()) {
           ExerciseFields(form.name, onName, form.nameError, form.category, onCategory, form.type, onType)
          }
            if (!form.isNew) LhButton(if (form.isHidden) "Unhide" else "Hide from picker", onToggleHidden, Modifier.fillMaxWidth(), variant = LhButtonVariant.Outline)
            if (!form.isNew) LhButton("Delete", { confirm = true }, Modifier.fillMaxWidth(), variant = LhButtonVariant.Destructive, leadingIcon = Icons.Outlined.DeleteOutline)
        }
        LhSaveBar(onSave, Modifier.testTag("save-bar"))
    }
    if (confirm) LhConfirmDialog("Delete exercise?", "This can't be undone.", "Delete", { confirm = false; onDelete() }, { confirm = false })
}
