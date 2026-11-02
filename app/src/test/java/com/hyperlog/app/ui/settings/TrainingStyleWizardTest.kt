package com.hyperlog.app.ui.settings

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.await
import com.hyperlog.app.click
import com.hyperlog.app.coach.AboutMe
import com.hyperlog.app.coach.AgeBand
import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.coach.TrainingGoal
import com.hyperlog.app.coach.TrainingStyles
import com.hyperlog.app.coach.TrainingTargets
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.openTab
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The training style's wizard (SPEC, Coaching → Training style → The wizard): each step opens on
 * the current value, Done saves and counts as set, × saves nothing, and until it is done Today
 * shows a card and New program opens it first. Played with the scripted coach, no key, no network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TrainingStyleWizardTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val db get() = HyperlogDatabase.get(context)

    private val setup = object : ExternalResource() {
        override fun before() = runBlocking {
            db.settings().put(SettingEntity("scripted_coach", "on"))
            ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(setup).around(compose)

    private val styles get() = TrainingStyles(db, context)
    private fun style() = runBlocking { styles.current() }
    private fun done() = runBlocking { styles.done.first() }

    private fun next() {
        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.await(hasText(text))
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Walks from the first step to the summary, keeping what each step shows. */
    private fun walkToSummary() {
        repeat(9) { next() }
        compose.waitFor("This is how you train")
    }

    /** × on the check-in under way, so it doesn't outlive the test (the scripted coach keeps it). */
    private fun closeCheckIn() {
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("What’s going on?")
    }

    private fun setUpAgain() {
        compose.openTab("Settings")
        compose.click("Training style")
        compose.click("Set it up again")
        compose.waitFor("What are you training for?")
    }

    private val mine = TrainingTargets(goal = TrainingGoal.Strength, volume = NumberRange(14, 20), compound = NumberRange(4, 8))
    private val me = AboutMe(age = AgeBand.Forties, anythingElse = "I train fasted")

    @Test
    fun theWizardOpensOnTheCurrentStyleAndDoneWithoutAChangeKeepsIt() {
        runBlocking { styles.setTargets(mine); styles.setAboutMe(me) }
        setUpAgain()
        walkToSummary()
        compose.waitFor("Strength")
        compose.waitFor("14–20 sets a week · Same each week")
        compose.waitFor("40–49")
        compose.click("Done")
        assertEquals(mine, style().targets)
        assertEquals(me, style().aboutMe)
        assertTrue(done())
        // Back on the page it was run from.
        compose.waitFor("Training targets")
    }

    @Test
    fun doneWithEveryDefaultKeptCountsAsSet() {
        assertFalse(done())
        setUpAgain()
        walkToSummary()
        compose.click("Done")
        compose.waitFor("Training targets")
        assertTrue(done())
        assertEquals(TrainingTargets(), style().targets)
    }

    @Test
    fun eachStepsChoiceIsSavedTogetherAtDone() {
        setUpAgain()
        tap("Both")
        next()
        tap("High")
        tap("Build up each week")
        next()
        tap("Never")
        next()
        next()
        tap("30–39")
        next()
        tap("Progress takes weeks")
        tap("Coming back from a break")
        next()
        tap("Knees")
        next()
        next()
        next()
        compose.waitFor("This is how you train")
        // Nothing is kept until Done.
        assertEquals(TrainingTargets(), style().targets)
        compose.click("Done")
        compose.waitFor("Training targets")
        val kept = style()
        assertEquals(TrainingGoal.Both, kept.targets.goal)
        assertEquals(NumberRange(14, 20), kept.targets.volume)
        assertEquals(com.hyperlog.app.coach.AcrossBlock.Rising, kept.targets.acrossBlock)
        assertEquals(com.hyperlog.app.coach.FailureRule.Never, kept.targets.failure)
        assertEquals(AgeBand.Thirties, kept.aboutMe.age)
        assertEquals(com.hyperlog.app.coach.Progress.Weeks, kept.aboutMe.progress)
        assertTrue(kept.aboutMe.comingBack)
        assertEquals(setOf(com.hyperlog.app.coach.BodyArea.Knees), kept.aboutMe.aches.keys)
    }

    @Test
    fun aRangeTypedOnAStepIsKeptWhenNextIsTapped() {
        setUpAgain()
        next(); next(); next()
        compose.waitFor("How many reps a set?")
        val field = hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Compounds, fewest reps")
        compose.onNode(field).performScrollTo().performTextClearance()
        compose.onNode(field).performTextInput("3")
        next()
        compose.waitFor("How old are you?")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("How many reps a set?")
        repeat(6) { next() }
        compose.waitFor("This is how you train")
        compose.waitFor("Compounds 3–10 · isolation 8–15")
    }

    @Test
    fun backStepsBackAndLeavesFromTheFirstStep() {
        setUpAgain()
        next()
        compose.waitFor("How much do you train?")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("What are you training for?")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Training targets")
    }

    @Test
    fun closingLeavesWithoutSavingAnything() {
        setUpAgain()
        tap("Both")
        next()
        compose.onNodeWithContentDescription("Leave without saving").performClick()
        compose.waitFor("Training targets")
        assertEquals(TrainingTargets(), style().targets)
        assertFalse(done())
    }

    @Test
    fun howITrainOpensFromTheSummaryAndIsKeptAsItChanges() {
        setUpAgain()
        walkToSummary()
        tap("How I train")
        compose.waitFor("Always")
        tap("Blocks with a deload")
        assertFalse(style().howITrain.on(com.hyperlog.app.coach.Principle.Deload))
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("This is how you train")
    }

    @Test
    fun todaysCardShowsUntilDoneAndDismissingItDoesNotCountAsSet() {
        compose.waitFor("Set how you train")
        compose.onNodeWithContentDescription("Dismiss set up").performClick()
        compose.waitGone("Set how you train")
        assertFalse(done())
        assertTrue(runBlocking { styles.cardDismissed.first() })
    }

    @Test
    fun theCardSetsUpAndIsGoneOnceDone() {
        compose.click("Set up")
        compose.waitFor("What are you training for?")
        walkToSummary()
        compose.click("Done")
        compose.waitFor("Today")
        compose.waitGone("Set how you train")
        assertTrue(done())
    }

    @Test
    fun newProgramOpensTheWizardFirstAndItsDoneGoesOnIntoTheCheckIn() {
        compose.openTab("Coach")
        compose.click("New program")
        compose.waitFor("What are you training for?")
        walkToSummary()
        compose.click("Done")
        compose.waitFor("Do you have the program as a file?")
        assertTrue(done())
        closeCheckIn()
    }

    @Test
    fun dismissingTodaysCardDoesNotLiftNewProgramsGate() {
        compose.waitFor("Set how you train")
        compose.onNodeWithContentDescription("Dismiss set up").performClick()
        compose.openTab("Coach")
        compose.click("New program")
        compose.waitFor("What are you training for?")
    }

    @Test
    fun closingTheWizardFromNewProgramDropsTheCheckInToo() {
        compose.openTab("Coach")
        compose.click("New program")
        compose.waitFor("What are you training for?")
        compose.onNodeWithContentDescription("Leave without saving").performClick()
        compose.waitFor("What’s going on?")
        assertFalse(done())
    }

    @Test
    fun onceDoneNewProgramGoesStraightToItsCheckIn() {
        runBlocking { styles.finish(TrainingTargets(), AboutMe()) }
        compose.openTab("Coach")
        compose.click("New program")
        compose.waitFor("Do you have the program as a file?")
        closeCheckIn()
    }
}
