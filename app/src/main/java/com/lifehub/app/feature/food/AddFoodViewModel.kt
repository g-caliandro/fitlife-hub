package com.lifehub.app.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.off.OffApi
import com.lifehub.app.data.off.OffResult
import com.lifehub.app.data.repo.FoodRepository
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FoodTab(val label: String) { RECENT("Recent"), SEARCH("Search"), MINE("My foods") }

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val foods: List<Food>) : SearchState
    data class Error(val message: String) : SearchState
}

data class AddFoodState(
    val date: LocalDate,
    val today: LocalDate,
    val tab: FoodTab = FoodTab.RECENT,
    val query: String = "",
    val recent: List<Food> = emptyList(),
    val mine: List<Food> = emptyList(),
    val search: SearchState = SearchState.Idle,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AddFoodViewModel(
    private val food: FoodRepository, private val off: OffApi, today: LocalDate, date: LocalDate,
    private val debounceMs: Long = 400,
    private val now: () -> java.time.Instant = { java.time.Instant.now() },
) : ViewModel() {
    private val tab = MutableStateFlow(FoodTab.RECENT)
    private val query = MutableStateFlow("")

    private val searchState = combine(tab, merge(query.take(1), query.drop(1).debounce(debounceMs)).distinctUntilChanged()) { t, q -> t to q.trim() }
        .distinctUntilChanged()
        .flatMapLatest { (t, q) ->
            flow {
                if (t != FoodTab.SEARCH || q.length < 2) { emit(SearchState.Idle); return@flow }
                emit(SearchState.Loading)
                emit(when (val r = off.search(q)) { is OffResult.Ok -> SearchState.Results(r.value); is OffResult.Error -> SearchState.Error(r.message) })
            }
        }

    val state: StateFlow<AddFoodState> = combine(tab, query, food.observeRecent(), food.observeMine(), searchState) { t, q, r, m, s ->
        AddFoodState(date, today, t, q, filterFoods(r, if (t == FoodTab.RECENT) q else ""), filterFoods(m, if (t == FoodTab.MINE) q else ""), s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddFoodState(date, today))

    fun setTab(t: FoodTab) { tab.value = t }
    fun setQuery(q: String) {
        query.value = q
        // Typing in a local list stays there; typing from Recent with no local hits is handled by the user switching tab.
    }

    /** Caches the picked product (OFF results are stored on selection) and returns the stored food. */
    fun pick(f: Food, then: (Food) -> Unit) {
        viewModelScope.launch { then(food.saveFood(f)) }
    }

    /** Trailing "+" on a row: logs 1 serving (if the food has one) else 100 g to the date, with Undo. */
    fun quickAdd(f: Food) {
        viewModelScope.launch {
            val grams = f.servingSizeG?.takeIf { it > 0 } ?: 100.0
            val e = food.addEntry(f, state.value.date, grams, now())
            com.lifehub.app.core.designsystem.SnackbarBus.show("Added ${f.name} · ${fmtGrams(grams)} g", "Undo") {
                viewModelScope.launch { food.deleteEntry(e.id) }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer {
                val h: SavedStateHandle = createSavedStateHandle()
                val today = c.time.today()
                val d = (h.get<Long>("date") ?: -1L).let { if (it < 0) today else LocalDate.ofEpochDay(it) }
                AddFoodViewModel(c.food, c.off, today, d, now = c.time::now)
            }
        }
    }
}
