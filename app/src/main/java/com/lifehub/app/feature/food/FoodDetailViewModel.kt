package com.lifehub.app.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FoodDetailState(
    val loading: Boolean = true,
    val food: Food? = null,
    val date: LocalDate,
    val today: LocalDate,
    val grams: String = "100",
    /** Non-null when editing an existing entry. */
    val entryId: String? = null,
    val kcalTarget: Int? = null,
) {
    val editing get() = entryId != null
}

class FoodDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val today = c.time.today()
    private val foodId: String = handle.get<String>("foodId").orEmpty()
    private val entryId: String? = handle.get<String?>("entryId")
    private val _state = MutableStateFlow(
        FoodDetailState(date = (handle.get<Long>("date") ?: -1L).let { if (it < 0) today else LocalDate.ofEpochDay(it) }, today = today, entryId = entryId)
    )
    val state: StateFlow<FoodDetailState> = _state
    private var entry: FoodEntry? = null

    init {
        viewModelScope.launch {
            val e = entryId?.let { c.food.getEntry(it) }
            entry = e
            val f = c.food.getFood(foodId) ?: e?.let(::foodFromEntry)
            _state.update {
                it.copy(
                    loading = false, food = f, kcalTarget = c.settings.settings.first().kcalTarget,
                    date = e?.date ?: it.date,
                    grams = e?.let { x -> fmtGrams(x.grams) } ?: f?.servingSizeG?.let(::fmtGrams) ?: "100",
                )
            }
        }
    }

    fun setGrams(v: String) = _state.update { it.copy(grams = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) }

    fun toggleFavorite() {
        val f = _state.value.food ?: return
        if (f.id.startsWith("snapshot-")) return
        viewModelScope.launch {
            c.food.setFavorite(f.id, !f.favorite)
            c.food.getFood(f.id)?.let { nf -> _state.update { it.copy(food = nf) } }
        }
    }

    /** Adds a new entry, or updates the edited one. [onDone] gets the snackbar text and the undo action. */
    fun save(onDone: (message: String, undo: () -> Unit) -> Unit) {
        val s = _state.value
        val f = s.food ?: return
        val g = parseGrams(s.grams) ?: return
        viewModelScope.launch {
            val prev = entry
            if (prev == null) {
                val added = c.food.addEntry(f, s.date, g, c.time.now())
                onDone("Added ${f.name}") { viewModelScope.launch { c.food.deleteEntry(added.id) } }
            } else {
                c.food.updateEntryGrams(prev.id, g, c.food.getFood(foodId))
                onDone("Updated ${f.name}") { viewModelScope.launch { c.food.restoreEntry(prev) } }
            }
        }
    }

    fun delete(onDone: (message: String, undo: () -> Unit) -> Unit) {
        val prev = entry ?: return
        viewModelScope.launch {
            c.food.deleteEntry(prev.id)
            onDone("Removed ${prev.name}") { viewModelScope.launch { c.food.restoreEntry(prev) } }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { FoodDetailViewModel(c, createSavedStateHandle()) } }
    }
}
