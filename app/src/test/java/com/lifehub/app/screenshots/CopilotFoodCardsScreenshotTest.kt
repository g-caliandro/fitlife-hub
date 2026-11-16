package com.lifehub.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.copilot.agent.MealItemView
import com.lifehub.app.copilot.agent.MealLoggedCard
import com.lifehub.app.copilot.agent.PendingInputCard
import com.lifehub.app.copilot.agent.PendingOptionView
import com.lifehub.app.copilot.agent.ProductCard
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.PendingInput
import com.lifehub.app.data.model.PendingKind
import com.lifehub.app.data.model.PendingStatus
import com.lifehub.app.feature.copilot.CardActions
import com.lifehub.app.feature.copilot.ChatItem
import com.lifehub.app.feature.copilot.ChatScreen
import com.lifehub.app.feature.copilot.ChatUiState
import com.lifehub.app.feature.settings.HouseholdMeasuresScreen
import com.lifehub.app.feature.settings.MeasureRow
import com.lifehub.app.feature.settings.MeasuresUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** Phase 3 cards (meal logged with Undo, pending request with an estimate, product) and the household measures screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xxhdpi")
class CopilotFoodCardsScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 16)

    private val meal = MealLoggedCard(
        captureId = "c1", date = "2026-11-16", time = "13:05",
        items = listOf(
            MealItemView("e1", "Riso bianco", null, 90.0, 328.5, 6.417, 71.955, 0.594, "base_table"),
            MealItemView("e2", "Petto di pollo", null, 180.0, 216.0, 40.5, 0.0, 4.716, "base_table"),
            MealItemView("e3", "Olio d'oliva", null, 9.0, 79.56, 0.0, 0.0, 9.0, "base_table", portionLabel = "cucchiaio", servings = 1.0, portionGrams = 9.0),
            MealItemView("e4", "Merluzzo gratinato", "Findus", 200.0, 300.0, 22.0, 18.0, 15.2, "history", portionLabel = "scatola", servings = 0.5, portionGrams = 400.0, usedDefaultPortion = true),
        ),
        totalKcal = 924.06, totalProteinG = 68.917, totalCarbsG = 89.955, totalFatG = 29.51,
        kcalRemaining = 1276, proteinRemainingG = 81,
    )
    private val ask = PendingInputCard(
        "p1", "c1", "use_estimate", "Non trovo le polpette della nonna Pina. Uso una stima?",
        listOf(PendingOptionView("Polpette al sugo", isEstimate = true, detail = "≈ 220 kcal / 100 g")), "polpette della nonna Pina",
    )
    private val product = ProductCard("Merluzzo gratinato", "Crai", "open_food_facts", 152.0, 11.1, 9.4, 7.6, listOf("package 400 g (default)"), incomplete = true)
    private val open = PendingInput("p1", "c1", "t", PendingKind.USE_ESTIMATE, ask.prompt, "[]", null, null, PendingStatus.OPEN, null, Instant.EPOCH, null)

    private fun state(undone: Boolean = false, pending: PendingInput = open) = ChatUiState(
        today,
        listOf(
            ChatItem.User("u1", "90 g di riso basmati, 180 g di petto di pollo, un cucchiaio di olio, mezzo merluzzo Findus e 100 g di polpette della nonna Pina"),
            ChatItem.Card("t1", meal), ChatItem.Card("t2", ask), ChatItem.Card("t3", product),
        ),
        pending = mapOf("p1" to pending), undoneCaptures = if (undone) setOf("c1") else emptySet(),
    )

    @Test fun mealAndPendingCardsLight() {
        var undo: String? = null; var chosen: Pair<String, Int>? = null
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(state(), onBack = {}, actions = CardActions(onUndo = { undo = it }, onChoose = { p, i -> chosen = p to i }))
        } }
        rule.onNodeWithText("Meal logged").assertIsDisplayed()
        rule.onNodeWithText("1,276 kcal left", substring = true).assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-food-cards-light.png")
        rule.onNodeWithTag("card-undo").performClick()
        assertEquals("c1", undo)
        rule.onNodeWithTag("pending-option-0").performClick()
        assertEquals("p1" to 0, chosen)
    }

    @Test fun undoneAndAnsweredDark() {
        val answered = open.copy(status = PendingStatus.ANSWERED, answerJson = """{"option":0}""")
        rule.setContent { LhTheme(darkTheme = true) { ChatScreen(state(undone = true, pending = answered), onBack = {}) } }
        rule.onNodeWithText("Meal log undone").assertIsDisplayed()
        rule.onNodeWithTag("card-undo").assertIsNotEnabled()
        rule.onNodeWithText("Chosen: Polpette al sugo (estimate)").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-food-cards-dark.png")
    }

    @Test fun householdMeasuresLight() {
        val s = MeasuresUiState(
            generic = listOf(MeasureRow("hm-cucchiaio", "cucchiaio", 10.0, null, null, null), MeasureRow("hm-cucchiaino", "cucchiaino", 5.0, null, null, null)),
            specific = listOf(MeasureRow("hm-cucchiaio-sr-171413", "cucchiaio", 9.0, "Olio d'oliva", null, "sr-171413")),
        )
        rule.setContent { LhTheme(darkTheme = false) { HouseholdMeasuresScreen(s, onBack = {}) } }
        rule.onNodeWithText("Olio d'oliva").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-household-measures.png")
    }
}
