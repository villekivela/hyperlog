package com.hyperlog.app.ui.coach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Program screen's actions are Changes: reviewed as a diff, applied on accept, reverted from the list. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ProgramChangesTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitGone(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }

    private fun programAtWeek(week: Int) {
        waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        waitFor("Week 1 · Ramp-in")
        val db = HyperlogDatabase.get(ApplicationProvider.getApplicationContext())
        runBlocking { db.programs().setPosition(db.programs().active()!!.id, week, 0) }
        compose.onNodeWithContentDescription("Program").performClick()
        waitFor("Week $week · Day A", substring = true)
    }

    @Test
    fun shiftingTheProgramByOneWeekShowsADiffAndAppliesOnlyAfterAccept() {
        programAtWeek(3)
        compose.onNodeWithText("Shift plan").performClick()
        compose.onNodeWithText("1 week later").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()

        waitFor("Review change")
        compose.onNodeWithText("The plan 1 week later").assertIsDisplayed()
        compose.onNodeWithText("Rest of the program").assertIsDisplayed()
        compose.onNodeWithText("Week 3 · Day A", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Week 2 · Day A", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reject").performClick()

        // Rejected: the program is where it was.
        waitGone("Review change")
        compose.onNodeWithText("Week 3 · Day A", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Shift plan").performClick()
        compose.onNodeWithText("Review").performClick()
        waitFor("Accept")
        compose.onNodeWithText("Accept").performClick()
        waitFor("Week 2 · Day A", substring = true)
        compose.onNodeWithText("Changes").assertIsDisplayed()
        compose.onNodeWithText("The plan 1 week later").assertIsDisplayed()
    }

    @Test
    fun revertingAnAcceptedChangeRestoresThePlan() {
        programAtWeek(3)
        compose.onNodeWithText("Skip day").performClick()
        waitFor("Accept")
        compose.onNodeWithText("Skip week 3 · Day A", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()
        waitFor("Week 3 · Day B", substring = true)

        compose.onNodeWithContentDescription("Revert").performClick()
        compose.onNodeWithText("Revert “Skip week 3 · Day A", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Revert").performClick()
        waitFor("Reverted")
        compose.onNodeWithText("Week 3 · Day A", substring = true).assertIsDisplayed()
    }
}
