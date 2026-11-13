package com.lifehub.app.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhSpacing
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.data.model.NotifType
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.navigation.LocalSnackbarHostState
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun SettingsRoute(onBack: () -> Unit, section: SettingsSection = SettingsSection.ALL) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val c = ctx.container
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app, c))
    val state by vm.ui.collectAsState()
    val snackbar = LocalSnackbarHostState.current

    fun granted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var blocked by remember { mutableStateOf(!granted()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { blocked = !granted() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) blocked = !granted() }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    // First open: ask once (non-blocking).
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !granted() && !c.settings.notificationPermissionAsked) {
            c.settings.notificationPermissionAsked = true
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is SettingsEvent.Snack -> snackbar.showSnackbar(e.message)
                is SettingsEvent.Share -> ctx.startActivity(ExportShare.intent(ctx, e.file, e.mime))
            }
        }
    }

    var pendingKind by remember { mutableStateOf(ExportKind.JSON) }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.JSON.mime)) { vm.saveTo(ExportKind.JSON, it) }
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.CSV.mime)) { vm.saveTo(ExportKind.CSV, it) }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.importFrom(it) }

    SettingsScreen(
        state = state,
        notificationsBlocked = blocked,
        onBack = onBack,
        onProteinTarget = vm::setProteinTarget,
        onKcalTarget = vm::setKcalTarget, onCarbsTarget = vm::setCarbsTarget, onFatTarget = vm::setFatTarget,
        section = section,
        onNotify = { type, on ->
            vm.setNotify(type, on)
            if (on && Build.VERSION.SDK_INT >= 33 && !granted()) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onTime = vm::setDailyCheckTime,
        onOpenSystemSettings = {
            ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onShare = vm::share,
        onSave = { k ->
            pendingKind = k
            val name = vm.fileNameFor(k)
            if (k == ExportKind.JSON) createJson.launch(name) else createZip.launch(name)
        },
        onImport = { openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onMock = vm::requestMock,
        onClear = vm::requestClear,
        onConfirmImport = vm::confirmImport, onDismissImport = vm::dismissImport,
        onConfirm = vm::confirmed, onDismissConfirm = vm::dismissConfirm,
    )
}

enum class SettingsSection(val title: String) { ALL("Settings"), TARGETS("Targets"), NOTIFICATIONS("Notifications"), DATA("Data") }

internal object ExportShare {
    fun intent(ctx: android.content.Context, file: java.io.File, mime: String): Intent =
        com.lifehub.app.export.ExportFiles.shareIntent(ctx, file, mime)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    notificationsBlocked: Boolean,
    onBack: () -> Unit,
    onProteinTarget: (Int) -> Unit,
    onNotify: (NotifType, Boolean) -> Unit,
    onTime: (LocalTime) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onShare: (ExportKind) -> Unit,
    onSave: (ExportKind) -> Unit,
    onImport: () -> Unit,
    onMock: () -> Unit,
    onClear: () -> Unit,
    onConfirmImport: () -> Unit,
    onDismissImport: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onKcalTarget: (Int?) -> Unit = {},
    onCarbsTarget: (Int?) -> Unit = {},
    onFatTarget: (Int?) -> Unit = {},
    section: SettingsSection = SettingsSection.ALL,
) {
    val s = state.settings
    val busy = state.busy != null
    val all = section == SettingsSection.ALL
    Column(Modifier.fillMaxSize()) {
        LhTopBar(section.title, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (all || section == SettingsSection.TARGETS) {
                if (all) LhSectionHeader("Nutrition")
                LhGroupCard {
                    TargetRow("Protein", "g", s.proteinTargetG, Icons.Outlined.TrackChanges, LhTheme.colors.protein, nullable = false, onChange = { onProteinTarget(it ?: 0) }, divider = true)
                    TargetRow("Calories", "kcal", s.kcalTarget, Icons.Outlined.LocalFireDepartment, LhTheme.colors.kcal, nullable = true, onChange = onKcalTarget, divider = true)
                    TargetRow("Carbs", "g", s.carbsTargetG, Icons.Outlined.Grain, LhTheme.colors.carbs, nullable = true, onChange = onCarbsTarget, divider = true)
                    TargetRow("Fat", "g", s.fatTargetG, Icons.Outlined.WaterDrop, LhTheme.colors.fat, nullable = true, onChange = onFatTarget, divider = false)
                }
                Text("The kcal ring shows kcal left when a kcal target is set.", style = LhType.captionMuted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }

            if (all || section == SettingsSection.NOTIFICATIONS) {
                if (all) LhSectionHeader("Notifications")
                if (notificationsBlocked) {
                    Row(Modifier.fillMaxWidth().padding(vertical = LhSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications are blocked.", style = LhType.captionMuted, color = LhTheme.colors.destructive, modifier = Modifier.weight(1f))
                        LhButton("Open settings", onOpenSystemSettings, variant = LhButtonVariant.Tonal, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.Settings)
                    }
                }
                LhGroupCard {
                    LhSwitchRow("Training reminders", s.notifyTrain, { onNotify(NotifType.TRAIN, it) }, subtitle = "On plan days, or after 3 days off")
                    LhSwitchRow("Protein check", s.notifyProtein, { onNotify(NotifType.PROTEIN, it) }, subtitle = "Mondays, if last week was low")
                    LhSwitchRow("Weigh-in reminder", s.notifyWeight, { onNotify(NotifType.WEIGHT, it) }, subtitle = "After 1 week without a weigh-in")
                    LhSwitchRow("Achievement memories", s.notifyAchievements, { onNotify(NotifType.ACHIEVEMENT_MEMORY, it) }, subtitle = "Unlocked achievements and anniversaries", showDivider = false)
                }
                Spacer(Modifier.height(12.dp))
                LhTimePickerField(s.dailyCheckTime, onTime, label = "Daily check time", modifier = Modifier.fillMaxWidth())
            }

            if (all || section == SettingsSection.DATA) {
                if (all) LhSectionHeader("Data")
                LhGroupCard {
                    ExportRow("Export JSON", state.busy == BusyOp.EXPORT_JSON, busy, { onShare(ExportKind.JSON) }, { onSave(ExportKind.JSON) })
                    ExportRow("Export CSV (zip)", state.busy == BusyOp.EXPORT_CSV, busy, { onShare(ExportKind.CSV) }, { onSave(ExportKind.CSV) })
                    ActionRow("Import JSON backup", Icons.Outlined.FileDownload, state.busy == BusyOp.IMPORT, if (busy) null else onImport, true)
                    ActionRow("Generate mock data", Icons.Outlined.Science, state.busy == BusyOp.MOCK, if (busy) null else onMock, true)
                    ActionRow("Delete all data", Icons.Outlined.DeleteForever, state.busy == BusyOp.CLEAR, if (busy) null else onClear, false, destructive = true)
                }
            }

            if (all) {
                LhSectionHeader("About")
                Text("Life Hub ${state.appVersion} · data schema v${com.lifehub.app.export.JsonCodec.SCHEMA_VERSION}", style = LhType.captionMuted, modifier = Modifier.padding(vertical = LhSpacing.sm))
            }
        }
    }

    state.importPreview?.let {
        LhConfirmDialog("Replace all data?", it.summary, "Replace", onConfirmImport, onDismissImport)
    }
    when (state.confirm) {
        Confirm.MOCK -> LhConfirmDialog("Generate mock data?", "Replace all data with ~10 weeks of sample data?", "Replace", onConfirm, onDismissConfirm)
        Confirm.CLEAR -> LhTypedConfirmDialog("Clear all data?", "This permanently deletes all goals, plans, workouts, macros and weigh-ins.", "DELETE", "Delete everything", onConfirm, onDismissConfirm)
        null -> {}
    }
}

@Composable
private fun TargetRow(
    title: String, unit: String, value: Int?, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: androidx.compose.ui.graphics.Color,
    nullable: Boolean, onChange: (Int?) -> Unit, divider: Boolean,
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    LaunchedEffect(text) {
        delay(500)
        val v = text.toIntOrNull()
        if (v != value && (v != null || (nullable && text.isEmpty()))) onChange(v)
    }
    LhListItem(
        title, standalone = false, showDivider = divider, icon = icon, iconTint = tone,
        trailing = { LhNumberField(text, { text = it }, suffix = unit, placeholder = if (nullable) "None" else null, modifier = Modifier.size(width = 128.dp, height = 56.dp)) },
    )
}

@Composable
private fun ActionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, spinning: Boolean, onClick: (() -> Unit)?, divider: Boolean, destructive: Boolean = false) {
    val tone = if (destructive) LhTheme.colors.destructive else LhTheme.colors.primary
    LhListItem(
        title, standalone = false, showDivider = divider, icon = icon, iconTint = tone, onClick = onClick,
        trailing = { if (spinning) Spinner(true) else androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LhTheme.colors.mutedForeground) },
    )
}

@Composable
private fun Spinner(show: Boolean) {
    if (show) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LhTheme.colors.foreground)
}

@Composable
private fun ExportRow(title: String, spinning: Boolean, disabled: Boolean, onShare: () -> Unit, onSave: () -> Unit) {
    LhListItem(title, standalone = false, showDivider = true, icon = Icons.Outlined.FileUpload, trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner(spinning)
            LhIconButton(Icons.Outlined.Share, "Share", onShare, variant = LhButtonVariant.Tonal, enabled = !disabled)
            LhIconButton(Icons.Outlined.FileDownload, "Save to device", onSave, variant = LhButtonVariant.Tonal, enabled = !disabled)
        }
    })
}
