package com.lifehub.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.*
import com.lifehub.app.domain.macrosFor
import com.lifehub.app.domain.mergeNutrition
import com.lifehub.app.feature.calendar.*
import com.lifehub.app.feature.food.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class FoodScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 12)
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    private fun both(name: String, content: @Composable () -> Unit) {
        var dark by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { LhTheme(darkTheme = dark) { content() } }
        rule.onRoot().captureRoboImage("build/screens/food-$name.png")
        dark = true
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/food-$name-dark.png")
    }

    private fun food(id: String, name: String, brand: String?, k: Double, p: Double, c: Double, f: Double, serving: Double? = null, src: FoodSource = FoodSource.OFF) =
        Food(id, src, null, name, brand, k, p, c, f, serving, false, now)

    private val oats = food("o", "Rolled oats", "Quaker", 372.0, 13.5, 58.7, 7.0, 40.0).copy(imageUrl = "https://images.openfoodfacts.org/oats.200.jpg") // offline in tests -> letter fallback
    private val chicken = food("c", "Chicken breast", null, 165.0, 31.0, 0.0, 3.6, null, FoodSource.CUSTOM)
    private val results = listOf(
        oats, food("y", "Greek yogurt 0%", "Fage", 54.0, 10.3, 4.0, 0.2, 170.0), chicken,
        food("w", "Whey isolate chocolate", "MyProtein", 372.0, 86.0, 3.5, 1.6, 25.0), food("b", "Wholemeal bread", "Harry's", 247.0, 13.0, 41.0, 3.4, 40.0),
    )

    @Test fun addFoodSearch() = both("add-search") {
        AddFoodScreen(AddFoodState(today, today, FoodTab.SEARCH, "oat", search = SearchState.Results(results)), {}, {}, {}, {}, {}, {})
    }
    @Test fun addFoodRecent() = both("add-recent") {
        AddFoodScreen(AddFoodState(today.minusDays(1), today, FoodTab.RECENT, recent = results.take(3)), {}, {}, {}, {}, {}, {})
    }
    @Test fun addFoodOffline() = both("add-error") {
        AddFoodScreen(AddFoodState(today, today, FoodTab.SEARCH, "oat", search = SearchState.Error("Couldn't reach Open Food Facts. Check your connection.")), {}, {}, {}, {}, {}, {})
    }
    @Test fun detail() = both("detail") { FoodDetailScreen(FoodDetailState(false, oats, today, today, "80", kcalTarget = 2200), {}, {}, {}, {}, {}, {}) }
    @Test fun detailEdit() = both("detail-edit") { FoodDetailScreen(FoodDetailState(false, chicken.copy(favorite = true), today.minusDays(2), today, "150", "e1"), {}, {}, {}, {}, {}, {}) }
    @Test fun custom() = both("custom") {
        CustomFoodScreen(CustomFoodState(CustomFoodForm("Homemade granola", "", "", "", "9", "62", "16", "40")), {}, {}, {}, {})
    }
    @Test fun scanPermission() = both("scan-permission") { ScanScreen(ScanUi.NeedsPermission(false), {}, {}, {}, {}, {}) }
    @Test fun scanDenied() = both("scan-denied") { ScanScreen(ScanUi.PermanentlyDenied, {}, {}, {}, {}, {}) }
    @Test fun scanNotFound() = both("scan-notfound") { ScanScreen(ScanUi.NotFound("3017620422003"), {}, {}, {}, {}, {}) }

    @Config(qualifiers = "w360dp-h1900dp-xxhdpi")   // tall: the whole food list of the day detail
    @Test fun calendarDay() {
        val d = today.minusDays(1)
        fun e(id: String, f: Food, g: Double, at: Long): FoodEntry { val m = f.macrosFor(g)
            return FoodEntry(id, d, f.id, f.name, f.brand, g, m.kcal, m.proteinG, m.carbsG, m.fatG, now.plusSeconds(at)) }
        val entries = listOf(e("1", oats, 70.0, 0), e("2", results[1], 200.0, 1), e("3", chicken, 180.0, 2), e("4", results[3], 30.0, 3))
        // Quick adds between the foods: a full one without name, a kcal-only named one (macros n/a), a partial one.
        val quick = listOf(
            QuickAddEntry("q1", d, null, 184, 12, 25, 4, true, now.plusSeconds(1).plusMillis(500)),
            QuickAddEntry("q2", d, "Pizza out", 1050, null, null, null, false, now.plusSeconds(5)),
            QuickAddEntry("q3", d, "Cappuccino", 120, 6, null, null, false, now.plusSeconds(6)),
        )
        val nut = mergeNutrition(quick, entries).single()
        val state = CalendarState(
            today, d, macroDays = setOf(d, today.minusDays(3)), weightDays = setOf(d),
            day = DaySummary(d, emptyList(), BodyEntry(d, 76.9, 37.3, 12.5, now), nut),
            kcalTarget = 2600, carbsTarget = 250, fatTarget = 80,
        )
        both("calendar-day") { CalendarScreen(state, {}, {}) }
    }

    /** A day logged only with kcal-only quick adds: every macro is unknown ("—"). */
    @Config(qualifiers = "w360dp-h1500dp-xxhdpi")
    @Test fun calendarDayKcalOnly() {
        val d = today.minusDays(1)
        val quick = listOf(
            QuickAddEntry("q1", d, "Lunch out", 950, null, null, null, false, now),
            QuickAddEntry("q2", d, null, 600, null, null, null, false, now.plusSeconds(1)),
        )
        val state = CalendarState(
            today, d, day = DaySummary(d, emptyList(), null, mergeNutrition(quick, emptyList()).single()),
            kcalTarget = 2600, carbsTarget = 250, fatTarget = 80,
        )
        both("calendar-day-kcal-only") { CalendarScreen(state, {}, {}) }
    }
}
