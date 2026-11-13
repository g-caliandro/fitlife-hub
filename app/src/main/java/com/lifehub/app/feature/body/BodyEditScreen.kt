package com.lifehub.app.feature.body

import com.lifehub.app.core.designsystem.components.LhCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhDatePickerField
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhNumberField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.core.designsystem.fmtDateFull
import com.lifehub.app.core.designsystem.fmtKg
import com.lifehub.app.core.designsystem.SnackbarBus
import com.lifehub.app.core.designsystem.components.LhSaveBar
import com.lifehub.app.core.designsystem.components.LhStepper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import java.time.LocalDate

@Composable
fun BodyError?.text(): String? = when (this) {
    null -> null
    BodyError.WeightRange -> stringResource(R.string.body_err_weight)
    BodyError.MuscleExceeds, BodyError.FatExceeds -> stringResource(R.string.body_err_exceed)
    BodyError.SumExceeds -> stringResource(R.string.body_err_sum)
}

@Composable
fun BodyEditRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: BodyEditViewModel = viewModel(factory = BodyEditViewModel.factory(c))
    val state by vm.state.collectAsState()
    val savedMsg = stringResource(R.string.body_saved)
    val deletedMsg = stringResource(R.string.body_deleted)
    BodyEditScreen(
        state, onBack, vm::setDate, vm::setWeight, vm::setMuscle, vm::setFat,
        onSave = { vm.save { undo -> onBack(); SnackbarBus.show(savedMsg, "Undo", undo) } },
        onDelete = { vm.delete { undo -> onBack(); SnackbarBus.show(deletedMsg, "Undo", undo) } },
    )
}

@Composable
fun BodyEditScreen(
    state: BodyEditState, onBack: () -> Unit, onDate: (LocalDate) -> Unit,
    onWeight: (String) -> Unit, onMuscle: (String) -> Unit, onFat: (String) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, autoFocus: Boolean = true,
) {
    val c = LhTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val live = validateBody(state.weight, state.muscle, state.fat)
    val v = state.validation
    val pct = liveFatPct(live.weight, live.fat)
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar(stringResource(if (state.existing) R.string.body_title_edit else R.string.body_title_log), onBack = onBack, actions = {
            if (state.existing) {
                Box {
                    LhIconButton(Icons.Outlined.MoreVert, stringResource(R.string.body_more), { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; confirm = true })
                    }
                }
            }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          LhCard(Modifier.fillMaxWidth()) {
           Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LhDatePickerField(state.date, onDate, label = stringResource(R.string.body_date), maxDate = state.today)
            LhStepper(
                state.weight, onWeight, 0.1, Modifier.fillMaxWidth().testTag("body-weight"), label = stringResource(R.string.body_weight),
                suffix = "kg", decimals = 1, min = 20.0, max = 300.0, placeholder = "0.0",
            )
            v?.weightError.text()?.let { Text(it, style = LhType.caption, color = c.destructive) }
            LhStepper(
                state.muscle, onMuscle, 0.1, Modifier.fillMaxWidth().testTag("body-muscle"), label = stringResource(R.string.body_muscle),
                suffix = "kg", decimals = 1, max = 300.0, placeholder = "0.0",
            )
            v?.muscleError.text()?.let { Text(it, style = LhType.caption, color = c.destructive) }
            LhStepper(
                state.fat, onFat, 0.1, Modifier.fillMaxWidth().testTag("body-fat"), label = stringResource(R.string.body_fat),
                suffix = "kg", decimals = 1, max = 300.0, placeholder = "0.0",
            )
            v?.fatError.text()?.let { Text(it, style = LhType.caption, color = c.destructive) }
            Text(
                if (pct == null) "Body fat " + stringResource(R.string.dash) else "Body fat · ${fmtKg(pct)} %",
                style = LhType.tnum(LhType.label), color = if (pct == null) c.mutedForeground else c.primary,
                modifier = Modifier.testTag("body-fat-pct")
                    .background(if (pct == null) c.muted else c.primaryContainer, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (state.lastDate != null && state.lastWeight != null) {
                Text(stringResource(R.string.body_last, fmtKg(state.lastWeight), fmtDate(state.lastDate, state.today.year)), style = LhType.captionMuted)
            }
           }
          }
        }
        LhSaveBar(onSave, Modifier.testTag("save-bar"))
    }
    if (confirm) LhConfirmDialog(
        stringResource(R.string.body_delete_title), stringResource(R.string.body_delete_msg, fmtDateFull(state.date)),
        stringResource(R.string.delete), onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false },
    )
}
