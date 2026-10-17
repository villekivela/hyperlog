package com.hyperlog.app.ui.today

import androidx.compose.ui.test.hasScrollToIndexAction
import com.hyperlog.app.onScreen
import androidx.compose.ui.test.performScrollToNode
import com.hyperlog.app.openTab
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.press
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitForCount
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

    private fun importProgram() {
        compose.waitFor("Today")
        val result = runBlocking { ProgramStore.get(ApplicationProvider.getApplicationContext<Application>()).import(TestPrograms.twoDays("Test")) }
        assertEquals(com.hyperlog.app.program.ProgramParser.Result.Valid::class, result::class)
        compose.waitFor("Day A")
    }

    @Test
    fun todayShowsTheNextDayWithItsSlotsAndSets() {
        importProgram()
        compose.onNodeWithText("Week 1 · Main").assertIsDisplayed()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        // Week 1's ramp-in is one RIR easier than written.
        compose.onNodeWithText("1 × 4–6\u00A02 RIR", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 × 6–8\u00A02 RIR", substring = true).assertIsDisplayed()
        // Planned sets are targets: outlined RIR chips, and no `@` left on screen.
        compose.onAllNodesWithTag(com.hyperlog.app.ui.session.RirChipTargetTag, useUnmergedTree = true).onFirst().assertExists()
        compose.onAllNodesWithText("@", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Ad-hoc session").assertIsDisplayed()
    }

    @Test
    fun theSessionShowsEveryPlannedSetWithBackOffWeightsFromTheTopSet() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
        compose.press("1"); compose.press("0"); compose.press("0")
        // The back-off rows follow the top set's weight as it is typed: 100 − 10% = 90. The second
        // may sit below the pad's fold, at the table's end.
        compose.waitFor("90")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Add set"))
        compose.waitForCount("90", 2)
    }

    @Test
    fun thePrescriptionSheetShowsEverySetTheRestAndBothNotes() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        compose.waitFor("Same grip, setup and pause every rep.")
        compose.onNodeWithText("Rest 3:00").assertIsDisplayed()
        // Both back-off sets in the sheet; the table marks none (their lowered weight shows it).
        assertEquals(2, compose.onAllNodesWithText("Back-off, top set − 10%").fetchSemanticsNodes().size)
    }

    @Test
    fun finishingTheDayPutsTheNextDayOnToday() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
        compose.press("8"); compose.press("0"); compose.press("Next"); compose.press("6"); compose.press("Log set")
        // The first back-off is up next: 80 − 10% = 72, rounded down to 70 on the plates.
        compose.waitFor("70")
        compose.press("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitFor("Done")
        compose.onNodeWithText("Done").performClick()
        // Today shows the finished session, and points to the next day.
        compose.waitFor("Next: Day B on", substring = true)
        compose.onNodeWithText("Next: Day B on", substring = true).performClick()
        compose.waitFor("Leg Extension")
    }

    @Test
    fun theProgramScreenShowsTheDoneDayAsLoggedAndOpensItsSession() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
        compose.press("8"); compose.press("0"); compose.press("Next"); compose.press("6"); compose.press("Log set")
        compose.waitFor("70")
        compose.press("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitFor("Done")
        compose.onNodeWithText("Done").performClick()
        compose.waitFor("Next: Day B on", substring = true)

        compose.openTab("Program")
        compose.waitFor("Main · weeks 1–2")
        compose.waitFor(onScreen("80 × 6"), substring = true)
        compose.onNodeWithText(onScreen("80 × 6"), substring = true).performClick()
        compose.waitFor("Barbell Bench Press")
        compose.onNodeWithText("Main · weeks 1–2").assertDoesNotExist()
        // Back returns to Program, where the session was opened from.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Main · weeks 1–2")
    }

    @Test
    fun aSessionResumedAfterFinishingKeepsItsDayOnToday() {
        importProgram()
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
        compose.press("8"); compose.press("0"); compose.press("Next"); compose.press("6"); compose.press("Log set")
        compose.waitFor("70")
        compose.press("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitFor("Resume session")
        compose.onNodeWithText("Resume session").performClick()
        compose.waitFor("Reps")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Leave").performClick()
        // Today shows the day Resume opens, not the one after it.
        compose.waitFor("Resume session")
        compose.onNodeWithText("Day A").assertIsDisplayed()
        compose.onNodeWithText("Day B").assertDoesNotExist()
    }
}
