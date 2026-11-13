package com.lifehub.app.feature.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.NotifType
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.data.repo.DataSnapshot
import com.lifehub.app.di.AppContainer
import com.lifehub.app.export.CsvExporter
import com.lifehub.app.export.ExportFiles
import com.lifehub.app.export.ImportException
import com.lifehub.app.export.JsonCodec
import com.lifehub.app.mock.MockDataGenerator
import com.lifehub.app.notifications.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import java.time.ZonedDateTime

enum class BusyOp { EXPORT_JSON, EXPORT_CSV, IMPORT, MOCK, CLEAR }
enum class ExportKind(val mime: String, val ext: String) {
    JSON(ExportFiles.MIME_JSON, "json"), CSV(ExportFiles.MIME_ZIP, "zip")
}
enum class Confirm { MOCK, CLEAR }

data class ImportPreview(val snapshot: DataSnapshot) {
    val summary: String
        get() = "${snapshot.goals.size} goals, ${snapshot.plans.size} plans, ${snapshot.sessions.size} sessions, " +
            "${snapshot.macros.size} macro days, ${snapshot.bodyEntries.size} weigh-ins."
}

data class SettingsUiState(
    val settings: Settings = Settings(),
    val busy: BusyOp? = null,
    val importPreview: ImportPreview? = null,
    val confirm: Confirm? = null,
    val appVersion: String = "",
)

sealed interface SettingsEvent {
    data class Snack(val message: String) : SettingsEvent
    data class Share(val file: File, val mime: String) : SettingsEvent
}

class SettingsViewModel(private val app: Application, private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState(settings = c.settings.current(), appVersion = version()))
    val ui: StateFlow<SettingsUiState> = _ui
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { c.settings.settings.collect { s -> _ui.value = _ui.value.copy(settings = s) } }
    }

    private fun version(): String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    private fun snack(m: String) { _events.trySend(SettingsEvent.Snack(m)) }
    private fun busy(op: BusyOp?) { _ui.value = _ui.value.copy(busy = op) }

    fun setProteinTarget(g: Int) = c.settings.setProteinTarget(g)

    fun setKcalTarget(v: Int?) = c.settings.setKcalTarget(v)
    fun setCarbsTarget(v: Int?) = c.settings.setCarbsTarget(v)
    fun setFatTarget(v: Int?) = c.settings.setFatTarget(v)

    fun setNotify(type: NotifType, on: Boolean) = c.settings.setNotify(type, on)

    fun setDailyCheckTime(t: LocalTime) {
        c.settings.setDailyCheckTime(t)
        NotificationScheduler.schedule(app, t, replace = true)
    }

    private fun now(): ZonedDateTime = c.time.now().atZone(c.time.zone)

    private suspend fun writeExport(kind: ExportKind, out: java.io.OutputStream) {
        val snap = c.backup.snapshot()
        val at = now()
        withContext(Dispatchers.IO) {
            when (kind) {
                ExportKind.JSON -> out.write(JsonCodec.encode(snap, at, _ui.value.appVersion).toByteArray(Charsets.UTF_8))
                ExportKind.CSV -> CsvExporter.writeZip(snap, at, out)
            }
        }
    }

    fun share(kind: ExportKind) {
        if (_ui.value.busy != null) return
        viewModelScope.launch {
            busy(if (kind == ExportKind.JSON) BusyOp.EXPORT_JSON else BusyOp.EXPORT_CSV)
            try {
                val snap = c.backup.snapshot()
                val at = now()
                val file = withContext(Dispatchers.IO) {
                    ExportFiles.writeToCache(app, ExportFiles.fileName(at, kind.ext)) { out ->
                        when (kind) {
                            ExportKind.JSON -> out.write(JsonCodec.encode(snap, at, _ui.value.appVersion).toByteArray(Charsets.UTF_8))
                            ExportKind.CSV -> CsvExporter.writeZip(snap, at, out)
                        }
                    }
                }
                _events.trySend(SettingsEvent.Share(file, kind.mime))
            } catch (e: Exception) { snack("Export failed: ${e.message}") }
            busy(null)
        }
    }

    fun fileNameFor(kind: ExportKind) = ExportFiles.fileName(now(), kind.ext)

    fun saveTo(kind: ExportKind, uri: Uri?) {
        if (uri == null || _ui.value.busy != null) return
        viewModelScope.launch {
            busy(if (kind == ExportKind.JSON) BusyOp.EXPORT_JSON else BusyOp.EXPORT_CSV)
            try {
                val out = app.contentResolver.openOutputStream(uri) ?: error("Cannot open file")
                out.use { writeExport(kind, it) }
                snack("Exported")
            } catch (e: Exception) { snack("Export failed: ${e.message}") }
            busy(null)
        }
    }

    fun importFrom(uri: Uri?) {
        if (uri == null || _ui.value.busy != null) return
        viewModelScope.launch {
            busy(BusyOp.IMPORT)
            try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Cannot read file")
                }
                val snap = withContext(Dispatchers.Default) { JsonCodec.decode(text) }
                _ui.value = _ui.value.copy(importPreview = ImportPreview(snap))
            } catch (e: ImportException) { snack("Import failed: ${e.message}")
            } catch (e: Exception) { snack("Import failed: ${e.message}") }
            busy(null)
        }
    }

    fun dismissImport() { _ui.value = _ui.value.copy(importPreview = null) }

    fun confirmImport() {
        val snap = _ui.value.importPreview?.snapshot ?: return
        _ui.value = _ui.value.copy(importPreview = null)
        viewModelScope.launch {
            busy(BusyOp.IMPORT)
            try {
                c.backup.replaceAll(snap)
                if (snap.achievements.isEmpty()) {
                    c.achievements.recalculate(c.time.today(), c.settings.current().proteinTargetG, c.time.zone, c.time.now())
                }
                NotificationScheduler.schedule(app, c.settings.current().dailyCheckTime, replace = true)
                val n = snap.goals.size + snap.plans.size + snap.sessions.size + snap.macros.size + snap.bodyEntries.size
                snack("Imported $n items")
            } catch (e: Exception) { snack("Import failed: ${e.message}") }
            busy(null)
        }
    }

    fun requestMock() {
        if (_ui.value.busy != null) return
        viewModelScope.launch {
            if (c.backup.isEmpty()) generateMock() else _ui.value = _ui.value.copy(confirm = Confirm.MOCK)
        }
    }

    fun requestClear() { _ui.value = _ui.value.copy(confirm = Confirm.CLEAR) }
    fun dismissConfirm() { _ui.value = _ui.value.copy(confirm = null) }

    fun confirmed() {
        val which = _ui.value.confirm ?: return
        _ui.value = _ui.value.copy(confirm = null)
        viewModelScope.launch { if (which == Confirm.MOCK) generateMock() else clearAll() }
    }

    private suspend fun generateMock() {
        busy(BusyOp.MOCK)
        try {
            val snap = withContext(Dispatchers.Default) {
                // DECISION: keep the user's current settings rather than resetting them to defaults; only an unset kcal
                // target is filled in so the Diary's "over calories" markers have something to show.
                val mine = c.settings.current()
                MockDataGenerator.generate(c.time.today(), c.time.zone)
                    .copy(settings = mine.copy(kcalTarget = mine.kcalTarget ?: MockDataGenerator.MOCK_KCAL_TARGET))
            }
            c.backup.replaceAll(snap)
            snack("Sample data generated")
        } catch (e: Exception) { snack("Failed: ${e.message}") }
        busy(null)
    }

    private suspend fun clearAll() {
        busy(BusyOp.CLEAR)
        try {
            c.backup.clearAll()
            NotificationScheduler.schedule(app, c.settings.current().dailyCheckTime, replace = true)
            snack("All data cleared")
        } catch (e: Exception) { snack("Failed: ${e.message}") }
        busy(null)
    }

    companion object {
        fun factory(app: Application, c: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(app, c) } }
    }
}
