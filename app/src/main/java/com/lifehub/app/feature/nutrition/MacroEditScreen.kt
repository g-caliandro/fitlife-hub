package com.lifehub.app.feature.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.lifehub.app.core.designsystem.components.LhCard
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
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhDatePickerField
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhNumberField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtDateFull
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.core.designsystem.SnackbarBus
import com.lifehub.app.core.designsystem.components.LhSaveBar
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ContentCopy

@Composable
fun MacroEditRoute(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: MacroEditViewModel = viewModel(factory = MacroEditViewModel.factory(c))
    val state by vm.state.collectAsState()
    val savedMsg = stringResource(R.string.nut_saved)
    val deletedMsg = stringResource(R.string.nut_deleted)
    MacroEditScreen(
        state, onBack,
        onDate = vm::setDate, onProtein = vm::setProtein, onCarbs = vm::setCarbs, onFat = vm::setFat, onKcal = vm::setKcal,
        onCopy = vm::copyFromPrevious,
        onSave = { vm.save { undo -> onBack(); SnackbarBus.show(savedMsg, "Undo", undo) } },
        onDelete = { vm.delete { undo -> onBack(); SnackbarBus.show(deletedMsg, "Undo", undo) } },
    )
}

@Composable
fun MacroEditScreen(
    state: MacroEditState, onBack: () -> Unit,
    onDate: (java.time.LocalDate) -> Unit, onProtein: (String) -> Unit, onCarbs: (String) -> Unit,
    onFat: (String) -> Unit, onKcal: (String) -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
    autoFocus: Boolean = true, onCopy: () -> Unit = {},
) {
    val c = LhTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val m = parseMacros(state.protein, state.carbs, state.fat, state.kcal)
    val auto = macroAutoKcal(m)
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar(stringResource(if (state.existing) R.string.nut_title_edit else R.string.nut_title_log), onBack = onBack, actions = {
            if (state.existing) {
                Box {
                    LhIconButton(Icons.Outlined.MoreVert, stringResource(R.string.nut_more), { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; confirm = true })
                    }
                }
            }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LhCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LhDatePickerField(state.date, onDate, label = stringResource(R.string.nut_date), maxDate = state.today)
                    if (state.existing) Text(stringResource(R.string.nut_editing_existing), style = LhType.captionMuted)
                }
            }
            Text("Macros", style = LhType.heading, color = c.foreground)
            LhCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "≈ ${fmtInt(if (state.kcal.isBlank()) auto else state.kcal.toIntOrNull() ?: auto)} kcal", style = LhType.tnum(LhType.stat), color = c.foreground,
                        modifier = Modifier.testTag("macro-auto-chip"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LhNumberField(state.protein, { onProtein(it) }, Modifier.weight(1f).focusRequester(focus).testTag("macro-protein"), label = stringResource(R.string.nut_protein), suffix = "g")
                        LhNumberField(state.carbs, { onCarbs(it) }, Modifier.weight(1f).testTag("macro-carbs"), label = stringResource(R.string.nut_carbs), suffix = "g")
                        LhNumberField(state.fat, { onFat(it) }, Modifier.weight(1f).testTag("macro-fat"), label = stringResource(R.string.nut_fat), suffix = "g")
                    }
                    if (state.showError) Text(stringResource(R.string.nut_error_empty), style = LhType.caption, color = c.destructive)
                    LhNumberField(
                        state.kcal, onKcal, Modifier.fillMaxWidth().testTag("macro-kcal"), label = "Override kcal (optional)",
                        placeholder = "Auto", suffix = "kcal", imeAction = ImeAction.Done, onImeAction = onSave,
                    )
                    Text(
                        if (state.kcal.isBlank()) "Auto · 4·P + 4·C + 9·F" else "Manual · auto would be ${fmtInt(auto)}",
                        style = LhType.captionMuted,
                    )
                }
            }
            val reached = m.protein >= state.proteinTarget
            Text(
                stringResource(R.string.nut_target, state.proteinTarget, m.protein) + if (reached && m.protein > 0) " ✓" else "",
                style = LhType.caption, color = if (reached && m.protein > 0) c.success else c.mutedForeground,
            )
        }
        LhSaveBar(
            onSave, Modifier.testTag("save-bar"),
            secondary = state.copySource?.let { src ->
                {
                    LhButton(copyLabel(src.date, state.date), onCopy, Modifier.fillMaxWidth().testTag("macro-copy"), variant = LhButtonVariant.Tonal, leadingIcon = Icons.Outlined.ContentCopy)
                }
            },
        )
    }
    if (confirm) LhConfirmDialog(
        stringResource(R.string.nut_delete_title), stringResource(R.string.nut_delete_msg, fmtDateFull(state.date)),
        stringResource(R.string.delete), onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false },
    )
}
