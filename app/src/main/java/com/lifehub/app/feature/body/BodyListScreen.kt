package com.lifehub.app.feature.body

import com.lifehub.app.core.designsystem.components.LhListItem
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhListRow
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.core.designsystem.fmtDateFull
import com.lifehub.app.core.designsystem.fmtKg
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.domain.fatPct
import com.lifehub.app.navigation.Routes
import java.time.LocalDate

@Composable
fun BodyListRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: BodyListViewModel = viewModel(factory = BodyListViewModel.factory(c))
    val entries by vm.entries.collectAsState()
    BodyListScreen(entries.orEmpty(), vm.today, onBack, onNavigate, vm::delete)
}

fun bodySubtitle(e: BodyEntry, currentYear: Int): String = buildList {
    add(fmtDate(e.date, currentYear))
    e.muscleKg?.let { add("M ${fmtKg(it)}") }
    e.fatKg?.let { add("F ${fmtKg(it)} (${fmtKg(fatPct(e))}%)") }
}.joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BodyListScreen(
    entries: List<BodyEntry>, today: LocalDate, onBack: () -> Unit,
    onNavigate: (String) -> Unit, onDelete: (LocalDate) -> Unit,
) {
    var toDelete by remember { mutableStateOf<LocalDate?>(null) }
    Column(Modifier.fillMaxSize().background(LhTheme.colors.background)) {
        LhTopBar(stringResource(R.string.body_title_list), onBack = onBack, actions = {
            LhIconButton(Icons.Outlined.Add, stringResource(R.string.body_add), { onNavigate(Routes.bodyEdit(today)) })
        })
        if (entries.isEmpty()) {
            LhEmptyState(
                stringResource(R.string.body_empty), icon = androidx.compose.material.icons.Icons.Outlined.MonitorWeight, actionIcon = androidx.compose.material.icons.Icons.Outlined.Add, actionText = stringResource(R.string.cal_log_weight),
                onAction = { onNavigate(Routes.bodyEdit(today)) },
            )
        } else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.date.toEpochDay() }) { e ->
                LhListItem(
                    title = "${fmtKg(e.weightKg)} kg", subtitle = bodySubtitle(e, today.year),
                    icon = Icons.Outlined.MonitorWeight, iconTint = LhTheme.colors.carbs,
                    modifier = Modifier.combinedClickable(onClick = { onNavigate(Routes.bodyEdit(e.date)) }, onLongClick = { toDelete = e.date }),
                )
            }
        }
    }
    toDelete?.let { d ->
        LhConfirmDialog(
            stringResource(R.string.body_delete_title), stringResource(R.string.body_delete_msg, fmtDateFull(d)),
            stringResource(R.string.delete), onConfirm = { onDelete(d); toDelete = null }, onDismiss = { toDelete = null },
        )
    }
}
