package com.hyperlog.app.ui.library

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class LibraryScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitGone(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun openLibrary() {
        waitFor("Today")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("173 exercises")
        click("Exercise library")
        waitFor("Search 173 exercises")
    }

    private fun search(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun browsesAndSearchesTheLibrary() {
        openLibrary()
        compose.onNodeWithText("1-Arm Cable Pulldown").assertIsDisplayed()
        compose.onAllNodesWithText("Cable · Lats")[0].assertIsDisplayed()

        search("pec deck")
        compose.onNodeWithText("Pec Deck").assertIsDisplayed()
        compose.onNodeWithText("1-Arm Reverse Pec Deck").assertIsDisplayed()
        compose.onAllNodesWithText("1-Arm Cable Pulldown").assertCountIs(0)
    }

    @Test
    fun addsAnExercise() {
        openLibrary()
        compose.onNodeWithText("New exercise", useUnmergedTree = true).performClick()
        waitFor("Save")
        compose.onNodeWithText("Save").assertIsNotEnabled()

        compose.onNode(hasText("Name").and(hasSetTextAction())).performTextInput("Landmine Press")
        click("Barbell")
        compose.onNodeWithContentDescription("Movement pattern").performClick()
        click("Vertical push")
        click("Low")
        compose.onAllNodesWithText("Add")[0].performClick()
        click("Front Delts")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()

        waitFor("Exercise library")
        search("landmine")
        waitFor("Landmine Press")
        compose.onNodeWithText("Barbell · Front Delts").assertIsDisplayed()
    }

    @Test
    fun editsAnExercise() {
        openLibrary()
        search("pec deck")
        click("Pec Deck")
        waitFor("Edit exercise")

        compose.onNode(hasText("Focus on bringing your inner elbows together - not your hands."))
            .performTextReplacement("Elbows high, squeeze for a second.")
        compose.onNode(hasText("https://youtu.be/-EIhKMDSjBY?t=151")).performTextReplacement("https://youtu.be/-EIhKMDSjBY?t=200")
        compose.onNodeWithText("Opens at 3:20").assertIsDisplayed()
        click("Save")

        waitFor("Exercise library")
        click("Pec Deck")
        waitFor("Elbows high, squeeze for a second.")
    }

    @Test
    fun aHiddenExerciseIsNoLongerOfferedInASession() {
        openLibrary()
        search("pec deck")
        click("Pec Deck")
        waitFor("Hide exercise")
        compose.onNodeWithText("Hide exercise").performScrollTo().performClick()
        waitFor("Show exercise again")
        compose.onNodeWithContentDescription("Close").performClick()

        // It stays in the library, under Hidden.
        waitFor("Hidden")
        compose.onNodeWithText("Pec Deck").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Start session")
        click("Start session")
        waitFor("Add exercise")
        search("pec deck")
        compose.onNodeWithText("1-Arm Reverse Pec Deck").assertIsDisplayed()
        compose.onAllNodesWithText("Pec Deck").assertCountIs(0)
    }

    @Test
    fun theExerciseSheetShowsTheNoteAndOpensTheVideoInYouTubeAtItsTimestamp() {
        waitFor("Start session")
        click("Start session")
        waitFor("Add exercise")
        search("cable lateral")
        click("Cable Lateral Raise")
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Next")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Cable Lateral Raise").performClick()
        waitFor("Technique video · 10:53")
        compose.onNodeWithText("Raise your arm \"out\", not \"up\". Mind-muscle connection with the middle fibers.").assertIsDisplayed()
        compose.onNodeWithText("Cable · Side Delts").assertIsDisplayed()
        click("Technique video · 10:53")

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(YouTubePackage, started.`package`)
        assertEquals("https://www.youtube.com/watch?v=SJqInYJcg5k&t=653s", started.dataString)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountIs(n: Int) =
        assertEquals(n, fetchSemanticsNodes().size)
}
