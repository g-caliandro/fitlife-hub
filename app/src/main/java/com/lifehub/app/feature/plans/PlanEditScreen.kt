package com.lifehub.app.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhNumberField
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhSegmentedControl
import com.lifehub.app.core.designsystem.components.LhStepper
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtWeight
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.core.ui.ExercisePickerSheet
import com.lifehub.app.core.ui.WeekdayChips
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.GoalPlanLink
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.model.Plan
import com.lifehub.app.data.model.PlanExercise
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.data.repo.ExerciseRepository
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.data.repo.PlanRepository
import com.lifehub.app.di.AppContainer
import com.lifehub.app.navigation.LocalSnackbarHostState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import com.lifehub.app.core.designsystem.SnackbarBus
import com.lifehub.app.core.designsystem.components.LhSaveBar
import java.util.UUID

data class RowForm(
    val key: String,
    val exercise: Exercise,
    val sets: String = "3",
    val targetType: TargetType = TargetType.REPS,
    val value: String = "8",
    val weight: String = "",
    val rest: String = "90",
    val setsError: String? = null,
    val valueError: String? = null,
    val weightError: String? = null,
    val restError: String? = null,
) {
    val hasError get() = listOf(setsError, valueError, weightError, restError).any { it != null }
}

data class GoalLinkForm(val goalId: String, val name: String, val checked: Boolean, val needed: String = "", val error: String? = null)

data class PlanForm(
    val name: String = "",
    val weekdays: Set<Int> = emptySet(),
    val rows: List<RowForm> = emptyList(),
    val goals: List<GoalLinkForm> = emptyList(),
    val notes: String = "",
    val isNew: Boolean = true,
    val nameError: String? = null,
    val rowsError: String? = null,
    val errorTick: Int = 0,
    val firstErrorItem: Int = 0,
    val dayType: DayType? = null,
)

fun newRow(ex: Exercise) = RowForm(
    key = UUID.randomUUID().toString(), exercise = ex,
    targetType = if (ex.defaultTargetType == TargetType.SECONDS) TargetType.SECONDS else TargetType.REPS,
    value = if (ex.defaultTargetType == TargetType.SECONDS) "30" else "8",
)

/** Validates per F-PLAN-1..3. Item indices match the LazyColumn layout in [PlanEditScreen]. */
fun PlanForm.validated(): PlanForm {
    val rs = rows.map { r ->
        val sets = r.sets.toIntOrNull()
        val v = r.value.toIntOrNull()
        val w = r.weight.toDoubleOrNull()
        val rest = r.rest.toIntOrNull()
        r.copy(
            setsError = if (sets == null || sets !in 1..20) "1–20" else null,
            valueError = if (r.targetType != TargetType.MAX && (v == null || v < 1)) "≥ 1" else null,
            weightError = if (r.weight.isNotBlank() && (w == null || w < 0)) "≥ 0" else null,
            restError = if (rest == null || rest !in 0..600) "0–600" else null,
        )
    }
    val gs = goals.map { g ->
        val n = g.needed.toIntOrNull()
        g.copy(error = if (g.checked && g.needed.isNotBlank() && (n == null || n !in 1..500)) "1–500" else null)
    }
    val nameErr = if (name.isBlank()) "Required" else null
    val rowsErr = if (rs.isEmpty()) "Add at least one exercise" else null
    val first = when {
        nameErr != null -> 0
        rs.any { it.hasError } -> 4 + rs.indexOfFirst { it.hasError }
        rowsErr != null -> 4
        gs.any { it.error != null } -> 6 + rs.size + gs.indexOfFirst { it.error != null }
        else -> 0
    }
    return copy(
        rows = rs, goals = gs, nameError = nameErr, rowsError = rowsErr,
        errorTick = errorTick + if (nameErr != null || rowsErr != null || rs.any { it.hasError } || gs.any { it.error != null }) 1 else 0,
        firstErrorItem = first,
    )
}

val PlanForm.isValid: Boolean
    get() = name.isNotBlank() && rows.isNotEmpty() && rows.none { it.hasError } && goals.none { it.error != null }

class PlanEditViewModel(
    private val plans: PlanRepository, private val goalsRepo: GoalRepository, private val exercisesRepo: ExerciseRepository,
    private val time: TimeSource, handle: SavedStateHandle,
    private val undoScope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.GlobalScope,
) : ViewModel() {
    private val planId: String? = handle["planId"]
    private val preLinkGoal: String? = handle["goalId"]
    private var existing: Plan? = null
    private val _form = MutableStateFlow(PlanForm())
    val form: StateFlow<PlanForm> = _form.asStateFlow()
    private val _ex = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _ex.asStateFlow()
    private var lastRemoved: Pair<Int, RowForm>? = null

    init {
        viewModelScope.launch { exercisesRepo.observeAll().collect { _ex.value = it } }
        viewModelScope.launch {
            val links = planId?.let { plans.getLinks(it) }.orEmpty().associateBy { it.goalId }
            val goals = goalsRepo.observeGoals().first().map { it.goal }
                .filter { it.state == GoalState.ACTIVE || it.id in links }
                .sortedBy { it.name.lowercase() }
                .map { g ->
                    val l = links[g.id]
                    GoalLinkForm(g.id, g.name, checked = l != null || g.id == preLinkGoal, needed = l?.sessionsNeeded?.toString().orEmpty())
                }
            val p = planId?.let { plans.get(it) }
            if (p != null) {
                existing = p.plan
                _form.value = PlanForm(
                    p.plan.name, p.plan.weekdays,
                    p.exercises.map { RowForm(it.planExercise.id, it.exercise, it.planExercise.sets.toString(), it.planExercise.targetType,
                        it.planExercise.targetValue?.toString().orEmpty(), it.planExercise.weightKg?.let(::fmtWeight).orEmpty(), it.planExercise.restSeconds.toString()) },
                    goals, p.plan.notes.orEmpty(), isNew = false, dayType = p.plan.dayType,
                )
            } else _form.value = PlanForm(goals = goals)
        }
    }

    fun setName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun setNotes(v: String) = _form.update { it.copy(notes = v) }
    fun setDayType(t: DayType?) = _form.update { it.copy(dayType = t) }
    fun toggleDay(d: Int) = _form.update { it.copy(weekdays = if (d in it.weekdays) it.weekdays - d else it.weekdays + d) }
    fun addExercise(ex: Exercise) = _form.update { it.copy(rows = it.rows + newRow(ex), rowsError = null) }
    /** Saves a new exercise from the picker; it then shows up in the picker (exercises flow) ready to be picked. */
    fun createExercise(ex: Exercise) { viewModelScope.launch { exercisesRepo.save(ex) } }
    fun updateRow(key: String, f: (RowForm) -> RowForm) = _form.update { s -> s.copy(rows = s.rows.map { if (it.key == key) f(it) else it }) }
    fun move(key: String, delta: Int) = _form.update { s ->
        val i = s.rows.indexOfFirst { it.key == key }
        val j = i + delta
        if (i < 0 || j !in s.rows.indices) s else s.copy(rows = s.rows.toMutableList().also { java.util.Collections.swap(it, i, j) })
    }
    fun removeRow(key: String) = _form.update { s ->
        val i = s.rows.indexOfFirst { it.key == key }
        if (i >= 0) lastRemoved = i to s.rows[i]
        s.copy(rows = s.rows.filter { it.key != key })
    }
    fun undoRemove() {
        val (i, r) = lastRemoved ?: return
        lastRemoved = null
        _form.update { s -> s.copy(rows = s.rows.toMutableList().also { it.add(i.coerceAtMost(it.size), r) }) }
    }
    fun toggleGoal(id: String) = _form.update { s -> s.copy(goals = s.goals.map { if (it.goalId == id) it.copy(checked = !it.checked, error = null) else it }) }
    fun setNeeded(id: String, v: String) = _form.update { s -> s.copy(goals = s.goals.map { if (it.goalId == id) it.copy(needed = v, checked = true, error = null) else it }) }

    fun save(onDone: (undo: () -> Unit) -> Unit) {
        val f = _form.value.validated()
        _form.value = f
        if (!f.isValid) return
        viewModelScope.launch {
            val id = existing?.id ?: UUID.randomUUID().toString()
            val prevPlan = planId?.let { plans.get(it) }
            val prevLinks = planId?.let { plans.getLinks(it) }.orEmpty()
            val plan = Plan(id, f.name.trim(), f.weekdays, f.notes.trim().ifEmpty { null }, existing?.isArchived ?: false, existing?.createdAt ?: time.now(), f.dayType)
            val items = f.rows.mapIndexed { i, r ->
                PlanExercise(
                    UUID.randomUUID().toString(), id, r.exercise.id, i, r.sets.toInt(), r.targetType,
                    if (r.targetType == TargetType.MAX) null else r.value.toInt(), r.weight.toDoubleOrNull(), r.rest.toInt(),
                )
            }
            val links = f.goals.filter { it.checked }.map { GoalPlanLink(it.goalId, id, it.needed.toIntOrNull()) }
            plans.save(plan, items, links)
            onDone {
                undoScope.launch {
                    if (prevPlan == null) plans.delete(id)
                    else plans.save(prevPlan.plan, prevPlan.exercises.map { it.planExercise }, prevLinks)
                }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { PlanEditViewModel(c.plans, c.goals, c.exercises, c.time, createSavedStateHandle(), c.appScope) }
        }
    }
}

@Composable
fun PlanEditRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: PlanEditViewModel = viewModel(factory = PlanEditViewModel.factory(c))
    val form by vm.form.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    PlanEditScreen(
        form, exercises,
        PlanEditActions(
            onBack = onBack, onSave = { vm.save { undo -> onBack(); SnackbarBus.show("Plan saved", "Undo", undo) } }, onName = vm::setName, onNotes = vm::setNotes, onToggleDay = vm::toggleDay, onDayType = vm::setDayType,
            onAddExercise = vm::addExercise, onCreateExercise = vm::createExercise, onUpdateRow = vm::updateRow, onMove = vm::move,
            onRemove = { key ->
                vm.removeRow(key)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    if (snackbar.showSnackbar("Exercise removed", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoRemove()
                }
            },
            onToggleGoal = vm::toggleGoal, onNeeded = vm::setNeeded,
        ),
    )
}

class PlanEditActions(
    val onBack: () -> Unit = {}, val onSave: () -> Unit = {}, val onName: (String) -> Unit = {}, val onNotes: (String) -> Unit = {},
    val onToggleDay: (Int) -> Unit = {}, val onDayType: (DayType?) -> Unit = {}, val onAddExercise: (Exercise) -> Unit = {}, val onCreateExercise: (Exercise) -> Unit = {},
    val onUpdateRow: (String, (RowForm) -> RowForm) -> Unit = { _, _ -> }, val onMove: (String, Int) -> Unit = { _, _ -> },
    val onRemove: (String) -> Unit = {}, val onToggleGoal: (String) -> Unit = {}, val onNeeded: (String, String) -> Unit = { _, _ -> },
)

@Composable
fun PlanEditScreen(form: PlanForm, exercises: List<Exercise>, a: PlanEditActions) {
    val c = LhTheme.colors
    var picker by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(form.errorTick) { if (form.errorTick > 0) listState.animateScrollToItem(form.firstErrorItem) }
    Column(Modifier.fillMaxSize()) {
        LhTopBar(if (form.isNew) "New plan" else "Edit plan", onBack = a.onBack)
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LhTextField(form.name, a.onName, Modifier.fillMaxWidth(), label = "Name", error = form.nameError) }
            item {
                Column {
                    Text("Day type", style = LhType.label, color = c.foreground)
                    Spacer(Modifier.height(6.dp))
                    DayTypeSelector(form.dayType, a.onDayType)
                }
            }
            item {
                Column {
                    Text("Days", style = LhType.label, color = c.foreground)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                        WeekdayChips(form.weekdays, a.onToggleDay)
                    }
                }
            }
            item { Text("Exercises", style = LhType.heading, color = c.foreground, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(form.rows, key = { _, r -> r.key }) { i, r ->
                RowCard(i, r, last = i == form.rows.lastIndex, a)
            }
            item {
                Column {
                    LhButton("Add exercise", { picker = true }, Modifier.fillMaxWidth(), variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.Add)
                    form.rowsError?.let { Text(it, style = LhType.caption, color = c.destructive, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            item { Text("Linked goals", style = LhType.heading, color = c.foreground, modifier = Modifier.padding(top = 8.dp)) }
            if (form.goals.isEmpty()) item { Text("No active goals to link.", style = LhType.captionMuted) }
            itemsIndexed(form.goals, key = { _, g -> g.goalId }) { _, g ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { a.onToggleGoal(g.goalId) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(g.checked, { a.onToggleGoal(g.goalId) })
                        Text(g.name, style = LhType.body, color = c.foreground, maxLines = 2)
                    }
                    LhNumberField(g.needed, { a.onNeeded(g.goalId, it) }, Modifier.padding(start = 8.dp).weight(0.7f),
                        placeholder = "Sessions", enabled = g.checked, error = g.error)
                }
            }
            item { LhTextField(form.notes, a.onNotes, Modifier.fillMaxWidth(), label = "Notes", singleLine = false, minLines = 2) }
        }
        LhSaveBar(a.onSave, Modifier.testTag("save-bar"))
    }
    if (picker) ExercisePickerSheet(exercises, onPick = a.onAddExercise, onDismiss = { picker = false }, onCreate = a.onCreateExercise)
}

/** Compact 32dp icon button for dense card headers. */
@Composable
private fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LhTheme.colors
    Box(
        Modifier.size(32.dp).clip(LhRadius.mdShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(18.dp), tint = if (enabled) c.mutedForeground else c.mutedForeground.copy(alpha = 0.4f))
    }
}

/** Label above, control below: every cell uses the same label style/gap so rows align on one baseline. */
@Composable
private fun LabeledCell(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = LhType.label, color = LhTheme.colors.foreground, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(LhSize.input), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun RowCard(index: Int, r: RowForm, last: Boolean, a: PlanEditActions) {
    val c = LhTheme.colors
    LhCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}", style = LhType.tnum(LhType.label), color = c.mutedForeground, modifier = Modifier.width(18.dp))
                Text(r.exercise.name, style = LhType.heading, color = c.foreground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                SmallIconButton(Icons.Outlined.ArrowUpward, "Move up", { a.onMove(r.key, -1) }, enabled = index > 0)
                SmallIconButton(Icons.Outlined.ArrowDownward, "Move down", { a.onMove(r.key, 1) }, enabled = !last)
                SmallIconButton(Icons.Outlined.Close, "Remove", { a.onRemove(r.key) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sets", style = LhType.label, color = c.foreground)
                    LhStepper(r.sets, { v -> a.onUpdateRow(r.key) { it.copy(sets = v, setsError = null) } }, 1.0, Modifier.fillMaxWidth(), min = 1.0, max = 20.0)
                }
                LabeledCell("Target", Modifier.fillMaxWidth()) {
                    LhSegmentedControl(
                        listOf(TargetType.REPS, TargetType.MAX, TargetType.SECONDS), r.targetType,
                        { t -> a.onUpdateRow(r.key) { it.copy(targetType = t, valueError = null) } },
                        label = { when (it) { TargetType.REPS -> "Reps"; TargetType.MAX -> "Max"; TargetType.SECONDS -> "Seconds" } },
                    )
                }
            }
            r.setsError?.let { Text(it, style = LhType.caption, color = c.destructive) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                if (r.targetType == TargetType.MAX) {
                    LabeledCell("Reps", Modifier.weight(1f)) {
                        Box(Modifier.fillMaxSize().border(1.dp, c.input, LhRadius.mdShape).background(c.muted, LhRadius.mdShape), contentAlignment = Alignment.Center) {
                            Text("MAX", style = LhType.label, color = c.mutedForeground)
                        }
                    }
                } else {
                    LhNumberField(r.value, { v -> a.onUpdateRow(r.key) { it.copy(value = v, valueError = null) } }, Modifier.weight(1f),
                        label = if (r.targetType == TargetType.SECONDS) "Seconds" else "Reps", error = r.valueError)
                }
                LhNumberField(r.weight, { v -> a.onUpdateRow(r.key) { it.copy(weight = v, weightError = null) } }, Modifier.weight(1f),
                    label = "Weight", suffix = "kg", decimals = 1, placeholder = "0.0", error = r.weightError)
                LhNumberField(r.rest, { v -> a.onUpdateRow(r.key) { it.copy(rest = v, restError = null) } }, Modifier.weight(1f),
                    label = "Rest", suffix = "s", error = r.restError)
            }
        }
    }
}
