package com.lifehub.app.mock

import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.domain.macrosFor
import java.util.UUID
import kotlin.random.Random

/** Everyday foods used by the mock data (per 100 g values are typical label numbers). */
object MockFoods {
    private fun f(key: String, name: String, brand: String?, kcal: Double, p: Double, c: Double, fat: Double, serving: Double? = null, custom: Boolean = false) =
        key to Food(UUID.nameUUIDFromBytes("food-$key".toByteArray()).toString(), if (custom) FoodSource.CUSTOM else FoodSource.OFF,
            if (custom) null else "30" + key.hashCode().toString().takeLast(10).padStart(10, '0'), name, brand, kcal, p, c, fat, serving, false, null)

    val all: Map<String, Food> = listOf(
        f("oats", "Rolled oats", null, 372.0, 13.5, 58.7, 7.0, 40.0, custom = true),
        f("egg", "Egg", null, 143.0, 12.6, 0.7, 9.5, 55.0, custom = true),
        f("chicken", "Chicken breast", null, 165.0, 31.0, 0.0, 3.6, custom = true),
        f("rice", "Basmati rice (cooked)", null, 130.0, 2.7, 28.0, 0.3, 150.0),
        f("yogurt", "Greek yogurt 0%", "Fage", 54.0, 10.3, 4.0, 0.2, 170.0),
        f("whey", "Whey protein", "MyProtein", 400.0, 80.0, 7.0, 6.0, 30.0),
        f("banana", "Banana", null, 89.0, 1.1, 22.8, 0.3, 120.0, custom = true),
        f("oil", "Olive oil", null, 884.0, 0.0, 0.0, 100.0, 10.0, custom = true),
        f("pasta", "Pasta (cooked)", "Barilla", 158.0, 5.8, 30.9, 0.9, 200.0),
        f("salmon", "Salmon fillet", null, 208.0, 20.4, 0.0, 13.4, 150.0, custom = true),
        f("bread", "Wholemeal bread", null, 247.0, 13.0, 41.0, 3.4, 40.0),
        f("almonds", "Almonds", null, 579.0, 21.2, 21.6, 49.9, 30.0),
        f("apple", "Apple", null, 52.0, 0.3, 13.8, 0.2, 180.0, custom = true),
    ).toMap()

    val favorites: Set<String> = listOf("chicken", "whey", "oats").map { all.getValue(it).id }.toSet()

    private fun r5(g: Double) = (Math.round(g / 5.0) * 5).toDouble().coerceAtLeast(5.0)

    /**
     * (foodKey, grams) list for one day. Quantities are scaled so protein lands near [protein] and the kcal near the
     * old totals (carbs/fat only steer the carb base and the oil). [minProtein] is enforced by adding whey.
     */
    fun dayMenu(r: Random, protein: Int, carbs: Int, fat: Int, minProtein: Int): List<Pair<String, Double>> {
        val items = mutableListOf<Pair<String, Double>>()
        val carbBase = r.nextBoolean()
        items += "oats" to (50 + r.nextInt(0, 5) * 10.0)
        items += "banana" to 120.0
        items += "yogurt" to (150 + r.nextInt(0, 3) * 50.0)
        if (r.nextDouble() < 0.5) items += "egg" to (110.0)
        items += "chicken" to (130 + r.nextInt(0, 4) * 15.0)
        items += "rice" to (carbs * (if (carbBase) 1.4 else 0.9) + r.nextInt(-20, 21))
        items += "pasta" to (carbs * (if (carbBase) 0.75 else 1.1) + r.nextInt(-20, 21))
        items += "apple" to 180.0
        items += "oil" to (fat * 0.4 + r.nextInt(0, 6))
        items += (if (r.nextBoolean()) "salmon" else "chicken") to (120 + r.nextInt(0, 4) * 10.0)
        if (r.nextDouble() < 0.4) items += "almonds" to 30.0
        if (r.nextDouble() < 0.3) items += "bread" to 80.0
        items += "whey" to 30.0
        var out = items.map { it.first to r5(it.second) }
        fun prot() = out.sumOf { all.getValue(it.first).macrosFor(it.second).proteinG }
        // Steer protein with the whey + chicken portions.
        val diff = protein - prot()
        val wheyG = (30 + diff / 0.8 * 0.6).coerceIn(0.0, 90.0)
        out = out.map { if (it.first == "whey") it.first to r5(wheyG) else it }
        val rest = protein - prot()
        if (rest > 3) out = out.map { if (it.first == "chicken") it.first to r5(it.second + rest / 0.31 / 2) else it }
        else if (rest < -3) out = out.map { if (it.first == "chicken") it.first to r5((it.second + rest / 0.31 / 2).coerceAtLeast(60.0)) else it }
        while (prot() < minProtein) out = out.map { if (it.first == "whey") it.first to it.second + 5 else it }
        return out
    }
}
