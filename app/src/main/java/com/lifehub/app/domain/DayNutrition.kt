package com.lifehub.app.domain

import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.MacroDay
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** Macro totals (grams / kcal as doubles; use the rounded accessors for display). */
data class Totals(val kcal: Double = 0.0, val proteinG: Double = 0.0, val carbsG: Double = 0.0, val fatG: Double = 0.0) {
    operator fun plus(o: Totals) = Totals(kcal + o.kcal, proteinG + o.proteinG, carbsG + o.carbsG, fatG + o.fatG)
    val kcalInt get() = kcal.roundToInt()
    val proteinInt get() = proteinG.roundToInt()
    val carbsInt get() = carbsG.roundToInt()
    val fatInt get() = fatG.roundToInt()
}

/** Everything eaten on one day: sum(food entries) + quick add (the legacy [MacroDay]). */
data class DayNutrition(
    val date: LocalDate,
    val totals: Totals,
    val entries: List<FoodEntry>,
    val quickAdd: MacroDay?,
) {
    /** Totals shaped like a MacroDay so protein/kcal consumers (insights, notifications, charts) share one code path. */
    fun asMacroDay(): MacroDay = MacroDay(
        date, totals.proteinInt, totals.carbsInt, totals.fatInt, totals.kcalInt,
        kcalIsAuto = quickAdd?.kcalIsAuto ?: true, updatedAt = quickAdd?.updatedAt ?: entries.maxOfOrNull { it.createdAt } ?: Instant.EPOCH,
    )
}

fun Food.macrosFor(grams: Double): Totals {
    val f = grams / 100.0
    return Totals(kcal100 * f, protein100 * f, carbs100 * f, fat100 * f)
}

fun FoodEntry.totals() = Totals(kcal, proteinG, carbsG, fatG)

fun MacroDay.totals() = Totals(kcal.toDouble(), proteinG.toDouble(), carbsG.toDouble(), fatG.toDouble())

/** Merge macro quick-adds and food entries into one [DayNutrition] per date that has any data (sorted by date). */
fun mergeNutrition(macros: List<MacroDay>, entries: List<FoodEntry>): List<DayNutrition> {
    val m = macros.associateBy { it.date }
    val e = entries.groupBy { it.date }
    return (m.keys + e.keys).sorted().map { d ->
        val list = e[d].orEmpty().sortedBy { it.createdAt }
        val total = list.fold(m[d]?.totals() ?: Totals()) { acc, x -> acc + x.totals() }
        DayNutrition(d, total, list, m[d])
    }
}

fun List<DayNutrition>.asMacroDays(): List<MacroDay> = map { it.asMacroDay() }
