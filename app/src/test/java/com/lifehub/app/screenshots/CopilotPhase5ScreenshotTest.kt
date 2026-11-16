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
import com.lifehub.app.copilot.agent.WeightLoggedCard
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.feature.copilot.CardActions
import com.lifehub.app.feature.copilot.ChatAttachment
import com.lifehub.app.feature.copilot.ChatItem
import com.lifehub.app.feature.copilot.ChatScreen
import com.lifehub.app.feature.copilot.ChatUiState
import com.lifehub.app.feature.food.ScanScreen
import com.lifehub.app.feature.food.ScanUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Phase 5 UI: scanned-product chip in the composer, weight card with Undo, retroactive meal card, scanner in pick mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class CopilotPhase5ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 16)

    private val yesterdayMeal = MealLoggedCard(
        captureId = "c2", date = "2026-11-15", time = "20:30",
        items = listOf(MealItemView("e1", "Salmone selvaggio", null, 200.0, 284.0, 40.0, 0.0, 12.4, "base_table")),
        totalKcal = 284.0, totalProteinG = 40.0, totalCarbsG = 0.0, totalFatG = 12.4, kcalRemaining = 616, proteinRemainingG = 22,
    )

    @Test fun attachmentChipAndSendWithTheProductAttached() {
        var removed = false; var barcode = false
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(
                ChatUiState(today, listOf(ChatItem.User("u1", "ne ho mangiati 30 grammi", attachment = "Nocciolata (Rigoni)")),
                    attachment = ChatAttachment("f1", "Nocciolata (Rigoni)", incomplete = true)),
                onBack = {}, onBarcode = { barcode = true }, onRemoveAttachment = { removed = true },
            )
        } }
        rule.onNodeWithTag("chat-attachment").assertIsDisplayed()
        rule.onNodeWithTag("bubble-attachment").assertIsDisplayed()
        rule.onNodeWithText("incomplete values", substring = true).assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-attachment-light.png")
        rule.onNodeWithTag("chat-barcode").performClick(); assertEquals(true, barcode)
        rule.onNodeWithTag("chat-attachment-remove").performClick(); assertEquals(true, removed)
    }

    @Test fun noAttachmentChipByDefaultButTheBarcodeButtonIsThere() {
        rule.setContent { LhTheme(darkTheme = false) { ChatScreen(ChatUiState(today), onBack = {}) } }
        rule.onNodeWithTag("chat-attachment").assertDoesNotExist()
        rule.onNodeWithTag("chat-barcode").assertIsDisplayed()
    }

    @Test fun weightCardShowsValueDateAndUndoThenUndone() {
        var undo: String? = null
        val card = WeightLoggedCard("c1", "2026-11-15", 76.9, previousWeightKg = 77.4)
        rule.setContent { LhTheme(darkTheme = true) {
            ChatScreen(ChatUiState(today, listOf(ChatItem.Card("t1", card))), onBack = {}, actions = CardActions(onUndo = { undo = it }))
        } }
        rule.onNodeWithText("Weight logged").assertIsDisplayed()
        rule.onNodeWithTag("weight-value").assertIsDisplayed(); rule.onNodeWithText("76.9 kg").assertIsDisplayed()
        rule.onNodeWithText("Yesterday").assertIsDisplayed()
        rule.onNodeWithText("Replaced 77.4 kg").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-weight-dark.png")
        rule.onNodeWithTag("card-undo").performClick(); assertEquals("c1", undo)
    }

    @Test fun undoneWeightCardIsDisabled() {
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(ChatUiState(today, listOf(ChatItem.Card("t1", WeightLoggedCard("c1", "2026-11-16", 74.2))), undoneCaptures = setOf("c1")), onBack = {})
        } }
        rule.onNodeWithText("Weight log undone").assertIsDisplayed()
        rule.onNodeWithTag("card-undo").assertIsNotEnabled()
    }

    @Test fun retroactiveMealCardNamesYesterdayAndItsRemainingRefersToThatDay() {
        rule.setContent { LhTheme(darkTheme = false) { ChatScreen(ChatUiState(today, listOf(ChatItem.Card("t1", yesterdayMeal))), onBack = {}) } }
        rule.onNodeWithText("Yesterday · 20:30").assertIsDisplayed()
        rule.onNodeWithText("Yesterday after this meal: 616 kcal left · 22 g protein left").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-retro-meal-light.png")
    }

    @Test fun scannerInPickModeOffersNoCustomFoodAndTheAppFlowStillDoes() {
        rule.setContent { LhTheme(darkTheme = false) { ScanScreen(ScanUi.NotFound("3017620422003"), {}, {}, {}, {}, {}, allowCustom = false) } }
        rule.onNodeWithText("Scan again").assertIsDisplayed()
        rule.onNodeWithText("Create custom food").assertDoesNotExist()
    }

    @Test fun scannerInAppModeStillOffersCreateCustomFood() {
        rule.setContent { LhTheme(darkTheme = false) { ScanScreen(ScanUi.NotFound("3017620422003"), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Create custom food").assertIsDisplayed()
        rule.onNodeWithText("Scan again").assertIsDisplayed()
    }
}
