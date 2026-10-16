package com.hyperlog.app.ui.coach

import com.hyperlog.app.openTab
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.assertButtonOrder
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
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

    private fun programAtWeek(week: Int) {
        compose.waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        val db = HyperlogDatabase.get(ApplicationProvider.getApplicationContext())
        runBlocking { db.programs().setPosition(db.programs().active()!!.id, week, 0) }
        compose.openTab("Program")
        compose.waitFor("Week $week · Day A", substring = true)
    }

    /** A program action, from the menu. */
    private fun action(label: String) {
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test
    fun shiftingTheProgramByOneWeekShowsADiffAndAppliesOnlyAfterAccept() {
        programAtWeek(3)
        action("Shift program")
        compose.onNodeWithText("1 week later").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()

        compose.waitFor("Change to your program")
        compose.onNodeWithText("The plan 1 week later").assertIsDisplayed()
        compose.onNodeWithText("Rest of the program").assertIsDisplayed()
        compose.onAllNodesWithText("Week 3 · Day A", substring = true)[0].assertIsDisplayed()
        compose.onAllNodesWithText("Week 2 · Day A", substring = true)[0].assertIsDisplayed()
        compose.onNodeWithText("Reject").performClick()

        // Rejected: the program is where it was.
        compose.waitGone("Change to your program")
        compose.onAllNodesWithText("Week 3 · Day A", substring = true)[0].assertIsDisplayed()

        action("Shift program")
        compose.onNodeWithText("Review").performClick()
        compose.waitFor("Accept")
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 2 · Day A", substring = true)
        compose.onNodeWithText("Changes · 1 in force").performClick()
        compose.onNodeWithText("The plan 1 week later").assertIsDisplayed()
    }

    @Test
    fun revertingAnAcceptedChangeRestoresThePlan() {
        programAtWeek(3)
        action("Skip day")
        compose.waitFor("Accept")
        compose.onNodeWithText("Skip week 3 · Day A", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 3 · Day B", substring = true)

        compose.onNodeWithText("Changes · 1 in force").performClick()
        compose.onNodeWithContentDescription("Revert").performClick()
        compose.onNodeWithText("Revert “Skip week 3 · Day A", substring = true).assertIsDisplayed()
        compose.assertButtonOrder("Cancel", "Revert")
        compose.onNodeWithText("Revert").performClick()
        compose.waitFor("Reverted", substring = true)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Changes · none in force")
        compose.onAllNodesWithText("Week 3 · Day A", substring = true)[0].assertIsDisplayed()
    }
}
