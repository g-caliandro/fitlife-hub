package com.lifehub.app.feature.workout

import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.di.AppContainer
import com.lifehub.app.data.repo.ActiveSessionExistsException
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the plan list (start screen and manual-log plan picker). */
data class PlanPickRow(
    val id: String, val name: String, val weekdays: Set<Int>, val exerciseCount: Int, val setCount: Int, val today: Boolean,
    val dayType: com.lifehub.app.data.model.DayType? = null,
)

data class StartUi(
    val loaded: Boolean = false,
    val plans: List<PlanPickRow> = emptyList(),
    val preselect: String? = null,
    val inProgress: Boolean = false,
    val conflictPlanId: String? = null,
    val conflictPlanName: String = "",
)

internal fun planRows(plans: List<PlanWithExercises>, todayIso: Int): List<PlanPickRow> =
    plans.filter { !it.plan.isArchived }.map {
        PlanPickRow(
            it.plan.id, it.plan.name, it.plan.weekdays, it.exercises.size, it.exercises.sumOf { e -> e.planExercise.sets },
            todayIso in it.plan.weekdays, it.plan.dayType,
        )
    }.sortedWith(compareBy({ !it.today }, { it.name.lowercase() }))

class WorkoutStartViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val preselect: String? = handle.get<String>("planId")
    private val conflict = MutableStateFlow<String?>(null)
    private val _navigate = MutableStateFlow<String?>(null)
    val navigate: StateFlow<String?> = _navigate

    val ui: StateFlow<StartUi> = combine(c.plans.observePlans(), c.sessions.observeInProgress(), conflict) { plans, ip, cf ->
        val rows = planRows(plans, c.time.today().dayOfWeek.value)
        StartUi(true, rows, preselect, ip != null, cf, rows.firstOrNull { it.id == cf }?.name.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StartUi())

    val plan: StateFlow<PlanWithExercises?> =
        (preselect?.let { c.plans.observePlan(it) } ?: kotlinx.coroutines.flow.flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun start(planId: String) {
        viewModelScope.launch {
            try {
                c.sessions.startFromPlan(planId, c.time.now(), c.time.today())
                _navigate.value = Routes.replace(Routes.WORKOUT_LIVE)
            } catch (e: ActiveSessionExistsException) {
                conflict.value = planId
            }
        }
    }

    fun resume() { conflict.value = null; _navigate.value = Routes.replace(Routes.WORKOUT_LIVE) }
    fun dismissConflict() { conflict.value = null }

    fun discardAndStart() {
        val id = conflict.value ?: return
        viewModelScope.launch {
            c.sessions.observeInProgressOnce()?.let { c.sessions.discard(it) }
            conflict.value = null
            start(id)
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { WorkoutStartViewModel(c, createSavedStateHandle()) } }
    }
}

private suspend fun com.lifehub.app.data.repo.SessionRepository.observeInProgressOnce(): String? =
    observeInProgress().first()?.session?.id


@Composable
fun WorkoutStartRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: WorkoutStartViewModel = viewModel(factory = WorkoutStartViewModel.factory(context.container))
    val ui by vm.ui.collectAsState()
    val nav by vm.navigate.collectAsState()
    // IN_PROGRESS on entry => straight to the live screen.
    LaunchedEffect(ui.loaded, ui.inProgress) {
        if (ui.loaded && ui.inProgress && ui.conflictPlanId == null && nav == null) onNavigate(Routes.replace(Routes.WORKOUT_LIVE))
    }
    LaunchedEffect(nav) { nav?.let { onNavigate(it) } }
    WorkoutStartScreen(
        ui, onBack,
        onStart = { id -> onNavigate(Routes.workoutPreview(id)) },
        onConflictResume = vm::resume, onConflictDiscard = vm::discardAndStart, onConflictDismiss = vm::dismissConflict,
        onCreatePlan = { onNavigate(Routes.planEdit()) },
    )
}

/** Returns a function that starts [planId], asking POST_NOTIFICATIONS first on the very first workout. */
@Composable
internal fun rememberStarter(vm: WorkoutStartViewModel): (String) -> Unit {
    val context = LocalContext.current
    var pendingPlan by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPlan?.let { vm.start(it) }
        pendingPlan = null
    }
    return { id ->
        if (shouldAskNotifications(context)) {
            pendingPlan = id
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.start(id)
    }
}

/** First workout only: ask POST_NOTIFICATIONS once (API 33+). */
private fun shouldAskNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false
    val prefs = context.getSharedPreferences("lifehub_workout", Context.MODE_PRIVATE)
    if (prefs.getBoolean("notifAsked", false)) return false
    prefs.edit().putBoolean("notifAsked", true).apply()
    return true
}

@Composable
fun WorkoutStartScreen(
    ui: StartUi, onBack: () -> Unit, onStart: (String) -> Unit,
    onConflictResume: () -> Unit, onConflictDiscard: () -> Unit, onConflictDismiss: () -> Unit, onCreatePlan: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LhTopBar("Start workout", onBack = onBack)
        PlanPickList(ui.plans, ui.preselect, "Start", onStart, onCreatePlan)
    }
    if (ui.conflictPlanId != null) StartConflictDialog(ui.conflictPlanName, onConflictResume, onConflictDiscard, onConflictDismiss)
}

@Composable
internal fun StartConflictDialog(planName: String, onResume: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    LhDialogContainer(onDismiss) {
        Text("Workout in progress", style = LhType.heading, color = LhTheme.colors.foreground)
        Text(
            "Resume it or discard it and start $planName?", style = LhType.body,
            color = LhTheme.colors.mutedForeground, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            LhButton("Resume", onResume, variant = LhButtonVariant.Outline)
            LhButton("Discard & start", onDiscard, variant = LhButtonVariant.Destructive)
        }
    }
}

/** Plan list shared by Start and manual Log (button text differs). Today's plans first. */
@Composable
fun PlanPickList(
    plans: List<PlanPickRow>, preselect: String?, buttonText: String, onPick: (String) -> Unit, onCreatePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LhTheme.colors
    if (plans.isEmpty()) {
        LhEmptyState("No plans", description = "Create a plan to start a workout.", icon = androidx.compose.material.icons.Icons.Outlined.FitnessCenter, actionText = "Create plan", onAction = onCreatePlan, actionIcon = androidx.compose.material.icons.Icons.Outlined.Add)
        return
    }
    val today = plans.filter { it.today }
    val other = plans.filter { !it.today }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(today, key = { it.id }) { PlanPickCard(it, it.id == preselect, it == today.first(), buttonText, onPick) }
        if (other.isNotEmpty()) {
            item { Text(if (today.isEmpty()) "Plans" else "Other plans", style = LhType.heading, color = c.foreground, modifier = Modifier.padding(top = 8.dp)) }
            items(other, key = { it.id }) { PlanPickCard(it, it.id == preselect, today.isEmpty() && it == other.first(), buttonText, onPick) }
        }
    }
}

@Composable
private fun PlanPickCard(row: PlanPickRow, selected: Boolean, primary: Boolean, buttonText: String, onPick: (String) -> Unit) {
    val c = LhTheme.colors
    val shape = LhCardShape
    Column(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.border(1.5.dp, c.accent, shape) else Modifier)
    ) {
        LhCard(Modifier.fillMaxWidth(), onClick = { onPick(row.id) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (row.dayType != null) { com.lifehub.app.core.designsystem.DayTypeIcon(row.dayType, size = 20.dp); androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 8.dp)) }
                Text(row.name, style = LhType.heading, color = c.foreground, modifier = Modifier.weight(1f))
                if (row.today) LhBadge("Today", variant = LhBadgeVariant.Accent)
            }
            val days = row.weekdays.sorted().joinToString(" · ") { java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) }
            Text(
                listOfNotNull(days.ifEmpty { null }, "${row.exerciseCount} exercises · ${row.setCount} sets").joinToString("  ·  "),
                style = LhType.captionMuted, modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                LhButton(
                    buttonText, { onPick(row.id) },
                    variant = if (primary) LhButtonVariant.Primary else LhButtonVariant.Tonal,
                    size = LhButtonSize.Md, leadingIcon = if (buttonText == "Start") Icons.Outlined.PlayArrow else null,
                )
            }
        }
    }
}

private val LhCardShape = com.lifehub.app.core.designsystem.LhRadius.lgShape
