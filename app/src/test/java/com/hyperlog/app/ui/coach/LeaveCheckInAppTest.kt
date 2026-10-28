package com.hyperlog.app.ui.coach

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.lifecycle.ViewModelProvider
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.navItem
import com.hyperlog.app.openTab
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.await
import com.hyperlog.app.click
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/**
 * Leaving a check-in goes back to where it started: Today for the fit chip and the coach line, the
 * review page for a review (SPEC, Coaching → Leaving a check-in).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class LeaveCheckInAppTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The starter program past Ramp-in on every weekday with 30 minutes, so each day's card has a fit chip, and the scripted coach on. */
    private val setup = object : ExternalResource() {
        override fun before() = runBlocking {
            val db = HyperlogDatabase.get(context)
            db.settings().put(SettingEntity("scripted_coach", "on"))
            ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
            val programs = Programs(db)
            programs.import(StarterProgram.source(context))
            // Past Ramp-in, so Today points at its review.
            db.programs().setPosition(programs.active.first()!!.id, 3, 0)
            WeekdayPlans(db).set(WeekdayPlan(DayOfWeek.entries.associateWith { null }, DayOfWeek.entries.associateWith { 30 }))
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(setup).around(compose)

    private fun tapFitChip() {
        compose.await(hasText(" has 30", substring = true))
        compose.onAllNodes(hasText(" has 30", substring = true)).onFirst().performClick()
        compose.waitForIdle()
    }

    /** Today, not Coach: the fit chips show and Coach's tiles don't. */
    private fun assertOnToday() {
        compose.await(hasText(" has 30", substring = true))
        compose.onNodeWithText("What’s going on?").assertDoesNotExist()
    }

    @Test
    fun closingTheFitChipsCheckInGoesBackToToday() {
        tapFitChip()
        compose.waitFor("Change to your program")
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        assertOnToday()
    }

    @Test
    fun closingAFitChipsCheckInOnProgramGoesBackToProgram() {
        compose.openTab("Program")
        tapFitChip()
        compose.waitFor("Change to your program")
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.await(navItem("Program") and isSelected())
        compose.onNodeWithText("What’s going on?").assertDoesNotExist()
    }

    @Test
    fun doneOnAReviewsCheckInGoesBackToTheReview() {
        compose.click("Ramp-in is done · See the review")
        compose.click("Check in about the next block")
        compose.waitFor("Your lifts moved", substring = true)
        compose.click("Done")
        compose.waitFor("Ramp-in review")
        // Back from the review: Today, where it was opened.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertOnToday()
    }

    /** Bench at 80 × 8 four times in the last two weeks: Today's coach line says it hasn't moved. */
    private fun stallBench() {
        runBlocking {
            val db = HyperlogDatabase.get(context)
            listOf(12L, 9L, 6L, 3L).forEach { ago ->
                val at = System.currentTimeMillis() - ago * DAY
                val id = db.sessions().insertSession(SessionEntity(startedAt = at, finishedAt = at + 3_600_000))
                val bench = db.sessions().insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = "barbell_bench_press", position = 0))
                db.sessions().insertSet(LoggedSetEntity(sessionExerciseId = bench, position = 0, weightKg = 80.0, fullReps = 8, partialReps = 0, rir = 2, loggedAt = at))
            }
        }
        // Today reads the sessions again when it shows.
        compose.openTab("Program")
        compose.openTab("Today")
        compose.waitFor("Barbell Bench Press hasn't moved in 3 sessions", substring = true)
    }

    @Test
    fun closingTheCoachLinesCheckInGoesBackToToday() {
        stallBench()
        compose.click("Check in")
        compose.await(hasContentDescription("Close the check-in"))
        compose.onNodeWithText("What’s going on?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        assertOnToday()
    }

    @Test
    fun withoutAKeyClosingAskClaudeGoesBackWhereItStarted() {
        ViewModelProvider(compose.activity)[CheckInViewModel::class.java].setScripted(false)
        tapFitChip()
        compose.waitFor("In your words")
        compose.onNodeWithContentDescription("Back").performClick()
        assertOnToday()

        stallBench()
        compose.click("Check in")
        compose.waitFor("In your words")
        compose.onNodeWithContentDescription("Back").performClick()
        assertOnToday()

        compose.click("Ramp-in is done · See the review")
        compose.click("Check in about the next block")
        compose.waitFor("In your words")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Ramp-in review")
    }

    @Test
    fun closingTheProgramFromAProgramReviewsNewProgramGoesBackToTheReview() {
        // The whole program done: Today points at its program review.
        runBlocking {
            val db = HyperlogDatabase.get(context)
            db.programs().setPosition(Programs(db).active.first()!!.id, LAST_WEEK + 1, 0)
        }
        compose.click("Hyperlog Full Body is done · See the review")
        compose.waitFor("Hyperlog Full Body review")
        compose.onNode(hasText("New program") and hasClickAction()).performClick()
        compose.click("Build it from my answers")
        compose.click("3")
        compose.waitFor("Start program")
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("Hyperlog Full Body review")
    }

    @Test
    fun closingEaseBackInGoesBackToToday() {
        runBlocking { HyperlogDatabase.get(context).sessions().insertSession(SessionEntity(startedAt = System.currentTimeMillis() - 11 * DAY, finishedAt = System.currentTimeMillis() - 11 * DAY + 3_600_000)) }
        // Today reads the last session again when it shows.
        compose.openTab("Program")
        compose.openTab("Today")
        compose.click("Ease back in")
        compose.await(hasContentDescription("Close the check-in"))
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        assertOnToday()
    }

    @Test
    fun acceptStillGoesToToday() {
        compose.openTab("Program")
        tapFitChip()
        compose.click("Accept")
        compose.waitGone("Change to your program")
        assertOnToday()
        compose.onNodeWithText("Ramp-in is done · See the review").assertExists()
    }

    @Test
    fun aCheckInFromCoachsTilesEndsOnCoachAfterOneFromTodayWasBackedOutOf() {
        tapFitChip()
        compose.click("Reject")
        // Back past the first answer: Coach's tiles, the fit's check-in gone.
        while (compose.onAllNodes(hasText("What’s going on?")).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForIdle()
        }
        compose.click("Something hurts")
        compose.waitFor("Where does it hurt?")
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("What’s going on?")
    }

    private companion object {
        const val DAY = 86_400_000L

        /** The starter program's last week. */
        const val LAST_WEEK = 12
    }
}
