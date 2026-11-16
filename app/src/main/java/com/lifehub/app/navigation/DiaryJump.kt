package com.lifehub.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * One-shot request to show a given day when the Diary tab opens ("Open in diary" on copilot cards). The Diary is a
 * tab root without arguments, so the date travels here instead of in the route; CalendarRoute consumes it.
 */
object DiaryJump {
    private val _date = MutableStateFlow<LocalDate?>(null)
    val date: StateFlow<LocalDate?> = _date
    fun request(d: LocalDate) { _date.value = d }
    fun consume() { _date.value = null }
}
