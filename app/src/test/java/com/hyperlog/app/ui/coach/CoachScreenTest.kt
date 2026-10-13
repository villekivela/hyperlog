package com.hyperlog.app.ui.coach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rule coach from Today: the Coach screen, time off on Today, and missing equipment on gym pick. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class CoachScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val db get() = HyperlogDatabase.get(ApplicationProvider.getApplicationContext())

    private fun runStarter() {
        compose.waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        compose.waitFor("Week 1 · Ramp-in")
    }

    @Test
    fun handPainProposesTheProgramDocumentsChange() {
        runStarter()
        compose.onNodeWithContentDescription("Coach").performClick()
        compose.waitFor("What’s in the way?")
        compose.onNodeWithText("Pain").performClick()
        compose.onNodeWithText("Hands").assertIsDisplayed()
        compose.onNodeWithText("No grip").assertIsDisplayed()
        compose.onNodeWithText("Review change").performClick()

        compose.waitFor("Accept")
        compose.onNodeWithText("Hands: less grip for two weeks").assertIsDisplayed()
        compose.onNodeWithText("In place of Barbell Bench Press").assertIsDisplayed()
        compose.onNodeWithText("Smith Machine Bench Press").assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()

        // Back on Today, the day is the Change's.
        compose.waitFor("Smith Machine Bench Press")
        compose.onNodeWithText("Heavy bench", substring = true).assertIsDisplayed()
    }

    @Test
    fun handsWorseAfterAnExerciseProposeItsNoGripSwap() {
        runStarter()
        compose.onNodeWithContentDescription("Coach").performClick()
        compose.waitFor("What’s in the way?")
        compose.onNodeWithText("Pain").performClick()
        // Before a hand Change there is nothing to be worse after.
        assertEquals(0, compose.onAllNodesWithText("Worse the next morning after").fetchSemanticsNodes().size)
        compose.onNodeWithText("Review change").performClick()
        compose.waitFor("Accept")
        compose.onNodeWithText("Accept").performClick()

        compose.waitFor("Smith Machine Bench Press")
        compose.onNodeWithContentDescription("Coach").performClick()
        compose.waitFor("What’s in the way?")
        compose.onNodeWithText("Pain").performClick()
        compose.onNodeWithText("Worse the next morning after").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cable Lateral Raise").performScrollTo().performClick()
        compose.onNodeWithText("Review change").performClick()
        compose.waitFor("Hands worse after Cable Lateral Raise: no grip on it")
        compose.onNodeWithText("Machine Lateral Raise").assertExists()
    }

    @Test
    fun aShortBreakResumesAsPlanned() {
        runStarter()
        runBlocking { db.sessions().insertSession(SessionEntity(startedAt = System.currentTimeMillis() - 3 * DAY, finishedAt = System.currentTimeMillis() - 3 * DAY + 3_600_000)) }
        compose.onNodeWithContentDescription("Coach").performClick()
        compose.waitFor("3 days since your last session")
        compose.onNodeWithText("Time off").performClick()
        compose.onNodeWithText("Review change").performClick()
        compose.waitFor("3 days since your last session. Resume as planned.")
    }

    @Test
    fun elevenDaysWithoutTrainingProposesAReEntrySession() {
        runStarter()
        runBlocking { db.sessions().insertSession(SessionEntity(startedAt = System.currentTimeMillis() - 11 * DAY, finishedAt = System.currentTimeMillis() - 11 * DAY + 3_600_000)) }
        // Today reads the last session again when it shows.
        compose.onNodeWithContentDescription("Program").performClick()
        compose.waitFor("Ramp-in · weeks 1–2", substring = true)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("11 days since your last session")
        compose.onNodeWithText("Ease back in").performClick()

        compose.waitFor("Accept")
        compose.onNodeWithText("Back after 11 days: an easier first session").assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()
        compose.waitGone("Ease back in")
        compose.onNodeWithText("Week 1 · Ramp-in").assertIsDisplayed()
    }

    @Test
    fun pickingAGymWithoutALegExtensionProposesASwapForThatSession() {
        runStarter()
        runBlocking {
            val gyms = Gyms(db)
            val id = gyms.addGym("Kamppi")
            gyms.markMissing(id, "leg_extension")
            db.programs().setPosition(db.programs().active()!!.id, 1, 1)
        }
        compose.waitFor("Leg Extension")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Not at Kamppi · for this session")
        compose.onNodeWithText("In place of Leg Extension").assertIsDisplayed()
        compose.onNodeWithText("Reverse Nordic").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()

        compose.waitFor("Target")
        val session = runBlocking { db.sessions().runningSession()!! }
        val legs = runBlocking { db.sessions().sessionExercises(session.id) }.single { it.slotId == "leg_extension" }
        assertEquals("reverse_nordic", legs.exerciseId)
    }

    @Test
    fun withNoProgramTheCoachTalksItThroughWithClaudeAndSaysWhatTheRulesNeed() {
        compose.waitFor("Run this program")
        compose.onNodeWithContentDescription("Coach").performClick()
        compose.waitFor("What’s in the way?")
        compose.onNodeWithText("Pain").performClick()
        compose.onNodeWithText("Review change").performClick()
        compose.waitFor("The rules change a program’s plan: run one to use them.")

        // Something else still asks Claude; with no plan to change there is nothing to paste back.
        compose.onNodeWithText("Something else").performClick()
        compose.onNodeWithText("Ask Claude").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Paste change").fetchSemanticsNodes().size)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
