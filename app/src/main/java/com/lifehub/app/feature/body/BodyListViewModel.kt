package com.lifehub.app.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BodyListViewModel(private val c: AppContainer) : ViewModel() {
    val today: LocalDate = c.time.today()
    val entries: StateFlow<List<BodyEntry>?> = c.body.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete(date: LocalDate) { viewModelScope.launch { c.body.delete(date) } }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { BodyListViewModel(c) } }
    }
}
