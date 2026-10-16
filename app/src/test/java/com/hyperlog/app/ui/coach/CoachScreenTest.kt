package com.hyperlog.app.ui.coach

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertFalse
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
        compose.onNodeWithText("Something hurts").performClick()
        assertEquals("Something hurts: ", wordsText())
        words().performTextInput("left elbow.")
        assertEquals("Something hurts: left elbow.", wordsText())
        // A second starter goes on a new line.
        compose.onNodeWithText("Slept badly").performScrollTo().performClick()
        assertEquals("Something hurts: left elbow.\nI slept badly last night.", wordsText())
        assertTrue(ask().contains("> Something hurts: left elbow.\n> I slept badly last night."))
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
    fun tappingAChangeInForceShowsWhatItDoesWithRevert() {
        runStarter()
        openCoach()
        copy(elbow)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Accept")
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 1 · Ramp-in")

        openCoach()
        compose.onNodeWithText("Elbow: machine curls").performClick()
        compose.waitFor("Change in force")
        compose.onNodeWithText("In place of Bayesian Cable Curl").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Accept")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Revert").performClick()
        compose.waitFor("Revert “Elbow: machine curls”?")
        compose.onAllNodes(hasText("Revert"))[1].performClick()
        compose.waitFor("In your words")
        compose.waitGone("Elbow: machine curls")
        assertEquals(0, runBlocking { db.planChanges().changes(db.programs().active()!!.id) }.count { it.revertedAt == null })
    }

    @Test
    fun theNextStepGoesBackToAskClaudeWhenTheRequestEnds() {
        runStarter()
        openCoach()
        val next = compose.onNode(hasTestTag(NextStepTag))
        words().performTextInput("Elbow aches.")
        next.assertTextEquals("Ask Claude")
        ask()
        next.assertTextEquals("Paste reply")
        // Cleared words end it.
        words().performTextClearance()
        next.assertTextEquals("Ask Claude")

        // So does an accept.
        words().performTextInput("Elbow aches.")
        ask()
        copy(elbow)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Accept")
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        openCoach()
        compose.onNode(hasTestTag(NextStepTag)).assertTextEquals("Ask Claude")
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

        // Elsewhere sends no gym's equipment: the words say what's there.
        compose.onNodeWithText("Elsewhere").performClick()
        val away = ask()
        assertTrue(away, away.contains("## Gym: somewhere not set up in Hyperlog"))
        assertFalse(away, away.contains("## Gym: Harbour") || away.contains("## Gym: Itis"))
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

    private val hotel = "```json\n" +
        """{"workout": {"name": "Hotel dumbbells, 45 min", "summary": "Dumbbells only.", "groups": [""" +
        """{"slots": [{"id": "row", "exercise": "chest_supported_db_row", "sets": [{"count": 3, "reps": "8-12", "rir": 1}]}]}]}}""" +
        "\n```"

    @Test
    fun withNoProgramOnlyAWorkoutComesBackAndGoesAboveStartSession() {
        compose.waitFor("Run this program")
        openCoach()
        compose.onNodeWithText("builds you a workout", substring = true).assertIsDisplayed()
        words().performTextInput("My knee hurts on lunges.")
        assertTrue(ask().contains("No program is running in Hyperlog"))
        // A Change has nothing to change.
        copy(elbow)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("No program is running, so there’s nothing for a Change to change.")
        // A workout opens its review, and Add to Today puts it on Today.
        copy(hotel)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Workout for today")
        compose.onNodeWithText("Chest-Supported DB Row").assertIsDisplayed()
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        compose.onNode(hasTestTag(com.hyperlog.app.ui.today.WorkoutCardTag)).assertIsDisplayed()
        compose.onNodeWithText("Ad-hoc session").assertIsDisplayed()
    }

    @Test
    fun buildMeAWorkoutOpensItsOptionsWithTheirDefaultsAndThePromptCarriesThem() {
        runStarter()
        openCoach()
        compose.onNodeWithText("Build me a workout").performClick()
        compose.onNode(hasTestTag(WorkoutOptionsTag)).assertIsDisplayed()
        compose.onNode(hasText("Normal") and androidx.compose.ui.test.isOn()).assertIsDisplayed()
        compose.onNode(hasText("1 h") and androidx.compose.ui.test.isOn()).assertIsDisplayed()
        // Words are optional with the options open.
        assertEquals("", wordsText())
        compose.onNodeWithText("Hard").performClick()
        compose.onNodeWithText("45 min").performClick()
        compose.onNodeWithText("Legs").performScrollTo().performClick()
        val prompt = ask()
        assertTrue(prompt, prompt.contains("Build me a workout for today:"))
        assertTrue(prompt, prompt.contains("- Effort: hard"))
        assertTrue(prompt, prompt.contains("- Time: 45 min"))
        assertTrue(prompt, prompt.contains("- Focus: Legs (Quads, Hamstrings, Adductors, Gastrocnemius, Soleus)."))
        // Closing the options takes them out of the request.
        compose.onNodeWithText("Build me a workout").performClick()
        assertTrue(compose.onAllNodes(hasTestTag(WorkoutOptionsTag)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Ask Claude").assertIsNotEnabled()
    }

    @Test
    fun aWorkoutIsTodaysMainStartWithTheProgramDayUnderItAndRemoveTakesItOff() {
        runStarter()
        openCoach()
        copy(hotel)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Add to Today")
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        // The workout comes first with its rest; the program day follows as a card with its own Start.
        compose.onNodeWithText("2:00 rest", substring = true).assertExists()
        compose.onNode(hasTestTag(com.hyperlog.app.ui.today.ProgramDayCardTag)).performScrollTo()
        compose.onNodeWithText("Week 1 · Ramp-in").assertIsDisplayed()
        // Another asks before it replaces this one.
        compose.openTab("Coach")
        compose.waitFor("In your words")
        copy(hotel.replace("Hotel dumbbells, 45 min", "Legs at home"))
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Add to Today")
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Replace today’s workout?")
        compose.onNodeWithText("Replace").performClick()
        compose.waitFor("Legs at home")
        assertTrue(compose.onAllNodes(hasText("Hotel dumbbells, 45 min")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("More for this workout").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitGone("Legs at home")
        // Undo puts it back as it was.
        compose.waitFor("Workout removed")
        compose.onNodeWithText("Undo").performClick()
        compose.waitFor("Legs at home")
    }

    @Test
    fun startingAWorkoutTrainsItAndLeavesTheProgramWhereItWas() {
        runStarter()
        openCoach()
        copy(hotel)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Add to Today")
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        // The floating Start workout starts the workout, not the program day.
        compose.onNodeWithText("Start workout").performClick()
        compose.waitFor("Reps")
        val session = runBlocking { db.sessions().runningSession()!! }
        assertTrue(session.workoutId != null)
        assertEquals(listOf("chest_supported_db_row"), runBlocking { db.sessions().sessionExercises(session.id) }.map { it.exerciseId })
        assertEquals(1, runBlocking { db.programs().active()!!.week })
        assertEquals(0, runBlocking { db.programs().active()!!.dayIndex })
    }

    @Test
    fun aWorkoutAskedForElsewhereStartsWithoutAGym() {
        runStarter()
        runBlocking { Gyms(db).addGym("Harbour") }
        openCoach()
        compose.waitFor("Elsewhere")
        compose.onNodeWithText("Elsewhere").performClick()
        copy(hotel)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Add to Today")
        compose.onNodeWithText("Elsewhere").assertIsDisplayed()
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        compose.onNodeWithText("Start workout").performClick()
        // The sheet names the workout and has Elsewhere picked.
        compose.waitFor("No gym: the usual dumbbells and plates")
        compose.onNode(hasText("Elsewhere") and isSelected()).assertExists()
        compose.onAllNodes(hasText("Start"))[compose.onAllNodes(hasText("Start")).fetchSemanticsNodes().size - 1].performClick()
        compose.waitFor("Reps")
        val session = runBlocking { db.sessions().runningSession()!! }
        assertEquals(null, session.gymId)
        assertTrue(session.workoutId != null)
    }

    @Test
    fun switchingTabsKeepsTheGymAndTheWorkoutsOptions() {
        runStarter()
        runBlocking { Gyms(db).addGym("Harbour"); Gyms(db).addGym("Itis") }
        openCoach()
        compose.waitFor("Itis")
        compose.onNodeWithText("Elsewhere").performClick()
        compose.onNodeWithText("Build me a workout").performClick()
        compose.onNodeWithText("Easy").performClick()
        compose.onNodeWithText("Back", substring = true).performScrollTo().performClick()
        compose.openTab("History")
        compose.openTab("Coach")
        compose.waitFor("In your words")
        compose.onNode(hasText("Elsewhere") and isSelected()).assertExists()
        compose.onNode(hasText("Easy") and androidx.compose.ui.test.isOn()).assertExists()
        words().performTextInput("Dumbbells to 30 kg.")
        val prompt = ask()
        assertTrue(prompt, prompt.contains("## Gym: somewhere not set up in Hyperlog"))
        assertTrue(prompt, prompt.contains("- Effort: easy"))
        assertTrue(prompt, prompt.contains("- Focus: Back ("))
    }

    @Test
    fun finishingAWorkoutWithNothingLoggedSaysItGoesBackOnToday() {
        runStarter()
        openCoach()
        copy(hotel)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Add to Today")
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        compose.onNodeWithText("Start workout").performClick()
        compose.waitFor("Reps")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Finish session").performClick()
        compose.waitFor("Nothing is logged yet, so the session is dropped and the workout goes back on Today.")
    }

    @Test
    fun changingTheOptionsOrTheGymAfterAskingMakesAskClaudeTheNextStepAgain() {
        runStarter()
        runBlocking { Gyms(db).addGym("Harbour"); Gyms(db).addGym("Itis") }
        openCoach()
        compose.waitFor("Itis")
        compose.onNodeWithText("Build me a workout").performClick()
        ask()
        compose.onNode(hasTestTag(NextStepTag)).assertTextEquals("Paste reply")
        compose.onNodeWithText("Hard").performClick()
        compose.onNode(hasTestTag(NextStepTag)).assertTextEquals("Ask Claude")
        ask()
        compose.onNodeWithText("Itis").performClick()
        compose.onNode(hasTestTag(NextStepTag)).assertTextEquals("Ask Claude")
    }

    @Test
    fun elsewhereNeedsWordsSayingWhatEquipmentIsThere() {
        runStarter()
        runBlocking { Gyms(db).addGym("Harbour") }
        openCoach()
        compose.waitFor("Elsewhere")
        compose.onNodeWithText("Elsewhere").performClick()
        compose.onNodeWithText("Build me a workout").performClick()
        compose.onNodeWithText("Ask Claude").assertIsNotEnabled()
        compose.onNodeWithText("Say what equipment is there", substring = true).assertExists()
        words().performTextInput("Dumbbells to 30 kg and a bench.")
        compose.onNodeWithText("Ask Claude").assertIsEnabled()
    }

    @Test
    fun acceptingAChangeWithAWorkoutOnTodayShowsTheProgramDay() {
        runStarter()
        openCoach()
        copy(hotel.replace("\"groups\": [", "\"groups\": [" + List(5) { i -> """{"slots": [{"id": "x$i", "exercise": "db_bench_press", "sets": [{"reps": 8}]}]},""" }.joinToString("")))
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Add to Today")
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        compose.openTab("Coach")
        compose.waitFor("In your words")
        copy(elbow)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Accept")
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Week 1 · Ramp-in").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Week 1 · Ramp-in").assertIsDisplayed()
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
