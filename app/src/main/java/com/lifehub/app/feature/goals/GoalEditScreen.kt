package com.lifehub.app.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.SnackbarBus
import com.lifehub.app.core.designsystem.components.LhSaveBar
import com.lifehub.app.core.designsystem.components.LhDatePickerField
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.time.TimeSource
import com.lifehub.app.data.model.Goal
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.data.repo.GoalRepository
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

const val DEFAULT_GOAL_DAYS = 84L

data class GoalForm(
    val name: String,
    val notes: String,
    val start: LocalDate,
    val deadline: LocalDate,
    val deadlineIsAuto: Boolean = true,
    val nameError: String? = null,
    val deadlineError: String? = null,
    val isNew: Boolean = true,
) {
    fun validated(): GoalForm = copy(
        nameError = if (name.isBlank()) "Required" else null,
        deadlineError = if (!deadline.isAfter(start)) "Must be after start date" else null,
    )
    val isValid get() = name.isNotBlank() && deadline.isAfter(start)
}

fun newGoalForm(today: LocalDate) = GoalForm("", "", today, today.plusDays(DEFAULT_GOAL_DAYS))

/** "12 weeks", "2 weeks 3 days", or "custom" handled by caller. */
fun durationLabel(start: LocalDate, end: LocalDate): String {
    val d = ChronoUnit.DAYS.between(start, end).toInt()
    if (d <= 0) return "—"
    val w = d / 7
    val r = d % 7
    return buildString {
        if (w > 0) append(if (w == 1) "1 week" else "$w weeks")
        if (r > 0) { if (w > 0) append(' '); append(if (r == 1) "1 day" else "$r days") }
    }
}

class GoalEditViewModel(private val goals: GoalRepository, private val time: TimeSource, handle: SavedStateHandle, private val undoScope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.GlobalScope) : ViewModel() {
    private val goalId: String? = handle["goalId"]
    private val _form = MutableStateFlow(newGoalForm(time.today()))
    val form = _form.asStateFlow()
    private var existing: Goal? = null

    init {
        if (goalId != null) viewModelScope.launch {
            goals.get(goalId)?.let { g ->
                existing = g
                _form.value = GoalForm(g.name, g.notes.orEmpty(), g.startDate, g.deadline, g.deadlineIsAuto, isNew = false)
            }
        }
    }

    fun setName(v: String) = _form.update { it.copy(name = v.take(60), nameError = null) }
    fun setNotes(v: String) = _form.update { it.copy(notes = v) }
    fun setStart(d: LocalDate) = _form.update {
        val dl = if (it.deadlineIsAuto) d.plusDays(DEFAULT_GOAL_DAYS) else it.deadline
        it.copy(start = d, deadline = dl, deadlineError = null)
    }
    fun setDeadline(d: LocalDate) = _form.update { it.copy(deadline = d, deadlineIsAuto = false, deadlineError = null) }

    fun save(onDone: (undo: () -> Unit) -> Unit) {
        val f = _form.value.validated()
        _form.value = f
        if (!f.isValid) return
        viewModelScope.launch {
            val e = existing
            val newId = e?.id ?: UUID.randomUUID().toString()
            goals.save(
                Goal(
                    id = newId, name = f.name.trim(), notes = f.notes.trim().ifEmpty { null },
                    startDate = f.start, deadline = f.deadline, deadlineIsAuto = f.deadlineIsAuto,
                    state = e?.state ?: GoalState.ACTIVE, achievedAt = e?.achievedAt, createdAt = e?.createdAt ?: time.now(),
                ),
            )
            onDone { undoScope.launch { if (e != null) goals.save(e) else goals.delete(newId) } }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { GoalEditViewModel(c.goals, c.time, createSavedStateHandle(), c.appScope) } }
    }
}

@Composable
fun GoalEditRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: GoalEditViewModel = viewModel(factory = GoalEditViewModel.factory(c))
    val form by vm.form.collectAsState()
    GoalEditScreen(form, vm::setName, vm::setNotes, vm::setStart, vm::setDeadline, onBack, onSave = { vm.save { undo -> onBack(); SnackbarBus.show("Goal saved", "Undo", undo) } })
}

@Composable
fun GoalEditScreen(
    form: GoalForm, onName: (String) -> Unit, onNotes: (String) -> Unit, onStart: (LocalDate) -> Unit,
    onDeadline: (LocalDate) -> Unit, onBack: () -> Unit, onSave: () -> Unit, autoFocus: Boolean = true,
) {
    val colors = LhTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus && form.isNew) runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        LhTopBar(if (form.isNew) "New goal" else "Edit goal", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                LhTextField(form.name, onName, Modifier.fillMaxWidth().focusRequester(focus), label = "Name", error = form.nameError, maxLength = 60)
                Text("${form.name.length}/60", style = LhType.caption, color = colors.mutedForeground, modifier = Modifier.align(Alignment.End))
            }
            LhTextField(form.notes, onNotes, Modifier.fillMaxWidth(), label = "Notes", singleLine = false, minLines = 3)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LhDatePickerField(form.start, onStart, Modifier.fillMaxWidth(), label = "Start date")
                LhDatePickerField(form.deadline, onDeadline, Modifier.fillMaxWidth(), label = "Deadline", error = form.deadlineError)
            }
            Text(
                durationLabel(form.start, form.deadline) + if (form.deadlineIsAuto) "" else " · custom",
                style = LhType.caption, color = colors.mutedForeground,
            )
        }
        LhSaveBar(onSave, Modifier.testTag("save-bar"))
    }
}
