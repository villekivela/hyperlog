package com.hyperlog.app.ui.coach

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
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
import com.hyperlog.app.coach.exercises
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.openTab
import com.hyperlog.app.navItem
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
    fun newProgramEndsInItsReviewAndItsNewExerciseIsSavedOnlyOnStartProgram() {
        val db = HyperlogDatabase.get(context)
        val before = runBlocking { Programs(db).active.first()!!.id }
        fun inLibrary() = runBlocking { db.exercises().all().any { it.name == "Barbell Drag Curl" } }
        compose.openTab("Coach")
        compose.click("New program")
        compose.waitFor("Do you have the program as a file?")
        compose.onNodeWithText("Attach a spreadsheet, PDF or photo").assertExists()
        compose.click("Build it from my answers")
        compose.waitFor("How many days a week can you train?")
        compose.click("3")
        compose.waitFor("Start program")
        compose.onNodeWithText("Seal Row", substring = true).assertExists()
        // Reviewed, nothing is saved yet.
        assertEquals(false, inLibrary())
        compose.click("Start program")
        compose.waitFor("Replace Hyperlog Full Body?")
        compose.click("Replace")
        compose.waitGone("Start program")
        assertEquals(true, inLibrary())
        val active = runBlocking { Programs(db).active.first()!! }
        assertEquals("Scripted full body, 2 days", active.program.title)
        assertEquals(com.hyperlog.app.program.Position(1, 0), active.position)
        // The program before stays, as the record of its sessions.
        assertEquals("Hyperlog Full Body", runBlocking { Programs(db).program(before)!!.title })
    }

    /** A new program built in a check-in, up to its review. */
    private fun buildNewProgram() {
        compose.openTab("Coach")
        compose.click("New program")
        compose.click("Build it from my answers")
        compose.waitFor("How many days a week can you train?")
        compose.click("3")
        compose.waitFor("Start program")
    }

    @Test
    fun saveForLaterEndsTheCheckInAndProgramsStartsItLaterWithItsNewExercise() {
        val db = HyperlogDatabase.get(context)
        fun inLibrary() = runBlocking { db.exercises().all().any { it.name == "Barbell Drag Curl" } }
        buildNewProgram()
        compose.click("Save for later")
        compose.waitFor("Saved to your programs")
        // The check-in ended, and the program running goes on.
        compose.onNodeWithText("What’s going on?").assertExists()
        assertEquals("Hyperlog Full Body", runBlocking { Programs(db).active.first()!!.program.title })
        assertEquals(false, inLibrary())
        compose.click("Programs")
        compose.waitFor("Saved")
        compose.click("Start")
        compose.click("Replace")
        compose.waitFor("Scripted full body, 2 days")
        assertEquals("Scripted full body, 2 days", runBlocking { Programs(db).active.first()!!.program.title })
        assertEquals(true, inLibrary())
    }

    @Test
    fun aSavedProgramWhoseNewExerciseTheLibraryGainedSinceShowsTheProblemsAndStartsNothing() {
        val db = HyperlogDatabase.get(context)
        buildNewProgram()
        compose.click("Save for later")
        compose.waitFor("Saved to your programs")
        val curl = runBlocking { Programs(db).kept.first().first().program.exercises() }.single { it.startsWith("barbell_drag_curl") }
        runBlocking {
            ExerciseLibrary(db).addNew(listOf(com.hyperlog.app.library.Exercise(curl, "My Drag Curl", com.hyperlog.app.library.Equipment.Barbell,
                com.hyperlog.app.library.MovementPattern.ElbowFlexion, com.hyperlog.app.library.GripDemand.High, listOf("Biceps"))))
        }
        compose.openTab("Program")
        compose.onNodeWithContentDescription("More").performClick()
        compose.click("Programs")
        compose.click("Start")
        compose.click("Replace")
        compose.waitFor("Not imported")
        compose.onNodeWithText("the library already has \"$curl\"", substring = true).assertExists()
        assertEquals("Hyperlog Full Body", runBlocking { Programs(db).active.first()!!.program.title })
    }

    @Test
    fun aReplyThatComesWhileTheAppIsAwayIsNoticedAndItsNotificationOpensIt() {
        compose.openTab("Coach")
        compose.click("New program")
        compose.waitFor("Do you have the program as a file?")
        compose.click("Build it from my answers")
        compose.waitFor("How many days a week can you train?")
        compose.click("3")
        try {
            // The app goes away at once, while Claude writes (the saving itself: CheckInRunnerTest).
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            val notifications = org.robolectric.Shadows.shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
            compose.waitUntil(15_000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                notifications.allNotifications.any { it.extras.getString("android.title") == "Your program is ready" }
            }
            val open = org.robolectric.Shadows.shadowOf(notifications.allNotifications.single { it.extras.getString("android.title") == "Your program is ready" }.contentIntent).savedIntent
            compose.activityRule.scenario.onActivity { it.onNewIntent(open) }
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitFor("Start program")
        } finally {
            // × ends it, as every check-in here ends: the file holding it would open the next test's Coach.
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.await(androidx.compose.ui.test.hasContentDescription("Close the check-in"))
            compose.onNodeWithContentDescription("Close the check-in").performClick()
            compose.waitFor("What’s going on?")
        }
    }

    @Test
    fun theScriptedCoachIsASettingsSwitch() {
        compose.openTab("Settings")
        compose.click("Scripted coach")
        // Off, with no key: the same tiles, and a tile opens Ask Claude's screen.
        compose.openTab("Coach")
        compose.waitFor("Something else")
        compose.click("Something else")
        compose.waitFor("In your words")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitFor("What’s going on?")
        compose.openTab("Settings")
        compose.click("Scripted coach")
        compose.openTab("Coach")
        compose.waitFor("What’s going on?")
    }

    @Test
    fun closeOnAReviewDropsTheCheckInWhereRejectStepsBack() {
        compose.openTab("Coach")
        compose.click("Build me a workout")
        compose.waitFor("How hard?")
        compose.click("Normal")
        compose.waitFor("How long, warm-ups and rest included?")
        compose.click("1 h")
        compose.waitFor("What to focus on?")
        compose.click("Balanced full body")
        compose.waitFor("Workout for today")
        // Reject is Back, with Type my own open to say what was wrong.
        compose.click("Reject")
        compose.waitFor("What to focus on?")
        compose.await(hasSetTextAction())
        compose.click("Balanced full body")
        compose.waitFor("Workout for today")
        // × drops the check-in, as on every step.
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("What’s going on?")
        compose.onNodeWithText("Workout for today").assertDoesNotExist()
    }

    @Test
    fun theBarHidesDuringACheckInAndYourOwnWordsComeBackOnBack() {
        compose.openTab("Coach")
        compose.click("Something hurts")
        compose.waitFor("Where does it hurt?")
        // A check-in hides the navigation bar, as a session does.
        compose.await(navItem("History")) { it == 0 }
        compose.click("Type my own")
        compose.onNode(hasSetTextAction()).performTextInput("Left wrist")
        // Half typed, the activity made again: still there.
        compose.activityRule.scenario.recreate()
        compose.await(hasSetTextAction() and hasText("Left wrist"))
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitFor("Does it hurt when you press, or only when you grip?")
        // Back to the question answered in your own words: Type my own opens with them.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Where does it hurt?")
        compose.await(hasSetTextAction() and hasText("Left wrist"))
        // × ends it, and its words with it (the check-in file outlives the test).
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("What’s going on?")
        compose.click("Something hurts")
        compose.waitFor("Where does it hurt?")
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("What’s going on?")
        compose.await(navItem("History"))
    }
}
