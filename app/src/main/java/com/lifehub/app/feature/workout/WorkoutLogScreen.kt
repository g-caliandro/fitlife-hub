package com.lifehub.app.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.SnackbarBus

/** F-WO-8: manual/backdated workout. Plan list first, then the shared edit screen with sets prefilled DONE. */
@Composable
fun WorkoutLogRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SessionEditViewModel = viewModel(factory = SessionEditViewModel.factory(context.container, manual = true))
    val ui by vm.ui.collectAsState()
    if (!ui.loaded) return
    if (ui.needsPlan) {
        Column(Modifier.fillMaxSize()) {
            LhTopBar("Log workout", onBack = onBack)
            PlanPickList(ui.plans, null, "Select", vm::pickPlan, onCreatePlan = onBack)
        }
        return
    }
    SessionEditScreen(
        ui, onBack,
        onSave = { vm.save { onBack(); SnackbarBus.show("Workout saved", "Undo") { vm.undoManual() } } },
        onDelete = null,
        onStart = vm::setStart, onDuration = vm::setDuration, onRpe = vm::setRpe,
        onNotes = vm::setNotes, onRow = vm::setRow,
    )
}
