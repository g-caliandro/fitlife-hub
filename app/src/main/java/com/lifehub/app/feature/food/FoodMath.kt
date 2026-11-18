package com.lifehub.app.feature.food

import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.domain.autoKcal
import java.util.Locale
import java.util.UUID

/** "12", "7.5" (one decimal below 10 when not whole), never "7.0". */
fun fmtMacro(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r >= 10 || r == Math.floor(r)) Math.round(v).toString() else String.format(Locale.US, "%.1f", r)
}

/** Grams text -> value; accepts ',' decimal separator; null when blank/<=0/absurd. */
fun parseGrams(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 10_000 }

/** Up to 2 decimals, trailing zeros trimmed (lossless enough for editing). */
fun fmtExact(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

fun fmtGrams(g: Double): String = if (g == Math.floor(g)) g.toLong().toString() else String.format(Locale.US, "%.1f", g)

data class GramChip(val label: String, val grams: Double)

/** Quick amount chips: 50/100/150/200 plus "1 serving (x g)" when the product has a serving size. */
fun gramChips(servingSizeG: Double?): List<GramChip> =
    listOf(50.0, 100.0, 150.0, 200.0).map { GramChip("${it.toInt()} g", it) } +
        listOfNotNull(servingSizeG?.takeIf { it > 0 }?.let { GramChip("1 serving (${fmtGrams(it)} g)", it) })

/** Stepper for the amount field: +/-[step] clamped to [step, 10000]. */
fun stepGrams(current: Double?, dir: Int, step: Double = 10.0): Double =
    ((current ?: 0.0) + dir * step).coerceIn(step, 10_000.0)

/** Search/filter of the local lists (name or brand contains every word, case-insensitive). */
fun filterFoods(foods: List<Food>, query: String): List<Food> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return foods
    return foods.filter { f ->
        val hay = (f.name + " " + (f.brand ?: "")).lowercase()
        words.all { it in hay }
    }
}

/** Food reconstructed from an entry snapshot (the original food was deleted). */
fun foodFromEntry(e: FoodEntry): Food {
    val f = 100.0 / e.grams.coerceAtLeast(0.0001)
    return Food("snapshot-${e.id}", FoodSource.CUSTOM, null, e.name, e.brand, e.kcal * f, e.proteinG * f, e.carbsG * f, e.fatG * f, null, false, null, e.imageUrl)
}

/** Raw text form of the custom food editor. */
data class CustomFoodForm(
    val name: String = "", val brand: String = "", val barcode: String = "",
    val kcal: String = "", val protein: String = "", val carbs: String = "", val fat: String = "",
    val serving: String = "",
) {
    private fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val autoKcal: Int get() = autoKcal(Math.round(num(protein) ?: 0.0).toInt(), Math.round(num(carbs) ?: 0.0).toInt(), Math.round(num(fat) ?: 0.0).toInt())
    /** Name + at least one of kcal/macros is required. */
    val valid: Boolean get() = name.isNotBlank() && (num(kcal) != null || num(protein) != null || num(carbs) != null || num(fat) != null)

    fun toFood(existing: Food?, newId: () -> String = { UUID.randomUUID().toString() }): Food? {
        if (!valid) return null
        val p = num(protein) ?: 0.0; val c = num(carbs) ?: 0.0; val f = num(fat) ?: 0.0
        return Food(
            id = existing?.id ?: newId(), source = FoodSource.CUSTOM,
            barcode = barcode.trim().takeIf { it.isNotEmpty() }, name = name.trim(), brand = brand.trim().takeIf { it.isNotEmpty() },
            kcal100 = num(kcal) ?: autoKcal(p, c, f), protein100 = p, carbs100 = c, fat100 = f,
            servingSizeG = num(serving)?.takeIf { it > 0 }, favorite = existing?.favorite ?: false, lastUsedAt = existing?.lastUsedAt,
        )
    }

    companion object {
        fun from(f: Food) = CustomFoodForm(
            f.name, f.brand.orEmpty(), f.barcode.orEmpty(), fmtExact(f.kcal100), fmtExact(f.protein100), fmtExact(f.carbs100), fmtExact(f.fat100),
            f.servingSizeG?.let(::fmtGrams).orEmpty(),
        )
    }
}
