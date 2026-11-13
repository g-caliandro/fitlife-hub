package com.lifehub.app.navigation

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.lifehub.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** In-screen cross-tab links must behave exactly like the bottom-bar tabs (regression: Home -> See diary -> Home). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class TabNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun has(m: SemanticsMatcher) = rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(m: SemanticsMatcher) = rule.waitUntil(20_000) { has(m) }
    private fun waitGone(m: SemanticsMatcher) = rule.waitUntil(20_000) { !has(m) }
    private fun tab(name: String) = rule.onNodeWithTag("tab-$name").performClick()

    private fun assertHome() {
        waitFor(hasTestTag("home-list"))
        waitGone(hasTestTag("calendar-scroll"))
    }

    private fun assertDiary() {
        waitFor(hasTestTag("calendar-scroll"))
        waitGone(hasTestTag("home-list"))
    }

    @Test
    fun seeDiaryThenHomeTabReturnsHome() {
        assertHome()
        rule.onNodeWithText("See diary").performScrollTo().performClick()
        assertDiary()
        tab("Home")
        assertHome()
        // Twice, now that Diary has saved state, and through the real Diary tab too.
        rule.onNodeWithText("See diary").performScrollTo().performClick()
        assertDiary()
        tab("Home")
        assertHome()
        tab("Diary")
        assertDiary()
        tab("Home")
        assertHome()
    }

    @Test
    fun crossTabLinkKeepsSingleTabRootOnStack() {
        assertHome()
        rule.onNodeWithText("See diary").performScrollTo().performClick()
        assertDiary()
        tab("Trends")
        waitFor(hasTestTag("trends-list"))
        tab("Diary")
        assertDiary()
        tab("Home")
        assertHome()
    }
}
