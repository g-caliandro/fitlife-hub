package com.lifehub.app.copilot.agent

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Today", "Yesterday" or "Sun 15 Nov": how cards and notifications name the day a log landed on (English chrome). */
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
}
