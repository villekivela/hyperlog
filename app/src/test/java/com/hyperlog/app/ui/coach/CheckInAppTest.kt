package com.hyperlog.app.ui.coach

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.await
import com.hyperlog.app.click
import com.hyperlog.app.coach.SavedCheckIn
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.openTab
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coach as a check-in, played by the scripted coach (debug builds) with no key and no network:
 * the tiles, Claude's questions one a screen, Back, ×, leaving and coming back, and an accepted
 * Change that keeps its check-in (SPEC, Coaching → Check-in).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class CheckInAppTest {
    /** This test's application: Robolectric makes one for each test. */
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The starter program running, and the scripted coach switched on, before the app starts. */
    private val setup = object : ExternalResource() {
        override fun before() = runBlocking {
            val db = HyperlogDatabase.get(context)
            db.settings().put(SettingEntity("scripted_coach", "on"))
            ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
            Programs(db).import(StarterProgram.source(context))
            Unit
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(setup).around(compose)

    private fun answerFirst() {
        compose.await(hasTestTag(AnswerTag))
        compose.onAllNodes(hasTestTag(AnswerTag)).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun changeMyProgramAsksWhichAndWhyAndTheAcceptedChangeKeepsItsCheckIn() {
        compose.openTab("Coach")
        compose.click("Change my program")
        compose.waitFor("Which exercise do you want to change?")
        answerFirst()
        compose.waitFor("Why change it?")
        compose.click("It hurts")
        // The first proposal had a wrong slot; it went back, and the fixed one opens for review.
        compose.waitFor("Change to your program")
        compose.click("Accept")
        compose.waitGone("Change to your program")
        val saved = runBlocking { Programs(HyperlogDatabase.get(context)).active.first()!!.history.single().checkIn!! }
        assertEquals(listOf("Which exercise do you want to change?", "Why change it?"), saved.steps.map(SavedCheckIn.Step::question))
        assertEquals("It hurts", saved.steps.last().answer)
    }

    @Test
    fun backChangesTheAnswerBeforeAndTheCheckInSurvivesLeavingTheApp() {
        compose.openTab("Coach")
        compose.click("Something hurts")
        compose.waitFor("Where does it hurt?")
        compose.click("Elbow")
        compose.waitFor("Does it hurt when you press, or only when you grip?")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Where does it hurt?")
        // Leaving the app and coming back: the same step.
        compose.activityRule.scenario.recreate()
        compose.waitFor("Where does it hurt?")
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("What’s going on?")
    }

    @Test
    fun buildMeAWorkoutAsksEffortTimeAndFocusAndEndsInItsReview() {
        compose.openTab("Coach")
        compose.click("Build me a workout")
        compose.waitFor("How hard?")
        compose.click("Normal")
        compose.waitFor("How long, warm-ups and rest included?")
        compose.click("1 h")
        compose.waitFor("What to focus on?")
        compose.click("Balanced full body")
        compose.waitFor("Workout for today")
        compose.click("Add to Today")
        compose.waitFor("Workout")
    }

    @Test
    fun theScriptedCoachIsASettingsSwitch() {
        compose.openTab("Settings")
        compose.click("Scripted coach")
        // Off, with no key: Coach is Ask Claude's screen again.
        compose.openTab("Coach")
        compose.waitFor("In your words")
        compose.openTab("Settings")
        compose.click("Scripted coach")
        compose.openTab("Coach")
        compose.waitFor("What’s going on?")
    }
}
