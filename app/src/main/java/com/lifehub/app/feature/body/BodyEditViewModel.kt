package com.lifehub.app.feature.body

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BodyEditState(
    val date: LocalDate,
    val today: LocalDate,
    val weight: String = "",
    val muscle: String = "",
    val fat: String = "",
    val existing: Boolean = false,
    val validation: BodyValidation? = null,   // non-null after a failed save
    val lastDate: LocalDate? = null,
    val lastWeight: Double? = null,
)

class BodyEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val today = c.time.today()
    private val _state = MutableStateFlow(
        BodyEditState(
            date = (handle.get<Long>("date") ?: -1L).let { if (it < 0) today else LocalDate.ofEpochDay(it) },
            today = today,
        )
    )
    val state: StateFlow<BodyEditState> = _state

    init {
        load(_state.value.date)
        viewModelScope.launch {
            val ld = c.body.latestDate() ?: return@launch
            val e = c.body.get(ld) ?: return@launch
            _state.update { it.copy(lastDate = ld, lastWeight = e.weightKg) }
        }
    }

    private fun load(date: LocalDate) {
        viewModelScope.launch {
            val e = c.body.get(date)
            val latest = if (e == null) c.body.latestDate()?.let { c.body.get(it) } else null
            _state.update {
                when {
                    it.date != date -> it
                    e != null -> it.copy(
                        weight = fmt(e.weightKg), muscle = e.muscleKg?.let(::fmt) ?: "", fat = e.fatKg?.let(::fmt) ?: "",
                        existing = true, validation = null,
                    )
                    else -> it.copy(existing = false).let { st ->
                        if (st.weight.isBlank() && st.muscle.isBlank() && st.fat.isBlank() && latest != null) st.copy(
                            weight = fmt(latest.weightKg), muscle = latest.muscleKg?.let(::fmt) ?: "", fat = latest.fatKg?.let(::fmt) ?: "",
                        ) else st
                    }
                }
            }
        }
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.1f", v)

    fun setDate(d: LocalDate) { _state.update { it.copy(date = d) }; load(d) }
    fun setWeight(v: String) = _state.update { it.copy(weight = v, validation = null) }
    fun setMuscle(v: String) = _state.update { it.copy(muscle = v, validation = null) }
    fun setFat(v: String) = _state.update { it.copy(fat = v, validation = null) }

    fun save(onDone: (undo: () -> Unit) -> Unit) {
        val s = _state.value
        val v = validateBody(s.weight, s.muscle, s.fat)
        if (!v.ok) { _state.update { it.copy(validation = v) }; return }
        viewModelScope.launch {
            val prev = c.body.get(s.date)
            c.body.upsert(BodyEntry(s.date, v.weight!!, v.muscle, v.fat, c.time.now()))
            onDone { c.appScope.launch { restoreBody(c.body, s.date, prev) } }
        }
    }

    fun delete(onDone: (undo: () -> Unit) -> Unit) {
        val d = _state.value.date
        c.appScope.launch {
            val prev = c.body.get(d)
            c.body.delete(d)
            onDone { c.appScope.launch { restoreBody(c.body, d, prev) } }
        }
    }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { BodyEditViewModel(c, createSavedStateHandle()) } }
    }
}
