package com.lifehub.app.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.SnackbarBus
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhNumberField
import com.lifehub.app.core.designsystem.components.LhSaveBar
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.data.model.Food
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomFoodState(val form: CustomFoodForm = CustomFoodForm(), val existing: Boolean = false, val showError: Boolean = false)

class CustomFoodViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val foodId: String? = handle.get<String?>("foodId")
    private var original: Food? = null
    private val _state = MutableStateFlow(CustomFoodState(CustomFoodForm(barcode = handle.get<String?>("barcode").orEmpty()), existing = foodId != null))
    val state: StateFlow<CustomFoodState> = _state

    init {
        if (foodId != null) viewModelScope.launch {
            c.food.getFood(foodId)?.let { f -> original = f; _state.update { it.copy(form = CustomFoodForm.from(f)) } }
        }
    }

    fun update(f: (CustomFoodForm) -> CustomFoodForm) = _state.update { it.copy(form = f(it.form), showError = false) }

    fun save(onDone: (msg: String, undo: () -> Unit) -> Unit) {
        val form = _state.value.form
        val food = form.toFood(original) ?: run { _state.update { it.copy(showError = true) }; return }
        viewModelScope.launch {
            val prev = original
            c.food.saveFood(food)
            onDone(if (prev == null) "Saved ${food.name}" else "Updated ${food.name}") {
                viewModelScope.launch { if (prev == null) c.food.deleteFood(food.id) else c.food.saveFood(prev) }
            }
        }
    }

    fun delete(onDone: (msg: String, undo: () -> Unit) -> Unit) {
        val prev = original ?: return
        viewModelScope.launch {
            c.food.deleteFood(prev.id)
            onDone("Deleted ${prev.name}") { viewModelScope.launch { c.food.saveFood(prev) } }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { CustomFoodViewModel(c, createSavedStateHandle()) } }
    }
}

@Composable
fun CustomFoodRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: CustomFoodViewModel = viewModel(factory = CustomFoodViewModel.factory(c))
    val state by vm.state.collectAsState()
    CustomFoodScreen(
        state, onBack, vm::update,
        onSave = { vm.save { msg, undo -> onBack(); SnackbarBus.show(msg, "Undo", undo) } },
        onDelete = { vm.delete { msg, undo -> onBack(); SnackbarBus.show(msg, "Undo", undo) } },
    )
}

@Composable
fun CustomFoodScreen(
    state: CustomFoodState, onBack: () -> Unit, onChange: ((CustomFoodForm) -> CustomFoodForm) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, autoFocus: Boolean = false,
) {
    val c = LhTheme.colors
    val f = state.form
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar(if (state.existing) "Edit food" else "Custom food", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LhTextField(f.name, { v -> onChange { it.copy(name = v) } }, Modifier.fillMaxWidth().testTag("custom-name"), label = "Name", placeholder = "e.g. Homemade granola")
            LhTextField(f.brand, { v -> onChange { it.copy(brand = v) } }, Modifier.fillMaxWidth().testTag("custom-brand"), label = "Brand (optional)")
            Text("Per 100 g", style = LhType.heading, color = c.foreground, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhNumberField(f.protein, { v -> onChange { it.copy(protein = v) } }, Modifier.weight(1f).testTag("custom-protein"), label = "Protein", suffix = "g", decimals = 1)
                LhNumberField(f.carbs, { v -> onChange { it.copy(carbs = v) } }, Modifier.weight(1f).testTag("custom-carbs"), label = "Carbs", suffix = "g", decimals = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhNumberField(f.fat, { v -> onChange { it.copy(fat = v) } }, Modifier.weight(1f).testTag("custom-fat"), label = "Fat", suffix = "g", decimals = 1)
                Box(Modifier.weight(1f))
            }
            LhNumberField(
                f.kcal, { v -> onChange { it.copy(kcal = v) } }, Modifier.fillMaxWidth().testTag("custom-kcal"), label = "Calories (optional)",
                placeholder = "Auto · ${f.autoKcal}", suffix = "kcal", decimals = 1,
            )
            Text("Leave calories empty to use 4·P + 4·C + 9·F.", style = LhType.captionMuted)
            LhNumberField(
                f.serving, { v -> onChange { it.copy(serving = v) } }, Modifier.fillMaxWidth().testTag("custom-serving"), label = "Serving size (optional)",
                suffix = "g", decimals = 1, imeAction = ImeAction.Done,
            )
            LhTextField(f.barcode, { v -> onChange { it.copy(barcode = v.filter { ch -> ch.isDigit() }) } }, Modifier.fillMaxWidth(), label = "Barcode (optional)")
            if (state.showError) Text("Enter a name and at least one nutrition value.", style = LhType.caption, color = c.destructive)
        }
        LhSaveBar(onSave, label = if (state.existing) "Save changes" else "Save food", enabled = true, secondary = if (state.existing) ({
            LhButton("Delete", { confirm = true }, Modifier.fillMaxWidth(), variant = LhButtonVariant.Outline, leadingIcon = Icons.Outlined.Delete)
        }) else null)
    }
    if (confirm) LhConfirmDialog(
        title = "Delete this food?", message = "Past entries keep their values.", confirmText = "Delete",
        onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false },
    )
}
