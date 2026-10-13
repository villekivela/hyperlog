package com.hyperlog.app.ui.session

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Corrections on screen (docs/design/logging-screen.md, Corrections). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class CorrectionScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    // The pad follows the logger's saved state, which lands off the main thread: wait for the key to show.
    private fun key(description: String): SemanticsNodeInteraction = compose.also { c ->
        c.waitUntil(5_000) { c.onAllNodes(androidx.compose.ui.test.hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
    }.onNodeWithContentDescription(description)

    /** Taps keys by their description; "kg" is the weight cell of the row in the pad. */
    private fun tap(vararg descriptions: String) = descriptions.forEach {
        (if (it == "kg") compose.onNode(clickLabel("kg"), useUnmergedTree = true) else key(it)).performClick()
        compose.waitForIdle()
        // Loading a row reads it from the database first.
        if (it.startsWith("Correct ")) waitForDescription("Cancel correction")
        if (it == "Save correction") waitForNo("Cancel correction")
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    /** Waits until [text] can be tapped (Session done reads the session back first). */
    private fun waitForTappable(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text).and(hasClickAction())).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForDescription(description: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForNo(description: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isEmpty()
    }

    private fun startAndLog205() {
        waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        waitFor("Add exercise")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        waitForDescription("Next")
        tap("2", "0", "5", "Next", "8", "RIR 2", "Log set")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("205").fetchSemanticsNodes().size == 2 }
    }

    @Test
    fun tappingALoggedSetLoadsItIntoThePadAndTickSavesIt() {
        startAndLog205()

        tap("Correct set 1")
        key("Cancel correction").assertIsDisplayed()
        tap("kg", "2", "5", "Next", "Save correction")

        // The set reads 25; the set to do follows it.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("25").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("205").assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Cancel correction")).assertCountEquals(0)
    }

    @Test
    fun theCancelKeyAndBackLeaveTheSetAsItWas() {
        startAndLog205()

        tap("Correct set 1", "kg", "2", "5", "Cancel correction")
        waitForNo("Cancel correction")
        compose.onAllNodesWithText("205").assertCountEquals(2)

        tap("Correct set 1")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForNo("Cancel correction")
        // Back cancelled the correction rather than asking to leave.
        compose.onAllNodesWithText("Leave the session?").assertCountEquals(0)
    }

    @Test
    fun sessionDoneOpensAnExerciseToCorrect() {
        startAndLog205()
        tap("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        waitFor("Session done")

        waitForTappable("Barbell Bench Press")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        waitForDescription("Correct set 1")
        tap("Correct set 1", "kg", "2", "5", "Next", "Save correction")
        waitFor("25")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("Session done")
        compose.onNodeWithText("25 × 8 @ 2").assertIsDisplayed()
    }

    @Test
    fun pastSessionsOpenFromHistoryToCorrect() {
        startAndLog205()
        tap("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        waitFor("Session done")
        compose.onNodeWithText("Done").performClick()

        waitForDescription("History")
        tap("History")
        waitFor("Ad-hoc session")
        compose.onNodeWithText("Ad-hoc session").performClick()
        waitForTappable("Barbell Bench Press")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        waitForDescription("Correct set 1")
        tap("Correct set 1", "kg", "2", "5", "Next", "Save correction")
        waitFor("25")
    }
}
