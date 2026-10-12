package com.hyperlog.app.ui.session

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The owner's phone: 1280 × 2856 px at 3.125 density.
@Config(qualifiers = "w410dp-h914dp")
class SessionScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    // The pad follows the logger's saved state, which lands off the main thread: wait for the key to show.
    private fun key(description: String): SemanticsNodeInteraction = compose.also { c ->
        c.waitUntil(5_000) { c.onAllNodes(androidx.compose.ui.test.hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
    }.onNodeWithContentDescription(description)

    private fun tap(vararg descriptions: String) = descriptions.forEach {
        key(it).performClick()
        compose.waitForIdle()
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    /** The logged row and the next set's prefill both read 60. */
    private fun waitForLoggedSet() = compose.waitUntil(5_000) {
        compose.onAllNodesWithText("60").fetchSemanticsNodes().size == 2
    }

    private fun startWithBenchPress() {
        waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        waitFor("Add exercise")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Log set").or(hasContentDescription("Next"))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun logsASetFromThePad() {
        startWithBenchPress()

        tap("6", "0", "Next", "1", "2", "RIR 1", "Log set")

        waitForLoggedSet()
        compose.onAllNodesWithText("12").assertCountEquals(2)
        compose.onNodeWithContentDescription("RIR 1").assertIsSelected()
    }

    @Test
    fun theDoneKeyIsTheSameSizeAsTheOtherKeys() {
        startWithBenchPress()

        val done = compose.onNodeWithTag(DoneKeyTag).getBoundsInRoot()
        val digit = key("0").getBoundsInRoot()
        assertEquals(digit.width.value, done.width.value, 1f)
        assertEquals(digit.height.value, done.height.value, 0.5f)
        // ✓ sits in the bottom row, beside 0 and the hide key.
        assertEquals(digit.top, done.top)
        assertEquals(digit.top, key("Hide keypad").getBoundsInRoot().top)
    }

    @Test
    fun theHideKeyFoldsThePadToShowAndLog() {
        startWithBenchPress()

        tap("Hide keypad")

        key("Show keypad").assertIsDisplayed()
        compose.onNodeWithTag(DoneKeyTag).assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("1")).assertCountEquals(0)
        tap("Show keypad")
        key("1").assertIsDisplayed()
    }

    @Test
    fun backAsksBeforeLeavingAndLeavingKeepsTheSessionRunning() {
        startWithBenchPress()
        tap("6", "0", "Next", "1", "2", "RIR 1", "Log set")
        waitForLoggedSet()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Leave the session?").assertIsDisplayed()
        compose.onNodeWithText("Stay").performClick()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Leave").performClick()
        waitFor("Resume session")
        compose.onNodeWithText("Resume session").performClick()

        waitFor("Barbell Bench Press")
        compose.onAllNodesWithText("60").assertCountEquals(2)
    }

    @Test
    fun finishingAsksFirstAndSessionDoneCanResumeTheSession() {
        startWithBenchPress()
        tap("6", "0", "Next", "1", "2", "RIR 1", "Log set")
        waitForLoggedSet()

        tap("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish the session?").assertIsDisplayed()
        compose.onNodeWithText("Keep going").performClick()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()

        tap("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        waitFor("Session done")

        compose.onNodeWithText("Resume session").performClick()
        waitFor("Barbell Bench Press")
        compose.onAllNodesWithText("60").assertCountEquals(2)
    }
}
