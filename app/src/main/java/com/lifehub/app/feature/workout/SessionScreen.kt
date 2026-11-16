package com.lifehub.app.feature.workout

import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.SnackbarBus
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhTopBar
import androidx.compose.foundation.layout.Column

/** F-WO-13: view/edit/delete a saved session. */
@Composable
fun SessionRoute(onBack: () -> Unit) {
    val vm: SessionEditViewModel = viewModel(factory = SessionEditViewModel.factory(LocalContext.current.container, manual = false))
    val ui by vm.ui.collectAsState()
    if (!ui.loaded) return
    if (ui.notFound) {
        Column { LhTopBar("Session", onBack = onBack); LhEmptyState("Session not found", icon = androidx.compose.material.icons.Icons.Outlined.SearchOff) }
        return
    }
    SessionEditScreen(
        ui, onBack, onSave = { vm.save { onBack(); SnackbarBus.show("Session saved", "Undo") { vm.undoEdit() } } }, onDelete = { vm.delete(onBack) },
        onStart = vm::setStart, onDuration = vm::setDuration, onRpe = vm::setRpe,
        onNotes = vm::setNotes, onRow = vm::setRow,
    )
}
