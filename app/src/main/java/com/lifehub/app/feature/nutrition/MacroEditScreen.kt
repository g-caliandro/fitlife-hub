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
import com.lifehub.app.core.designsystem.components.LhTextField
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
        onCopy = vm::copyFromPrevious, onName = vm::setName,
        onSave = { vm.save { undo -> onBack(); SnackbarBus.show(savedMsg, "Undo", undo) } },
        onDelete = { vm.delete { undo -> onBack(); SnackbarBus.show(deletedMsg, "Undo", undo) } },
    )
}

/**
 * Quick add: calories (required) + optional name and protein/carbs/fat. A blank macro is unknown ("n/a"), not 0.
 * kcal may stay blank only when all three macros are given (then 4·P + 4·C + 9·F).
 */
@Composable
fun MacroEditScreen(
    state: MacroEditState, onBack: () -> Unit,
    onDate: (java.time.LocalDate) -> Unit, onProtein: (String) -> Unit, onCarbs: (String) -> Unit,
    onFat: (String) -> Unit, onKcal: (String) -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
    autoFocus: Boolean = true, onCopy: () -> Unit = {}, onName: (String) -> Unit = {},
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
                    LhIconButton(Icons.Outlined.MoreVert, stringResource(R.string.nut_more), { menu = true }, Modifier.testTag("macro-more"))
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; confirm = true }, modifier = Modifier.testTag("macro-delete"))
                    }
                }
            }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LhCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LhDatePickerField(state.date, onDate, label = stringResource(R.string.nut_date), maxDate = state.today)
                    LhTextField(
                        state.name, onName, Modifier.fillMaxWidth().testTag("macro-name"), label = "Name (optional)",
                        placeholder = "e.g. Pizza out", maxLength = 40,
                    )
                }
            }
            LhCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LhNumberField(
                        state.kcal, onKcal, Modifier.fillMaxWidth().focusRequester(focus).testTag("macro-kcal"), label = stringResource(R.string.nut_kcal_label),
                        placeholder = if (auto != null) "Auto ${fmtInt(auto)}" else "Required", suffix = "kcal",
                        error = if (state.showError) stringResource(R.string.nut_error_empty) else null,
                    )
                    val hint = when {
                        state.kcal.isNotBlank() && auto != null -> "Manual · from macros it would be ${fmtInt(auto)}"
                        state.kcal.isBlank() && auto != null -> "Auto · 4·P + 4·C + 9·F"
                        else -> null
                    }
                    if (hint != null && !state.showError) Text(hint, style = LhType.captionMuted, modifier = Modifier.testTag("macro-auto-chip"))
                    Text("Macros (optional)", style = LhType.label, color = c.foreground, modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LhNumberField(state.protein, { onProtein(it) }, Modifier.weight(1f).testTag("macro-protein"), label = stringResource(R.string.nut_protein), placeholder = "n/a", suffix = "g")
                        LhNumberField(state.carbs, { onCarbs(it) }, Modifier.weight(1f).testTag("macro-carbs"), label = stringResource(R.string.nut_carbs), placeholder = "n/a", suffix = "g")
                        LhNumberField(
                            state.fat, { onFat(it) }, Modifier.weight(1f).testTag("macro-fat"), label = stringResource(R.string.nut_fat), placeholder = "n/a", suffix = "g",
                            imeAction = ImeAction.Done, onImeAction = onSave,
                        )
                    }
                    Text("Leave empty if unknown: shown as n/a, not counted as 0.", style = LhType.captionMuted)
                }
            }
            m.protein?.let { p ->
                val reached = p >= state.proteinTarget
                Text(
                    stringResource(R.string.nut_target, state.proteinTarget, p) + if (reached && p > 0) " ✓" else "",
                    style = LhType.caption, color = if (reached && p > 0) c.success else c.mutedForeground,
                )
            }
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
        stringResource(R.string.nut_delete_title), stringResource(R.string.nut_delete_msg, state.name.trim().ifEmpty { "Quick add" }, fmtDateFull(state.date)),
        stringResource(R.string.delete), onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false },
    )
}
