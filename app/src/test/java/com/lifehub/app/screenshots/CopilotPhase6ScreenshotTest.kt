package com.lifehub.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.copilot.agent.DraftExerciseView
import com.lifehub.app.copilot.agent.DraftPlanView
import com.lifehub.app.copilot.agent.MealSuggestionCard
import com.lifehub.app.copilot.agent.PlanDraftCard
import com.lifehub.app.copilot.agent.SuggestionItemView
import com.lifehub.app.copilot.food.FoodCandidate
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.data.model.CaptureStatus
import com.lifehub.app.data.model.ChatOrigin
import com.lifehub.app.data.model.EntrySource
import com.lifehub.app.data.model.PlanDraftStatus
import com.lifehub.app.feature.copilot.CardActions
import com.lifehub.app.feature.copilot.ChatItem
import com.lifehub.app.feature.copilot.ChatScreen
import com.lifehub.app.feature.copilot.ChatUiState
import com.lifehub.app.feature.copilot.DraftLive
import com.lifehub.app.feature.copilot.ThreadRow
import com.lifehub.app.feature.copilot.ThreadsScreen
import com.lifehub.app.feature.copilot.ThreadsUiState
import com.lifehub.app.feature.plans.DraftRowModel
import com.lifehub.app.feature.plans.PlanDraftScreen
import com.lifehub.app.feature.plans.PlanDraftUiState
import com.lifehub.app.feature.plans.PlanRowModel
import com.lifehub.app.feature.plans.PlansScreen
import com.lifehub.app.feature.plans.PlansUiState
import com.lifehub.app.feature.settings.MemoryRow
import com.lifehub.app.feature.settings.MemoryUiState
import com.lifehub.app.feature.settings.UserMemoryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Phase 6 UI: plan draft card + approve dialog, draft in Train and its screen, meal suggestion card, memory, conversations. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class CopilotPhase6ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 16)
    private val zone = ZoneId.of("Europe/Rome")

    private val draft = PlanDraftCard(
        "d1", "Verso il muscle up", "Muscle up pulito", 4,
        listOf(
            DraftPlanView("Pull A", "Mon · Fri", listOf(
                DraftExerciseView("Pull-up", "5 × 6"), DraftExerciseView("Muscle up negativo", "3 × 3", isNew = true),
                DraftExerciseView("Hollow hold", "3 × 30 s", isNew = true),
            )),
            DraftPlanView("Push A", "Wed", listOf(DraftExerciseView("Dip", "4 × 8 +10 kg"))),
        ),
    )

    private fun cand(name: String, k: Double, p: Double, c: Double, f: Double) = FoodCandidate("food:$name", name, null, EntrySource.HISTORY, null, k, p, c, f)
    private val suggestion = MealSuggestionCard(
        "s1", "Pollo con riso e zucchine", "2026-11-16",
        listOf(
            SuggestionItemView("Petto di pollo", null, 180.0, 198.0, 41.94, 0.0, 2.88, "history", cand("Petto di pollo", 110.0, 23.3, 0.0, 1.6)),
            SuggestionItemView("Riso", null, 70.0, 252.0, 4.9, 55.3, 0.42, "history", cand("Riso", 360.0, 7.0, 79.0, 0.6)),
            SuggestionItemView("Zucchine", null, 200.0, 34.0, 2.42, 6.22, 0.64, "base_table", cand("Zucchine", 17.0, 1.21, 3.11, 0.32)),
        ),
        totalKcal = 484.0, totalProteinG = 49.26, totalCarbsG = 61.52, totalFatG = 3.94, kcalRemaining = 1291, proteinRemainingG = 99, fatRemainingG = 52,
    )

    @Test fun planDraftCardApproveDialogWithArchiveCheckbox() {
        var approved: Pair<String, Boolean>? = null; var rejected: String? = null; var opened: String? = null
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(
                ChatUiState(today, listOf(ChatItem.User("u", "Impostami 4 settimane verso il muscle up"), ChatItem.Card("c", draft),
                    ChatItem.Assistant("a", "Ho salvato una bozza: approvala se ti va bene.")),
                    drafts = mapOf("d1" to DraftLive(PlanDraftStatus.DRAFT, listOf("Pull day")))),
                onBack = {}, onApproveDraft = { id, archive -> approved = id to archive },
                actions = CardActions(onRejectDraft = { rejected = it }, onOpenDraft = { opened = it }),
            )
        } }
        rule.onNodeWithTag("card-plan-draft").assertIsDisplayed()
        rule.onNodeWithText("Not active").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-plan-draft-light.png")
        rule.onNodeWithTag("draft-reject").performClick(); assertEquals("d1", rejected)
        rule.onNodeWithTag("draft-open").performClick(); assertEquals("d1", opened)
        rule.onNodeWithTag("draft-approve").performClick()
        rule.onNodeWithText("Archive plans on the same days").assertIsDisplayed()
        rule.onNodeWithText("Pull day").assertIsDisplayed()
        rule.onAllNodes(isRoot())[1].captureRoboImage("build/screens/copilot-plan-approve-dialog.png")
        rule.onNodeWithTag("approve-confirm").performClick()
        assertEquals("d1" to false, approved)   // opt-in: unchecked by default
        rule.onNodeWithTag("draft-approve").performClick()
        rule.onNodeWithTag("approve-archive").performClick()
        rule.onNodeWithTag("approve-confirm").performClick()
        assertEquals("d1" to true, approved)
    }

    @Test fun approvedDraftCardDark() {
        rule.setContent { LhTheme(darkTheme = true) {
            ChatScreen(ChatUiState(today, listOf(ChatItem.Card("c", draft)), drafts = mapOf("d1" to DraftLive(PlanDraftStatus.APPROVED))), onBack = {})
        } }
        rule.onNodeWithText("Plan approved").assertIsDisplayed()
        rule.onNodeWithTag("draft-approve").assertDoesNotExist()
        rule.onRoot().captureRoboImage("build/screens/copilot-plan-draft-approved-dark.png")
    }

    @Test fun trainScreenShowsTheDraftAndTheDraftScreenOffersApproveRejectDelete() {
        var opened: String? = null
        rule.setContent { LhTheme(darkTheme = false) {
            PlansScreen(
                PlansUiState(listOf(PlanRowModel("p1", "Pull day", setOf(1, 4), 1, listOf("Muscle up"), false)), todayIso = 1,
                    drafts = listOf(DraftRowModel("d1", "Verso il muscle up", 2, setOf(1, 3, 5)))),
                onOpen = {}, onAdd = {}, onStart = {}, onExercises = {}, onOpenDraft = { opened = it },
            )
        } }
        rule.onNodeWithText("Drafts from the copilot").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-train-drafts.png")
        rule.onNodeWithTag("draft-row-d1").performClick(); assertEquals("d1", opened)
    }

    @Test fun draftScreen() {
        var deleted = false
        rule.setContent { LhTheme(darkTheme = true) {
            PlanDraftScreen(PlanDraftUiState(true, draft, PlanDraftStatus.DRAFT, listOf("Pull day")), onBack = {}, onDelete = { deleted = true })
        } }
        rule.onNodeWithTag("draft-approve").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-plan-draft-screen-dark.png")
        rule.onNodeWithTag("draft-delete").performClick()
        rule.onNodeWithText("Delete this draft?").assertIsDisplayed()
        rule.onAllNodesWithText("Delete")[1].performClick(); assertEquals(true, deleted)
    }

    @Test fun mealSuggestionCardLogAsMealThenLogged() {
        var logged: MealSuggestionCard? = null; var undo: String? = null
        rule.setContent { LhTheme(darkTheme = true) {
            ChatScreen(ChatUiState(today, listOf(ChatItem.User("u", "Cosa preparo per cena?"), ChatItem.Card("c", suggestion))), onBack = {},
                actions = CardActions(onLogSuggestion = { logged = it }, onUndo = { undo = it }))
        } }
        rule.onNodeWithText("Meal suggestion").assertIsDisplayed()
        rule.onNodeWithTag("suggestion-coverage").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("50% of the 99 g protein left").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-meal-suggestion-dark.png")
        rule.onNodeWithTag("suggestion-log").performClick(); assertEquals("s1", logged?.suggestionId)
    }

    @Test fun mealSuggestionLoggedShowsUndo() {
        var undo: String? = null
        rule.setContent { LhTheme(darkTheme = false) {
            ChatScreen(ChatUiState(today, listOf(ChatItem.Card("c", suggestion)), captureStatus = mapOf("sugg-s1" to CaptureStatus.DONE)), onBack = {},
                actions = CardActions(onUndo = { undo = it }))
        } }
        rule.onNodeWithText("Suggestion logged").assertIsDisplayed()
        rule.onNodeWithTag("suggestion-log").assertDoesNotExist()
        rule.onNodeWithTag("card-undo").performScrollTo().performClick(); assertEquals("sugg-s1", undo)
    }

    @Test fun memoryScreen() {
        val rows = listOf(
            MemoryRow("disliked_foods", "funghi, tonno", "16 Nov 2026"), MemoryRow("equipment", "sbarra, anelli, parallele", "12 Nov 2026"),
            MemoryRow("level_pull_up", "10 trazioni pulite", "16 Nov 2026"), MemoryRow("training_days", "lunedì, mercoledì, venerdì", "2 Nov 2026"),
        )
        rule.setContent { LhTheme(darkTheme = false) { UserMemoryScreen(MemoryUiState(rows, loaded = true), onBack = {}) } }
        rule.onNodeWithTag("memory-list").assertIsDisplayed()
        rule.onNodeWithText("funghi, tonno").assertIsDisplayed()
        rule.onNodeWithTag("memory-add").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-memory-light.png")
        // The edit dialog (text fields in a Dialog) never idles under Robolectric: edit/delete are covered by UserMemoryViewModelTest.
    }

    @Test fun memoryScreenEmptyDark() {
        rule.setContent { LhTheme(darkTheme = true) { UserMemoryScreen(MemoryUiState(emptyList(), loaded = true), onBack = {}) } }
        rule.onNodeWithTag("memory-empty").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-memory-empty-dark.png")
    }

    @Test fun threadsList() {
        val now = Instant.parse("2026-11-16T13:05:00Z")
        val rows = listOf(
            ThreadRow("t1", "Mezzo merluzzo Findus, il solito", now, ChatOrigin.QUICK, 1),
            ThreadRow("t2", "Impostami 4 settimane verso il muscle up", now.minusSeconds(7200), ChatOrigin.CHAT, 0),
            ThreadRow("t3", "Quante proteine mi mancano oggi?", now.minusSeconds(90_000), ChatOrigin.CHAT, 0),
        )
        var opened: String? = null; var deleted: String? = null; var new = false
        rule.setContent { LhTheme(darkTheme = false) {
            ThreadsScreen(ThreadsUiState(rows, today, zone, loaded = true), onBack = {}, onOpen = { opened = it }, onNew = { new = true }, onDelete = { deleted = it })
        } }
        rule.onNodeWithTag("thread-badge-t1", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Yesterday · 13:05").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-threads-light.png")
        rule.onNodeWithTag("thread-row-t2").performClick(); assertEquals("t2", opened)
        rule.onNodeWithTag("threads-new").performClick(); assertEquals(true, new)
        rule.onNodeWithTag("thread-delete-t1").performClick()
        rule.onNodeWithText("Its 1 open question(s) will be discarded.", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("Delete")[0].performClick(); assertEquals("t1", deleted)
    }

    @Test fun threadsEmptyDark() {
        rule.setContent { LhTheme(darkTheme = true) { ThreadsScreen(ThreadsUiState(emptyList(), today, zone, loaded = true), onBack = {}) } }
        rule.onNodeWithText("No conversations yet").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/copilot-threads-empty-dark.png")
    }
}
