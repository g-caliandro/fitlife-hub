package com.lifehub.app.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.QuickAddEntry
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Quick-add form: a new entry of [date] ([id] null) or the edit of entry [id]. Blank macro = unknown. */
data class MacroEditState(
    val date: LocalDate,
    val today: LocalDate,
    val id: String? = null,
    val name: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val kcal: String = "",
    val existing: Boolean = false,
    val showError: Boolean = false,
    val proteinTarget: Int = 150,
    val copySource: QuickAddEntry? = null,
)

class MacroEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val today = c.time.today()
    private val _state = MutableStateFlow(
        MacroEditState(
            date = (handle.get<Long>("date") ?: -1L).let { if (it < 0) today else LocalDate.ofEpochDay(it) },
            today = today, id = handle.get<String?>("id"), proteinTarget = c.settings.current().proteinTargetG,
        )
    )
    val state: StateFlow<MacroEditState> = _state

    init {
        viewModelScope.launch {
            val id = _state.value.id
            val e = id?.let { c.quickAdds.get(it) }
            if (e != null) _state.update { it.copy(date = e.date).fill(e).copy(existing = true) }
            else if (id != null) _state.update { it.copy(id = null) }   // deleted meanwhile: behave as "new"
            loadCopySource(_state.value.date)
        }
    }

    private fun MacroEditState.fill(e: QuickAddEntry) = copy(
        name = e.name.orEmpty(), protein = e.proteinG?.toString().orEmpty(), carbs = e.carbsG?.toString().orEmpty(),
        fat = e.fatG?.toString().orEmpty(), kcal = if (e.kcalIsAuto) "" else e.kcal.toString(), showError = false,
    )

    private fun loadCopySource(date: LocalDate) {
        viewModelScope.launch {
            val src = c.quickAdds.latestBefore(date)
            _state.update { if (it.date == date) it.copy(copySource = src) else it }
        }
    }

    fun setDate(d: LocalDate) { _state.update { it.copy(date = d) }; loadCopySource(d) }
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setProtein(v: String) = _state.update { it.copy(protein = v, showError = false) }
    fun setCarbs(v: String) = _state.update { it.copy(carbs = v, showError = false) }
    fun setFat(v: String) = _state.update { it.copy(fat = v, showError = false) }
    fun setKcal(v: String) = _state.update { it.copy(kcal = v, showError = false) }

    fun copyFromPrevious() {
        val e = _state.value.copySource ?: return
        _state.update { it.fill(e) }
    }

    /** [onDone] receives an undo action (new entry: remove it; edit: put the previous values back). */
    fun save(onDone: (undo: () -> Unit) -> Unit) {
        val s = _state.value
        val m = parseMacros(s.protein, s.carbs, s.fat, s.kcal)
        if (!macrosValid(m)) { _state.update { it.copy(showError = true) }; return }
        viewModelScope.launch {
            val prev = s.id?.let { c.quickAdds.get(it) }
            val saved = c.quickAdds.save(s.id, s.date, s.name, m.kcal, m.protein, m.carbs, m.fat, c.time.now())
            onDone {
                c.appScope.launch { if (prev == null) c.quickAdds.delete(saved.id) else c.quickAdds.restore(prev) }
            }
        }
    }

    fun delete(onDone: (undo: () -> Unit) -> Unit) {
        val id = _state.value.id ?: return
        // Use appScope so the delete completes even as the screen closes.
        c.appScope.launch {
            val prev = c.quickAdds.get(id)
            c.quickAdds.delete(id)
            onDone { c.appScope.launch { prev?.let { c.quickAdds.restore(it) } } }
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { MacroEditViewModel(c, createSavedStateHandle()) } }
    }
}
