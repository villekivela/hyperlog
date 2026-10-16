package com.hyperlog.app.ui.session

import com.hyperlog.app.await
import com.hyperlog.app.openTab
import com.hyperlog.app.navItem
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.key
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitForCount
import com.hyperlog.app.waitForDescription
import com.hyperlog.app.waitForNoDescription
import com.hyperlog.app.waitForTappable
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

    /** Taps keys by their description; "kg" is the weight cell of the row in the pad. */
    private fun tap(vararg descriptions: String) = descriptions.forEach {
        (if (it == "kg") compose.onNode(clickLabel("kg"), useUnmergedTree = true) else compose.key(it)).performClick()
        compose.waitForIdle()
        // Loading a row reads it from the database first.
        if (it.startsWith("Correct ")) compose.waitForDescription("Cancel correction")
        if (it == "Save correction") compose.waitForNoDescription("Cancel correction")
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun startAndLog205() {
        compose.waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Add exercise")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        compose.waitForDescription("Next")
        tap("2", "0", "5", "Next", "8", "RIR 2", "Log set")
        compose.waitForCount("205", 2)
    }

    @Test
    fun tappingALoggedSetLoadsItIntoThePadAndTickSavesIt() {
        startAndLog205()

        tap("Correct set 1")
        compose.key("Cancel correction").assertIsDisplayed()
        tap("kg", "2", "5", "Next", "Save correction")

        // The set reads 25; the set to do follows it.
        compose.waitForCount("25", 2)
        compose.onAllNodesWithText("205").assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Cancel correction")).assertCountEquals(0)
    }

    @Test
    fun theCancelKeyAndBackLeaveTheSetAsItWas() {
        startAndLog205()

        tap("Correct set 1", "kg", "2", "5", "Cancel correction")
        compose.waitForNoDescription("Cancel correction")
        compose.onAllNodesWithText("205").assertCountEquals(2)

        tap("Correct set 1")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForNoDescription("Cancel correction")
        // Back cancelled the correction rather than asking to leave.
        compose.onAllNodesWithText("Leave the session?").assertCountEquals(0)
    }

    @Test
    fun sessionDoneOpensAnExerciseToCorrect() {
        startAndLog205()
        tap("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitFor("Session done")

        compose.waitForTappable("Barbell Bench Press")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        compose.waitForDescription("Correct set 1")
        tap("Correct set 1", "kg", "2", "5", "Next", "Save correction")
        compose.waitFor("25")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitFor("Session done")
        compose.onNodeWithText("25 × 8 2 RIR").assertIsDisplayed()
    }

    @Test
    fun pastSessionsOpenFromHistoryToCorrect() {
        startAndLog205()
        tap("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitFor("Session done")
        compose.onNodeWithText("Done").performClick()

        compose.await(navItem("History"))
        compose.openTab("History")
        compose.waitFor("Ad-hoc session")
        compose.onNodeWithText("Ad-hoc session").performClick()
        compose.waitForTappable("Barbell Bench Press")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        compose.waitForDescription("Correct set 1")
        tap("Correct set 1", "kg", "2", "5", "Next", "Save correction")
        compose.waitFor("25")
    }
}
