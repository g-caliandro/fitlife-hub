package com.lifehub.app.domain

import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.MacroDay
import com.lifehub.app.data.model.QuickAddEntry
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Macro totals (grams / kcal as doubles; use the rounded accessors for display).
 *
 * Null semantics: a quick add may leave protein/carbs/fat unknown. Grams are the sum of the KNOWN values; [items]
 * counts the summed items and `*Missing` how many of them had that macro unknown. So a macro is
 * - unknown ([proteinUnknown]) when every item lacks it (show "—", never treat as 0 g eaten),
 * - complete ([proteinComplete]) when no item lacks it (the sum is the real total),
 * - otherwise partial (the sum is a lower bound).
 */
data class Totals(
    val kcal: Double = 0.0, val proteinG: Double = 0.0, val carbsG: Double = 0.0, val fatG: Double = 0.0,
    val items: Int = 0, val proteinMissing: Int = 0, val carbsMissing: Int = 0, val fatMissing: Int = 0,
) {
    operator fun plus(o: Totals) = Totals(
        kcal + o.kcal, proteinG + o.proteinG, carbsG + o.carbsG, fatG + o.fatG,
        items + o.items, proteinMissing + o.proteinMissing, carbsMissing + o.carbsMissing, fatMissing + o.fatMissing,
    )
    val kcalInt get() = kcal.roundToInt()
    val proteinInt get() = proteinG.roundToInt()
    val carbsInt get() = carbsG.roundToInt()
    val fatInt get() = fatG.roundToInt()
    val proteinUnknown get() = items > 0 && proteinMissing >= items
    val carbsUnknown get() = items > 0 && carbsMissing >= items
    val fatUnknown get() = items > 0 && fatMissing >= items
    val proteinComplete get() = proteinMissing == 0
}

/** Everything eaten on one day: sum(food entries) + sum(quick adds). */
data class DayNutrition(
    val date: LocalDate,
    val totals: Totals,
    val entries: List<FoodEntry>,
    val quickAdds: List<QuickAddEntry> = emptyList(),
) {
    /** Totals shaped like a MacroDay so protein/kcal consumers (insights, notifications, charts) share one code path. */
    fun asMacroDay(): MacroDay = MacroDay(
        date, totals.proteinInt, totals.carbsInt, totals.fatInt, totals.kcalInt,
        kcalIsAuto = quickAdds.all { it.kcalIsAuto },
        updatedAt = (entries.map { it.createdAt } + quickAdds.map { it.createdAt }).maxOrNull() ?: Instant.EPOCH,
        proteinKnown = !totals.proteinUnknown, proteinComplete = totals.proteinComplete,
        carbsKnown = !totals.carbsUnknown, fatKnown = !totals.fatUnknown,
        carbsComplete = totals.carbsMissing == 0, fatComplete = totals.fatMissing == 0,
    )
}

fun Food.macrosFor(grams: Double): Totals {
    val f = grams / 100.0
    return Totals(kcal100 * f, protein100 * f, carbs100 * f, fat100 * f)
}

fun FoodEntry.totals() = Totals(kcal, proteinG, carbsG, fatG, items = 1)

fun QuickAddEntry.totals() = Totals(
    kcal.toDouble(), proteinG?.toDouble() ?: 0.0, carbsG?.toDouble() ?: 0.0, fatG?.toDouble() ?: 0.0, items = 1,
    proteinMissing = if (proteinG == null) 1 else 0, carbsMissing = if (carbsG == null) 1 else 0, fatMissing = if (fatG == null) 1 else 0,
)

/** Merge quick adds and food entries into one [DayNutrition] per date that has any data (sorted by date). */
fun mergeNutrition(quickAdds: List<QuickAddEntry>, entries: List<FoodEntry>): List<DayNutrition> {
    val q = quickAdds.groupBy { it.date }
    val e = entries.groupBy { it.date }
    return (q.keys + e.keys).sorted().map { d ->
        val list = e[d].orEmpty().sortedBy { it.createdAt }
        val quick = q[d].orEmpty().sortedWith(compareBy({ it.createdAt }, { it.id }))
        val total = (list.map { it.totals() } + quick.map { it.totals() }).fold(Totals()) { acc, x -> acc + x }
        DayNutrition(d, total, list, quick)
    }
}

fun List<DayNutrition>.asMacroDays(): List<MacroDay> = map { it.asMacroDay() }
