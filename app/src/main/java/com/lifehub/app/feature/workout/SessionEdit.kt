package com.lifehub.app.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import com.lifehub.app.core.designsystem.ExerciseIcon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.designsystem.fmtTarget
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SessionStatus
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.data.model.WorkoutSession
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class EditRow(val set: SessionSet, val weight: String, val actual: String, val checked: Boolean)

data class SessionEditUi(
    val loaded: Boolean = false,
    val manual: Boolean = false,
    val needsPlan: Boolean = false,
    val plans: List<PlanPickRow> = emptyList(),
    val title: String = "",
    val dayType: com.lifehub.app.data.model.DayType? = null,
    val date: LocalDate = LocalDate.of(2026, 1, 1),
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    val startTime: LocalTime = LocalTime.of(18, 0),
    val durationMin: String = "",
    val rpe: Int? = null,
    val notes: String = "",
    val rows: List<EditRow> = emptyList(),
    val notFound: Boolean = false,
)

private fun SessionSet.toRow(): EditRow = EditRow(
    this,
    weight = weightKg?.let { if (it % 1.0 == 0.0) it.toLong().toString() else String.format(java.util.Locale.US, "%.1f", it) }.orEmpty(),
    actual = (if (targetType == TargetType.SECONDS) actualSeconds else actualReps)?.toString().orEmpty(),
    checked = status == SetStatus.DONE,
)

/** Session detail (sessionId arg) and manual log (date arg, plan picked first) share this VM. */
class SessionEditViewModel(private val c: AppContainer, handle: SavedStateHandle, private val manual: Boolean) : ViewModel() {
    private val sessionId: String? = handle.get<String>("sessionId")
    private val argDate: Long = handle.get<Long>("date") ?: -1L
    private var original: WorkoutSession? = null
    private var draftId: String = UUID.randomUUID().toString()
    private var planId: String? = null
    private var planName: String = ""

    private val _ui = MutableStateFlow(SessionEditUi(manual = manual))
    val ui: StateFlow<SessionEditUi> = _ui

    init {
        val today = c.time.today()
        viewModelScope.launch {
            if (manual) {
                val date = if (argDate >= 0) LocalDate.ofEpochDay(argDate).coerceAtMost(today) else today
                val plans = planRows(c.plans.observePlans().first(), date.dayOfWeek.value)
                _ui.value = SessionEditUi(
                    true, manual = true, needsPlan = true, plans = plans, title = "Log workout", date = date, today = today,
                    startTime = c.time.now().atZone(c.time.zone).toLocalTime().withSecond(0).withNano(0),
                )
            } else {
                val s = sessionId?.let { c.sessions.observeSession(it).first() }
                if (s == null) { _ui.value = SessionEditUi(true, notFound = true); return@launch }
                original = s.session
                val z = s.session.startedAt.atZone(c.time.zone)
                _ui.value = SessionEditUi(
                    true, title = s.session.planName, dayType = s.session.planId?.let { c.plans.get(it) }?.plan?.dayType, date = s.session.date, today = today, startTime = z.toLocalTime().withSecond(0).withNano(0),
                    durationMin = s.session.durationSec?.let { (it / 60).toString() }.orEmpty(), rpe = s.session.rpe,
                    notes = s.session.notes.orEmpty(), rows = WorkoutOps.ordered(s.sets).map { it.toRow() },
                )
            }
        }
    }

    fun pickPlan(id: String) {
        viewModelScope.launch {
            val plan = c.plans.get(id) ?: return@launch
            planId = plan.plan.id; planName = plan.plan.name
            val sets = WorkoutOps.manualSets(draftId, plan, c.time.now())
            _ui.value = _ui.value.copy(needsPlan = false, title = plan.plan.name, dayType = plan.plan.dayType, rows = sets.map { it.toRow() })
        }
    }

    private fun update(f: (SessionEditUi) -> SessionEditUi) { _ui.value = f(_ui.value) }
    fun setDate(d: LocalDate) = update { it.copy(date = d) }
    fun setStart(t: LocalTime) = update { it.copy(startTime = t) }
    fun setDuration(v: String) = update { it.copy(durationMin = v) }
    fun setRpe(v: Int?) = update { it.copy(rpe = v) }
    fun setNotes(v: String) = update { it.copy(notes = v) }
    fun setRow(i: Int, f: (EditRow) -> EditRow) = update { u -> u.copy(rows = u.rows.mapIndexed { j, r -> if (i == j) f(r) else r }) }

    fun save(onDone: () -> Unit) {
        val u = _ui.value
        if (u.date.isAfter(u.today)) return
        val now = c.time.now()
        val sets = u.rows.map { r -> WorkoutOps.applyEdit(r.set, r.weight, r.actual, r.checked, now) }
        val dur = u.durationMin.toIntOrNull()?.takeIf { it > 0 }?.times(60)
        val session = if (manual) {
            val start = u.date.atTime(u.startTime).atZone(c.time.zone).toInstant()
            WorkoutSession(
                id = draftId, planId = planId, planName = planName, date = u.date, startedAt = start,
                endedAt = dur?.let { start.plusSeconds(it.toLong()) }, durationSec = dur, status = SessionStatus.COMPLETED,
                rpe = u.rpe, notes = u.notes.trim().ifEmpty { null }, isManual = true, currentSetIndex = 0, restEndsAt = null,
            )
        } else {
            val o = original ?: return
            o.copy(
                date = u.date, durationSec = dur, endedAt = dur?.let { o.startedAt.plusSeconds(it.toLong()) } ?: o.endedAt,
                rpe = u.rpe, notes = u.notes.trim().ifEmpty { null },
            )
        }
        viewModelScope.launch {
            if (!manual) prevSets = c.sessions.observeSession(session.id).first()?.sets
            c.sessions.saveFull(session, sets)
            try {
                if (!manual) c.achievements.resetSession(session.id)
                c.achievements.onSessionFinished(session.id, now)
            } catch (_: Exception) {}
            onDone()
        }
    }

    private var prevSets: List<SessionSet>? = null

    /** Undo for a manual log: remove the session that was just created. */
    fun undoManual() { val id = draftId; c.appScope.launch { c.sessions.delete(id) } }

    /** Undo for an edit of an existing session: put the original session and sets back. */
    fun undoEdit() {
        val o = original ?: return
        val sets = prevSets ?: return
        c.appScope.launch {
            c.sessions.saveFull(o, sets)
            try { c.achievements.resetSession(o.id); c.achievements.onSessionFinished(o.id, c.time.now()) } catch (_: Exception) {}
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = original?.id ?: return
        viewModelScope.launch { c.sessions.delete(id); onDone() }
    }

    companion object {
        fun factory(c: AppContainer, manual: Boolean) =
            viewModelFactory { initializer { SessionEditViewModel(c, createSavedStateHandle(), manual) } }
    }
}

@Composable
fun SessionEditScreen(
    ui: SessionEditUi, onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?,
    onDate: (LocalDate) -> Unit, onStart: (LocalTime) -> Unit, onDuration: (String) -> Unit, onRpe: (Int?) -> Unit,
    onNotes: (String) -> Unit, onRow: (Int, (EditRow) -> EditRow) -> Unit,
) {
    val c = LhTheme.colors
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LhTopBar(ui.title, onBack = onBack, titleIcon = ui.dayType) {
            if (onDelete != null) Box {
                LhIconButton(Icons.Outlined.MoreVert, "More", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                    DropdownMenuItem(text = { Text("Delete", color = c.destructive) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LhCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LhDatePickerField(ui.date, onDate, Modifier.fillMaxWidth(), label = "Date", maxDate = ui.today)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        if (ui.manual) LhTimePickerField(ui.startTime, onStart, Modifier.weight(1f), label = "Start")
                        else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Start", style = LhType.label, color = c.foreground)
                            Text(ui.startTime.toString(), style = LhType.bodyLarge, color = c.mutedForeground, modifier = Modifier.padding(vertical = 10.dp))
                        }
                        LhNumberField(ui.durationMin, onDuration, Modifier.weight(1f), label = "Duration", suffix = "min")
                    }
                    Text("RPE (optional)", style = LhType.label, color = c.foreground)
                    RpeChips(ui.rpe, onRpe)
                    LhTextField(ui.notes, onNotes, label = "Notes", singleLine = false, minLines = 2)
                }
            }
            if (ui.manual) Text("Empty actual = skipped", style = LhType.captionMuted)
            ui.rows.withIndex().groupBy { it.value.set.exerciseOrder }.toSortedMap().forEach { (_, g) ->
                LhCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { ExerciseIcon(g.first().value.set.exerciseName, 32.dp, dayType = ui.dayType) }
                        Text(g.first().value.set.exerciseName, style = LhType.heading, color = c.foreground)
                    }
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        g.forEach { (i, row) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${row.set.setIndex + 1}", style = LhType.tnum(LhType.label), color = c.foreground, modifier = Modifier.width(20.dp))
                                Text(fmtTarget(row.set.targetType, row.set.targetValue), style = LhType.tnum(LhType.body), color = c.mutedForeground, modifier = Modifier.width(44.dp))
                                LhNumberField(row.weight, { v -> onRow(i) { it.copy(weight = v) } }, Modifier.weight(1f), suffix = "kg", decimals = 1, placeholder = "–")
                                LhNumberField(
                                    row.actual, { v -> onRow(i) { it.copy(actual = v) } }, Modifier.weight(1f),
                                    suffix = if (row.set.targetType == TargetType.SECONDS) "s" else null,
                                    placeholder = if (row.set.targetType == TargetType.SECONDS) "sec" else "reps",
                                )
                                Checkbox(
                                    row.checked, { v -> onRow(i) { it.copy(checked = v) } },
                                    colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.input, checkmarkColor = c.primaryForeground),
                                )
                            }
                        }
                    }
                }
            }
        }
        LhSaveBar(onSave, Modifier.testTag("save-bar"), enabled = !ui.date.isAfter(ui.today))
    }
    if (confirmDelete && onDelete != null) {
        LhConfirmDialog("Delete session?", "This session and its sets will be deleted.", "Delete",
            onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}
