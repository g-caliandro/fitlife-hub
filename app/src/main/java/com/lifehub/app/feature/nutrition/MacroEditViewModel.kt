package com.lifehub.app.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.MacroDay
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MacroEditState(
    val date: LocalDate,
    val today: LocalDate,
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val kcal: String = "",
    val existing: Boolean = false,
    val showError: Boolean = false,
    val proteinTarget: Int = 150,
    val copySource: MacroDay? = null,
)

class MacroEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val today = c.time.today()
    private val _state = MutableStateFlow(
        MacroEditState(
            date = (handle.get<Long>("date") ?: -1L).let { if (it < 0) today else LocalDate.ofEpochDay(it) },
            today = today, proteinTarget = c.settings.current().proteinTargetG,
        )
    )
    val state: StateFlow<MacroEditState> = _state

    init { load(_state.value.date, keepIfMissing = false) }

    private fun load(date: LocalDate, keepIfMissing: Boolean) {
        viewModelScope.launch {
            val m = c.macros.get(date)
            val src = c.macros.latestBefore(date)
            _state.update {
                when {
                    it.date != date -> it
                    m != null -> it.copy(copySource = src,
                        protein = m.proteinG.toString(), carbs = m.carbsG.toString(), fat = m.fatG.toString(),
                        kcal = if (m.kcalIsAuto) "" else m.kcal.toString(), existing = true, showError = false,
                    )
                    else -> it.copy(existing = false, copySource = src)
                }
            }
        }
    }

    fun setDate(d: LocalDate) { _state.update { it.copy(date = d) }; load(d, keepIfMissing = true) }
    fun setProtein(v: String) = _state.update { it.copy(protein = v, showError = false) }
    fun setCarbs(v: String) = _state.update { it.copy(carbs = v, showError = false) }
    fun setFat(v: String) = _state.update { it.copy(fat = v, showError = false) }
    fun setKcal(v: String) = _state.update { it.copy(kcal = v) }

    fun copyFromPrevious() {
        val m = _state.value.copySource ?: return
        _state.update {
            it.copy(
                protein = m.proteinG.toString(), carbs = m.carbsG.toString(), fat = m.fatG.toString(),
                kcal = if (m.kcalIsAuto) "" else m.kcal.toString(), showError = false,
            )
        }
    }

    /** [onDone] receives an undo action restoring the previous state of this day. */
    fun save(onDone: (undo: () -> Unit) -> Unit) {
        val s = _state.value
        val m = parseMacros(s.protein, s.carbs, s.fat, s.kcal)
        if (!macrosValid(m)) { _state.update { it.copy(showError = true) }; return }
        viewModelScope.launch {
            val prev = c.macros.get(s.date)
            c.macros.upsert(s.date, m.protein, m.carbs, m.fat, m.kcal, c.time.now())
            onDone { c.appScope.launch { c.macros.restore(s.date, prev, c.time.now()) } }
        }
    }

    fun delete(onDone: (undo: () -> Unit) -> Unit) {
        val d = _state.value.date
        // Use appScope so the delete completes even as the screen closes.
        c.appScope.launch {
            val prev = c.macros.get(d)
            c.macros.delete(d)
            onDone { c.appScope.launch { c.macros.restore(d, prev, c.time.now()) } }
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { MacroEditViewModel(c, createSavedStateHandle()) } }
    }
}
