package com.lifehub.app.feature.nutrition

import com.lifehub.app.data.model.QuickAddEntry
import com.lifehub.app.domain.autoKcal

/** Pure helpers for the quick-add form. A blank macro field = unknown (null), which is not 0. */
data class ParsedMacros(val protein: Int?, val carbs: Int?, val fat: Int?, val kcal: Int?) {
    val allMacrosKnown get() = protein != null && carbs != null && fat != null
}

fun parseMacros(p: String, c: String, f: String, kcal: String): ParsedMacros =
    ParsedMacros(p.trim().toIntOrNull(), c.trim().toIntOrNull(), f.trim().toIntOrNull(), kcal.trim().toIntOrNull())

/** Auto kcal (4·P + 4·C + 9·F), only when all three macros are known. */
fun macroAutoKcal(m: ParsedMacros): Int? = if (m.allMacrosKnown) autoKcal(m.protein!!, m.carbs!!, m.fat!!) else null

/** Calories are what matter: kcal typed, or computable because protein, carbs and fat are all given (and not all 0). */
fun macrosValid(m: ParsedMacros): Boolean = m.kcal != null || (macroAutoKcal(m) ?: 0) > 0

/** kcal that will be stored ([ParsedMacros.kcal] or the auto value); null when invalid. */
fun effectiveKcal(m: ParsedMacros): Int? = m.kcal ?: macroAutoKcal(m)?.takeIf { it > 0 }

/** "P 12 · C n/a · F 4" (n/a = unknown). */
fun macroSummary(proteinG: Int?, carbsG: Int?, fatG: Int?): String =
    if (proteinG == null && carbsG == null && fatG == null) "Macros n/a"
    else "P ${proteinG ?: "n/a"} · C ${carbsG ?: "n/a"} · F ${fatG ?: "n/a"}"

fun QuickAddEntry.macroSummary(): String = macroSummary(proteinG, carbsG, fatG)

/** "Copy yesterday" when the source is the day before [selected], else "Copy Mon". */
fun copyLabel(source: java.time.LocalDate, selected: java.time.LocalDate): String =
    if (source == selected.minusDays(1)) "Copy yesterday"
    else "Copy " + source.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
