package com.hyperlog.app.ui.settings

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.await
import com.hyperlog.app.click
import com.hyperlog.app.coach.AboutMe
import com.hyperlog.app.coach.AgeBand
import com.hyperlog.app.coach.BodyArea
import com.hyperlog.app.coach.FailureRule
import com.hyperlog.app.coach.Frequency
import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.coach.Progress
import com.hyperlog.app.coach.TrainingGoal
import com.hyperlog.app.coach.TrainingStyles
import com.hyperlog.app.coach.TrainingTargets
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.openTab
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Training style (SPEC, Coaching → Training style): every training target, How I train
 * with Back to default, and About me's fields, edited in place and kept across a restart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TrainingStyleAppTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val setup = object : ExternalResource() {
        override fun before() = runBlocking { ExerciseLibrary(HyperlogDatabase.get(context)).sync(LibrarySeed.fromAssets(context)) }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(setup).around(compose)

    private fun style() = runBlocking { TrainingStyles(HyperlogDatabase.get(context), context).current() }

    private fun open() {
        compose.openTab("Settings")
        compose.click("Training style")
        compose.waitFor("Training targets")
    }

    /** Taps [text] after scrolling it into view. */
    private fun tap(text: String) {
        compose.await(hasText(text))
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Taps the control TalkBack reads as [description] (an Add button), after scrolling it into view. */
    private fun tapDescribed(description: String) {
        val node = androidx.compose.ui.test.hasContentDescription(description)
        compose.await(node)
        compose.onNode(node).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Types [text] into the field labelled or described as [label], in place of what it held. */
    private fun type(label: String, text: String) {
        val field = hasSetTextAction() and (hasText(label) or androidx.compose.ui.test.hasContentDescription(label))
        compose.await(field)
        compose.onNode(field).performScrollTo().performTextClearance()
        compose.onNode(field).performTextInput(text)
        compose.waitForIdle()
    }

    /** Picks [name] from the picker's list, scrolled to it. */
    private fun pick(name: String) {
        compose.await(androidx.compose.ui.test.hasScrollToNodeAction())
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(hasText(name))
        compose.click(name)
    }

    @Test
    fun everyTrainingTargetIsEditedInPlaceAndKept() {
        open()
        tap("Strength")
        tap("High")
        tap("Rising")
        tap("3+")
        tap("Never")
        tap("Calibration sets")
        type("Compounds, fewest reps", "4")
        type("Isolation, most reps", "20")
        assertEquals(
            TrainingTargets(
                goal = TrainingGoal.Strength, volume = NumberRange(14, 20), acrossBlock = com.hyperlog.app.coach.AcrossBlock.Rising,
                frequency = Frequency.ThreeOrMore, failure = FailureRule.Never, calibration = false,
                compound = NumberRange(4, 10), isolation = NumberRange(8, 20),
            ),
            style().targets,
        )
        // Custom volume: each end its own field; one out of range isn't kept.
        tap("Custom")
        type("Volume, fewest sets", "12")
        type("Volume, most sets", "50")
        compose.waitFor("Each end 1–40, the fewest not above the most")
        assertEquals(NumberRange(12, 20), style().targets.volume)
        type("Volume, most sets", "18")
        assertEquals(NumberRange(12, 18), style().targets.volume)

        // After a restart, the page shows what was kept.
        compose.activityRule.scenario.recreate()
        compose.waitFor("Training targets")
        assertEquals(NumberRange(12, 18), style().targets.volume)
        compose.await(hasSetTextAction() and hasText("18"))
    }

    @Test
    fun howITrainSavesAndGoesBackToTheDefaultAfterAsking() {
        open()
        compose.onNodeWithText("Back to default").assertDoesNotExist()
        type("How I train", "Heavy and simple.")
        // Save waits in the strip at the bottom, outside the page's scroll.
        compose.click("Save")
        compose.waitGone("Save")
        assertEquals("Heavy and simple.", style().howITrain)
        tap("Back to default")
        compose.waitFor("Back to the default?")
        compose.click("Cancel")
        assertEquals("Heavy and simple.", style().howITrain)
        tap("Back to default")
        compose.onNode(hasText("Back to default") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        compose.waitGone("Back to the default?")
        assertFalse(runBlocking { TrainingStyles(HyperlogDatabase.get(context), context).edited.first() })
        compose.await(hasSetTextAction() and hasText("Form comes before load", substring = true))
    }

    @Test
    fun aboutMesFieldsAreEditedInPlaceAndKept() {
        open()
        tap("40–49")
        tap("Progress takes months")
        tap("Coming back from a break")
        tap("Knees")
        type("Knees", "clicks on deep squats")
        tapDescribed("Add an exercise to avoid")
        pick("Barbell Back Squat")
        tapDescribed("Add an exercise to favour")
        pick("Hack Squat")
        type("Anything else", "I train fasted")
        assertEquals(
            AboutMe(
                age = AgeBand.Forties, progress = Progress.Months, comingBack = true, aches = mapOf(BodyArea.Knees to "clicks on deep squats"),
                avoid = listOf("barbell_squat"), favour = listOf("hack_squat"), anythingElse = "I train fasted",
            ),
            style().aboutMe,
        )
        // Tapped again, an age band is cleared; an exercise comes off its list with its ×.
        tap("40–49")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Remove Barbell Back Squat")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(null, style().aboutMe.age)
        assertEquals(emptyList<String>(), style().aboutMe.avoid)

        compose.activityRule.scenario.recreate()
        compose.waitFor("Training targets")
        compose.await(hasSetTextAction() and hasText("clicks on deep squats"))
        compose.await(hasText("Hack Squat"))
    }
}
