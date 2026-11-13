package com.lifehub.app.feature.nutrition

import com.lifehub.app.data.model.MacroDay
import com.lifehub.app.data.repo.MacroRepository
import com.lifehub.app.domain.autoKcal

/** Pure helpers for the macro form. */
data class ParsedMacros(val protein: Int, val carbs: Int, val fat: Int, val kcal: Int?)

fun parseMacros(p: String, c: String, f: String, kcal: String): ParsedMacros =
    ParsedMacros(p.toIntOrNull() ?: 0, c.toIntOrNull() ?: 0, f.toIntOrNull() ?: 0, kcal.toIntOrNull())

/** F-NUT-1: at least one macro > 0. */
fun macrosValid(m: ParsedMacros): Boolean = m.protein > 0 || m.carbs > 0 || m.fat > 0

fun macroAutoKcal(m: ParsedMacros): Int = autoKcal(m.protein, m.carbs, m.fat)

/** Undo for a save/delete: put [prev] back, or remove the row if there was none. */
suspend fun MacroRepository.restore(date: java.time.LocalDate, prev: MacroDay?, now: java.time.Instant) {
    if (prev == null) delete(date)
    else upsert(date, prev.proteinG, prev.carbsG, prev.fatG, if (prev.kcalIsAuto) null else prev.kcal, now)
}

/** "Copy yesterday" when the source is the day before [selected], else "Copy Mon". */
fun copyLabel(source: java.time.LocalDate, selected: java.time.LocalDate): String =
    if (source == selected.minusDays(1)) "Copy yesterday"
    else "Copy " + source.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
