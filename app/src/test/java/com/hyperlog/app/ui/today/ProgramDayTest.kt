package com.hyperlog.app.ui.today

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.program.ProgramStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Today runs the program's next day: its slots, the planned sets, the sheet, and the next day after. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ProgramDayTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    // The pad follows the logger's saved state, which lands off the main thread: wait for the key to show.
    private fun key(description: String) = compose.also { c ->
        c.waitUntil(5_000) { c.onAllNodes(androidx.compose.ui.test.hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
    }.onNodeWithContentDescription(description).performClick().also { compose.waitForIdle() }

    private fun importProgram() {
        waitFor("Today")
        val result = runBlocking { ProgramStore.get(ApplicationProvider.getApplicationContext<Application>()).import(TestPrograms.twoDays("Test")) }
        assertEquals(com.hyperlog.app.program.ProgramParser.Result.Valid::class, result::class)
        waitFor("Day A")
    }

    @Test
    fun todayShowsTheNextDayWithItsSlotsAndSets() {
        importProgram()
        compose.onNodeWithText("Week 1 · Main").assertIsDisplayed()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        // Week 1's ramp-in is one RIR easier than written.
        compose.onNodeWithText("1 × 4–6 @ 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 × 6–8 @ 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ad-hoc session").assertIsDisplayed()
    }

    @Test
    fun theSessionShowsEveryPlannedSetWithBackOffWeightsFromTheTopSet() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        waitFor("Target")
        compose.onNodeWithText("Back-off, top set − 10%").assertIsDisplayed()
        key("1"); key("0"); key("0")
        // The back-off rows follow the top set's weight as it is typed: 100 − 10% = 90.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("90").fetchSemanticsNodes().size == 2 }
    }

    @Test
    fun thePrescriptionSheetShowsEverySetTheRestAndBothNotes() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        waitFor("Target")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        waitFor("Same grip, setup and pause every rep.")
        compose.onNodeWithText("Rest 3:00").assertIsDisplayed()
        // Both back-off sets in the sheet, and the rule heading them in the table behind it.
        assertEquals(3, compose.onAllNodesWithText("Back-off, top set − 10%").fetchSemanticsNodes().size)
    }

    @Test
    fun finishingTheDayPutsTheNextDayOnToday() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        waitFor("Target")
        key("8"); key("0"); key("Next"); key("Log set")
        // The first back-off is up next: 80 − 10% = 72, rounded down to 70 on the plates.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("70").fetchSemanticsNodes().isNotEmpty() }
        key("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        waitFor("Done")
        compose.onNodeWithText("Done").performClick()
        waitFor("Day B")
        compose.onNodeWithText("Leg Extension").assertIsDisplayed()
    }

    @Test
    fun theProgramScreenShowsTheWeeksAndWhereYouAre() {
        importProgram()
        key("Program")
        waitFor("Main · weeks 1–2")
        compose.onNode(hasContentDescription("Day 1A, next")).assertIsDisplayed()
        compose.onNode(hasContentDescription("Day 2B")).assertIsDisplayed()
    }
}
