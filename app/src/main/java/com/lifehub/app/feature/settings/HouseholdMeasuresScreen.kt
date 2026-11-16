package com.lifehub.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.HouseholdMeasure
import com.lifehub.app.data.repo.BaseFoodRepository
import com.lifehub.app.data.repo.FoodRepository
import com.lifehub.app.data.repo.HouseholdMeasureRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.feature.food.fmtGrams
import com.lifehub.app.feature.food.parseGrams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the table; [food] = the food a specific measure applies to (null = generic). */
data class MeasureRow(val id: String, val label: String, val grams: Double, val food: String?, val foodId: String?, val baseFoodId: String?)

data class MeasuresUiState(val generic: List<MeasureRow> = emptyList(), val specific: List<MeasureRow> = emptyList())

class HouseholdMeasuresViewModel(
    private val repo: HouseholdMeasureRepository,
    base: BaseFoodRepository,
    food: FoodRepository,
    private val time: TimeSource,
) : ViewModel() {
    private val names = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        viewModelScope.launch {
            val b = runCatching { base.all() }.getOrDefault(emptyList()).associate { it.id to it.name }
            val f = repo.all().mapNotNull { it.foodId }.distinct().mapNotNull { id -> food.getFood(id)?.let { id to it.name } }
            names.value = b + f
        }
    }

    val state: StateFlow<MeasuresUiState> = combine(repo.observe(), names) { list, n ->
        val rows = list.map { MeasureRow(it.id, it.label, it.grams, (it.foodId ?: it.baseFoodId)?.let { k -> n[k] ?: k }, it.foodId, it.baseFoodId) }
        MeasuresUiState(
            rows.filter { it.foodId == null && it.baseFoodId == null }.sortedBy { it.label },
            rows.filter { it.foodId != null || it.baseFoodId != null }.sortedWith(compareBy({ it.food }, { it.label })),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasuresUiState())

    fun save(row: MeasureRow?, label: String, grams: Double) = viewModelScope.launch {
        if (label.isBlank() || grams <= 0) return@launch
        repo.save(row?.id, label, grams, row?.foodId, row?.baseFoodId, time.now())
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun restoreDefaults() = viewModelScope.launch { repo.restoreDefaults(time.now()) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { HouseholdMeasuresViewModel(c.measures, c.baseFoods, c.food, c.time) } }
    }
}

@Composable
fun HouseholdMeasuresRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: HouseholdMeasuresViewModel = viewModel(factory = HouseholdMeasuresViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    HouseholdMeasuresScreen(state, onBack, onSave = { r, l, g -> vm.save(r, l, g) }, onDelete = { vm.delete(it) }, onRestore = { vm.restoreDefaults() })
}

/** Editable household measures (design doc §9): grams per spoon, glass, slice, piece... generic or for one food. */
@Composable
fun HouseholdMeasuresScreen(
    state: MeasuresUiState,
    onBack: () -> Unit,
    onSave: (MeasureRow?, String, Double) -> Unit = { _, _, _ -> },
    onDelete: (String) -> Unit = {},
    onRestore: () -> Unit = {},
) {
    var editing by remember { mutableStateOf<MeasureRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LhTopBar("Household measures", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "How many grams the copilot counts for a spoon, a glass, a slice or a piece. A spoon means a level spoon unless " +
                    "you say “colmo”. These starting values are approximate: weigh yours once and adjust them.",
                style = LhType.captionMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
            LhSectionHeader("General")
            MeasureGroup(state.generic) { editing = it }
            LhAddRow({ adding = true }, Modifier.padding(top = 8.dp).testTag("measure-add"), label = "Add measure")
            LhSectionHeader("Specific foods")
            MeasureGroup(state.specific) { editing = it }
            Spacer(Modifier.height(16.dp))
            LhButton("Restore default values", { confirmRestore = true }, Modifier.testTag("measure-restore"), variant = LhButtonVariant.Outline, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.RestartAlt)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (adding || editing != null) MeasureDialog(editing, onDismiss = { adding = false; editing = null },
        onSave = { l, g -> onSave(editing, l, g); adding = false; editing = null },
        onDelete = editing?.let { r -> { onDelete(r.id); editing = null } })
    if (confirmRestore) LhConfirmDialog("Restore default values?", "Every default measure goes back to its starting value. Measures you added are kept.",
        "Restore", { onRestore(); confirmRestore = false }, { confirmRestore = false }, destructive = false)
}

@Composable
private fun MeasureGroup(rows: List<MeasureRow>, onClick: (MeasureRow) -> Unit) {
    if (rows.isEmpty()) { Text("None", style = LhType.captionMuted, modifier = Modifier.padding(start = 4.dp)); return }
    LhGroupCard(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, r ->
            LhListRow(
                r.label, Modifier.testTag("measure-row"), subtitle = r.food, onClick = { onClick(r) }, showDivider = i < rows.lastIndex,
                trailing = { Text("${fmtGrams(r.grams)} g", style = LhType.body, color = LhTheme.colors.foreground) },
            )
        }
    }
}

@Composable
private fun MeasureDialog(row: MeasureRow?, onDismiss: () -> Unit, onSave: (String, Double) -> Unit, onDelete: (() -> Unit)?) {
    var label by remember(row) { mutableStateOf(row?.label ?: "") }
    var grams by remember(row) { mutableStateOf(row?.grams?.let(::fmtGrams) ?: "") }
    val g = parseGrams(grams)
    LhDialogContainer(onDismiss) {
        Text(if (row == null) "New measure" else "Edit measure", style = LhType.heading, color = LhTheme.colors.foreground)
        row?.food?.let { Text(it, style = LhType.captionMuted, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(16.dp))
        LhTextField(label, { label = it }, Modifier.fillMaxWidth().testTag("measure-label"), label = "Name", placeholder = "e.g. cucchiaio")
        Spacer(Modifier.height(12.dp))
        LhTextField(grams, { grams = it }, Modifier.fillMaxWidth().testTag("measure-grams"), label = "Grams",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (onDelete != null) LhButton("Delete", onDelete, variant = LhButtonVariant.Outline)
            Spacer(Modifier.weight(1f))
            LhButton("Cancel", onDismiss, variant = LhButtonVariant.Outline)
            LhButton("Save", { onSave(label, g!!) }, Modifier.testTag("measure-save"), enabled = label.isNotBlank() && g != null && g > 0)
        }
    }
}
