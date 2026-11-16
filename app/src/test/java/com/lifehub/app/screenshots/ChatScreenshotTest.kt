package com.lifehub.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.copilot.agent.DailySummaryCard
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.feature.copilot.ChatItem
import com.lifehub.app.feature.copilot.ChatProblem
import com.lifehub.app.feature.copilot.ChatScreen
import com.lifehub.app.feature.copilot.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class ChatScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 16)
    private val card = DailySummaryCard(
        date = "2026-11-16", kcal = 1450, proteinG = 51, carbsG = 160, fatG = 48,
        kcalTarget = 2200, proteinTargetG = 150, fatTargetG = 70,
        kcalRemaining = 750, proteinRemainingG = 99, fatRemainingG = 22, items = 5,
    )
    private val conversation = ChatUiState(
        today,
        listOf(
            ChatItem.User("u1", "Quante proteine mi mancano oggi?"),
            ChatItem.Card("t1", card),
            ChatItem.Assistant("a1", "Oggi ti mancano 99 g di proteine: ne hai mangiati 51 su 150."),
        ),
    )

    @Test fun chatWithSummaryCardLight() {
        var opened: LocalDate? = null
        rule.setContent { LhTheme(darkTheme = false) { ChatScreen(conversation, onBack = {}, onOpenDiary = { opened = it }) } }
        rule.onNodeWithText("99 g left").assertIsDisplayed()
        rule.onNodeWithText("51 / 150 g").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-chat-light.png")
        rule.onNodeWithTag("card-open-diary").performClick()
        assertEquals(today, opened)
    }

    @Test fun chatWorkingAndNetworkErrorDark() {
        rule.setContent { LhTheme(darkTheme = true) {
            ChatScreen(conversation.copy(problem = ChatProblem.Failed("Couldn't reach the LLM server. Check your connection and the base URL.", retryable = true, network = true)), onBack = {})
        } }
        rule.onNodeWithTag("chat-retry").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-chat-dark-error.png")
    }

    @Test fun chatMissingKeyLight() {
        var settings = 0
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(ChatUiState(today, problem = ChatProblem.MissingKey), onBack = {}, onOpenSettings = { settings++ }, initialDraft = "Ciao")
        } }
        rule.onNodeWithTag("chat-send").assertIsNotEnabled()
        rule.onRoot().captureRoboImage("build/screens/copilot-chat-missing-key.png")
        rule.onNodeWithTag("chat-open-settings").performClick()
        assertEquals(1, settings)
    }

    @Test fun chatEmptyThinkingLight() {
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(ChatUiState(today, listOf(ChatItem.User("u1", "Cosa mi aspetta questa settimana?")), working = true), onBack = {})
        } }
        rule.onNodeWithTag("chat-working").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-chat-thinking.png")
    }
}
