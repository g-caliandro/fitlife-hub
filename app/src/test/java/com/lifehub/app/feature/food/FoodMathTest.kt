package com.lifehub.app.feature.food

import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import com.lifehub.app.data.model.FoodSource
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FoodMathTest {
    private fun food(name: String, brand: String? = null) = Food(name, FoodSource.OFF, null, name, brand, 100.0, 1.0, 2.0, 3.0, null, false, null)

    @Test fun formatting() {
        assertEquals("12", fmtMacro(12.04)); assertEquals("7.5", fmtMacro(7.46)); assertEquals("7", fmtMacro(7.0)); assertEquals("0", fmtMacro(0.0))
        assertEquals("100", fmtGrams(100.0)); assertEquals("37.5", fmtGrams(37.5)); assertEquals("58.7", fmtExact(58.7)); assertEquals("0.3", fmtExact(0.3)); assertEquals("372", fmtExact(372.0))
    }

    @Test fun gramsParsing() {
        assertEquals(125.5, parseGrams(" 125,5 ")!!, 1e-9); assertEquals(80.0, parseGrams("80")!!, 1e-9)
        assertNull(parseGrams("")); assertNull(parseGrams("0")); assertNull(parseGrams("abc")); assertNull(parseGrams("99999"))
    }

    @Test fun chipsAndStepper() {
        assertEquals(listOf(50.0, 100.0, 150.0, 200.0), gramChips(null).map { it.grams })
        val c = gramChips(30.0)
        assertEquals(5, c.size); assertEquals("1 serving (30 g)", c.last().label); assertEquals(30.0, c.last().grams, 0.0)
        assertEquals(110.0, stepGrams(100.0, 1), 0.0); assertEquals(10.0, stepGrams(10.0, -1), 0.0); assertEquals(10.0, stepGrams(null, 1), 0.0)
    }

    @Test fun localFilter() {
        val l = listOf(food("Greek yogurt", "Fage"), food("Rolled oats"), food("Oat milk", "Alpro"))
        assertEquals(3, filterFoods(l, "  ").size)
        assertEquals(listOf("Rolled oats", "Oat milk"), filterFoods(l, "oat").map { it.name })
        assertEquals(listOf("Greek yogurt"), filterFoods(l, "fage greek").map { it.name })
        assertTrue(filterFoods(l, "pizza").isEmpty())
    }

    @Test fun customFormValidationAndAutoKcal() {
        assertNull(CustomFoodForm(name = "", protein = "10").toFood(null))
        assertNull(CustomFoodForm(name = "X").toFood(null))
        val f = CustomFoodForm(name = " Granola ", brand = "", protein = "10", carbs = "60,5", fat = "15", serving = "40").toFood(null) { "id1" }!!
        assertEquals("id1", f.id); assertEquals("Granola", f.name); assertNull(f.brand); assertEquals(FoodSource.CUSTOM, f.source)
        assertEquals(40 + 242 + 135.0, f.kcal100, 1e-9); assertEquals(60.5, f.carbs100, 1e-9); assertEquals(40.0, f.servingSizeG!!, 0.0)
        val g = CustomFoodForm(name = "Bar", kcal = "400", protein = "20").toFood(f.copy(favorite = true))!!
        assertEquals("id1", g.id); assertTrue(g.favorite); assertEquals(400.0, g.kcal100, 0.0)
        assertEquals(CustomFoodForm.from(f).copy(barcode = ""), CustomFoodForm.from(f))
        assertEquals("60.5", CustomFoodForm.from(f).carbs)
    }

    @Test fun snapshotFoodScalesBackToPer100g() {
        val e = FoodEntry("e", LocalDate.of(2026, 1, 1), null, "Rice", "B", 250.0, 325.0, 6.75, 70.0, 0.75, Instant.EPOCH)
        val f = foodFromEntry(e)
        assertEquals(130.0, f.kcal100, 1e-9); assertEquals(2.7, f.protein100, 1e-9); assertEquals(28.0, f.carbs100, 1e-9)
    }
}
