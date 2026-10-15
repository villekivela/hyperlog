package com.hyperlog.app.ui.coach

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.openTab
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The words-first Coach (SPEC, Coaching → Words first, through Claude), and what moved off it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class CoachScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val db get() = HyperlogDatabase.get(context)

    private fun runStarter() {
        compose.waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        compose.waitFor("Week 1 · Ramp-in")
    }

    private fun openCoach() {
        compose.openTab("Coach")
        compose.waitFor("In your words")
    }

    private fun words() = compose.onNode(hasSetTextAction())

    private fun wordsText(): String =
        words().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Claude", text))
    }

    /** Taps Ask Claude and returns the prompt the share sheet got. */
    private fun ask(): String {
        compose.onNodeWithText("Ask Claude").performClick()
        compose.waitUntil(5_000) { shadowOf(compose.activity).peekNextStartedActivity() != null }
        val chooser = shadowOf(compose.activity).nextStartedActivity
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        return send.getStringExtra(Intent.EXTRA_TEXT)!!
    }

    /** Hyperlog goes to the background, as for the Claude app, and comes back. */
    private fun awayAndBack() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    private val elbow = "```json\n" +
        """{"summary": "Elbow: machine curls", "scope": "rest_of_block", "operations": [{"op": "swap", "slot": "curl", "exercise": "machine_preacher_curl"}]}""" +
        "\n```"

    @Test
    fun coachIsWordsWithNoSituationsLimitsOrRules() {
        runStarter()
        openCoach()
        compose.onNodeWithText("Tell Claude what’s going on", substring = true).assertIsDisplayed()
        listOf("What’s in the way?", "Pain", "Time off", "Limits", "No grip", "Review change").forEach {
            assertTrue(it, compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isEmpty())
        }
        compose.onNodeWithText("Ask Claude").assertIsNotEnabled()
        compose.onNodeWithText("Paste reply").assertIsDisplayed()
    }

    @Test
    fun startersFillTheWordsAndTheWordsStayEditable() {
        runStarter()
        openCoach()
        compose.onNodeWithText("My hands hurt").performClick()
        assertEquals("My hands hurt.", wordsText())
        // A second starter goes on a new line.
        compose.onNodeWithText("Slept badly").performScrollTo().performClick()
        assertEquals("My hands hurt.\nI slept badly last night.", wordsText())
        words().performTextInput(" Left more than right.")
        assertTrue(wordsText(), wordsText().endsWith("Left more than right."))
        assertTrue(ask().contains("> My hands hurt.\n> I slept badly last night. Left more than right."))
    }

    @Test
    fun aChangeInForceIsACardWithRevertAndTellClaudeHowItsGoing() {
        runStarter()
        openCoach()
        words().performTextInput("Elbow aches on curls.")
        copy(elbow)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Change to your program")
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 1 · Ramp-in")

        openCoach()
        compose.onNodeWithText("Elbow: machine curls").assertIsDisplayed()
        compose.onNodeWithText("Rest of Ramp-in").assertIsDisplayed()
        compose.onNodeWithText("Tell Claude how it’s going").performClick()
        assertTrue(wordsText(), wordsText().startsWith("About “Elbow: machine curls”: "))

        // Revert asks first.
        compose.onNodeWithText("Revert").performClick()
        compose.waitFor("Revert “Elbow: machine curls”?")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Elbow: machine curls").assertIsDisplayed()
        compose.onNodeWithText("Revert").performClick()
        compose.onAllNodes(hasText("Revert"))[1].performClick()
        compose.waitGone("Elbow: machine curls")
    }

    @Test
    fun theUsualGymIsPickedAndThePromptHoldsItsEquipmentAndWhatsMissing() {
        runStarter()
        runBlocking {
            val gyms = Gyms(db)
            gyms.addGym("Itis")
            val harbour = gyms.addGym("Harbour")
            gyms.setWeekdays(harbour, setOf(LocalDate.now().dayOfWeek))
            gyms.markMissing(harbour, "leg_extension")
        }
        openCoach()
        compose.waitFor("Harbour")
        compose.onNode(hasText("Harbour").and(isSelected())).assertIsDisplayed()
        words().performTextInput("Short on time.")
        val prompt = ask()
        assertTrue(prompt, prompt.contains("## Gym: Harbour"))
        assertTrue(prompt, prompt.contains("leg_extension (Leg Extension)"))

        // Another gym, another prompt.
        compose.onNodeWithText("Itis").performClick()
        assertTrue(ask().contains("## Gym: Itis"))
    }

    @Test
    fun comingBackWithAValidChangeOnTheClipboardShowsTheReadyBar() {
        runStarter()
        openCoach()
        words().performTextInput("Elbow aches on curls.")
        ask()
        // Asked: Paste reply is the next step. Nothing is ready yet.
        copy("Rest it for a few days and train as planned.")
        awayAndBack()
        assertTrue(compose.onAllNodes(hasText("Claude’s reply is ready")).fetchSemanticsNodes().isEmpty())

        copy("Here it is:\n$elbow")
        awayAndBack()
        compose.waitFor("Claude’s reply is ready")
        // Nothing opens by itself.
        assertTrue(compose.onAllNodes(hasText("Change to your program")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Review").performClick()
        compose.waitFor("Change to your program")
        compose.onNodeWithText("Elbow: machine curls").assertIsDisplayed()
    }

    @Test
    fun easeBackInOpensCoachWithTheWordsFilledIn() {
        runStarter()
        runBlocking { db.sessions().insertSession(SessionEntity(startedAt = System.currentTimeMillis() - 11 * DAY, finishedAt = System.currentTimeMillis() - 11 * DAY + 3_600_000)) }
        // Today reads the last session again when it shows.
        compose.openTab("Program")
        compose.waitFor("Ramp-in · weeks 1–2", substring = true)
        compose.openTab("Today")
        compose.waitFor("11 days since your last session")
        compose.onNodeWithText("Ease back in").performClick()

        compose.waitFor("In your words")
        assertEquals("I’ve had 11 days off. Ease me back in.", wordsText())
        val prompt = ask()
        assertTrue(prompt, prompt.contains("11 days ago"))
    }

    @Test
    fun pickingAGymWithoutALegExtensionProposesASwapForThatSession() {
        runStarter()
        runBlocking {
            val gyms = Gyms(db)
            val id = gyms.addGym("Harbour")
            gyms.markMissing(id, "leg_extension")
            db.programs().setPosition(db.programs().active()!!.id, 1, 1)
        }
        compose.waitFor("Leg Extension")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Not at Harbour · for this session")
        compose.onNodeWithText("In place of Leg Extension").assertIsDisplayed()
        compose.onNodeWithText("Reverse Nordic").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()

        compose.waitFor("Reps")
        val session = runBlocking { db.sessions().runningSession()!! }
        val legs = runBlocking { db.sessions().sessionExercises(session.id) }.single { it.slotId == "leg_extension" }
        assertEquals("reverse_nordic", legs.exerciseId)
    }

    @Test
    fun withNoProgramClaudeAnswersInWordsAndNothingComesBack() {
        compose.waitFor("Run this program")
        openCoach()
        compose.onNodeWithText("With no program running", substring = true).assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Paste reply")).fetchSemanticsNodes().isEmpty())
        words().performTextInput("My knee hurts on lunges.")
        assertTrue(ask().contains("No program is running in Hyperlog"))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
