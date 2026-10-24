package com.hyperlog.app.ui.coach

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.coach.Tile
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

/** Check-in without a key: the tiles, then Ask Claude and Paste reply (SPEC, Coaching → Without a key: Ask Claude). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class AskClaudeAppTest {
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

    /** Coach's tiles, with "What’s going on?" asked. */
    private fun openTiles() {
        compose.openTab("Coach")
        compose.waitFor("What’s going on?")
    }

    /** Coach, then a tile: the words screen of Ask Claude, with the tile's sentence in it. */
    private fun openCoach(tile: String = "Something else") {
        openTiles()
        compose.onNodeWithText(tile).performClick()
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
    fun everyTileLeadsToAskClaudeWithAPromptHoldingTheTileAndTheWords() {
        runStarter()
        val tiles = mapOf(
            Tile.Hurts to "Something hurts", Tile.ShortOnTime to "Short on time", Tile.Gym to "Different gym or none",
            Tile.Tired to "Tired or slept badly", Tile.ChangeProgram to "Change my program", Tile.Workout to "Build me a workout",
            Tile.NewProgram to "New program", Tile.SomethingElse to "Something else",
        )
        assertEquals(Tile.entries.toSet(), tiles.keys)
        tiles.forEach { (tile, label) ->
            openCoach(label)
            words().performTextInput(" Words for ${tile.name}.")
            val prompt = ask()
            assertTrue(prompt, prompt.contains("> ${tile.words}\n"))
            assertTrue(prompt, prompt.contains("Words for ${tile.name}."))
            assertTrue(prompt, prompt.contains("## Program: Hyperlog Full Body"))
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitFor("What’s going on?")
        }
    }

    @Test
    fun theTilesSentenceIsInTheWordsToEditAndClearingItKeepsTheTile() {
        runStarter()
        openCoach("Something hurts")
        assertEquals("Something hurts: ", wordsText())
        words().performTextInput("left elbow.")
        assertEquals("Something hurts: left elbow.", wordsText())
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("What’s going on?")
        compose.onNodeWithText("Short on time").performClick()
        compose.waitFor("In your words")
        assertEquals("I’m short on time today: I have 45 minutes.", wordsText())
        words().performTextClearance()
        val prompt = ask()
        assertTrue(prompt, prompt.contains("> I'm short on time."))
    }

    @Test
    fun withoutAKeyHowsItGoingOnAChangeInForceOpensTheWordsAboutIt() {
        runStarter()
        openCoach()
        copy(elbow)
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Accept")
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        openTiles()
        compose.onNodeWithText("1 change in force").performClick()
        compose.waitFor("How’s it going?")
        compose.onNodeWithText("How’s it going?").performClick()
        compose.waitFor("In your words")
        assertTrue(wordsText(), wordsText().startsWith("How's it going with this Change?"))
        assertTrue(wordsText(), wordsText().contains("Elbow: machine curls"))
    }

    @Test
    fun askClaudeOpensTheShareSheetWithTheWholePrompt() {
        runStarter()
        openCoach()
        words().performTextInput("My left elbow aches on curls.")
        compose.onNodeWithText("Ask Claude").performClick()

        compose.waitUntil(5_000) { shadowOf(compose.activity).peekNextStartedActivity() != null }
        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        val prompt = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(prompt.contains("> My left elbow aches on curls."))
        assertTrue(prompt.contains("## Program: Hyperlog Full Body"))
        assertTrue(prompt.contains("## Sessions of the last 4 weeks"))
        assertTrue(prompt.contains("## Exercise library"))
        assertTrue(prompt.contains("\"op\": \"repeat_week\""))
        assertTrue(prompt.contains("## Weekly sets per muscle"))
    }

    @Test
    fun theKeyboardDoesNotCoverTheWordsField() {
        // Found on the phone: the field stayed under the keyboard, so the words were typed blind.
        runStarter()
        openCoach()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        val root = compose.activity.window.decorView
        val keyboardPx = root.height / 2
        val keyboard = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardPx))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(root, keyboard) }
        compose.waitForIdle()

        val bottom = field.fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("field bottom $bottom under the keyboard at ${root.height - keyboardPx}", bottom <= root.height - keyboardPx)
    }

    @Test
    fun aPastedChangeOpensItsDiff() {
        runStarter()
        openCoach()
        copy(
            "Here it is:\n```json\n" +
                """{"summary": "Elbow: no drop set on curls", "scope": "rest_of_block", "operations": [{"op": "swap", "slot": "curl", "exercise": "machine_preacher_curl"}]}""" +
                "\n```",
        )
        compose.onNodeWithText("Paste reply").performClick()

        compose.waitFor("Accept")
        compose.onNodeWithText("Elbow: no drop set on curls").assertIsDisplayed()
        compose.onNodeWithText("In place of Bayesian Cable Curl").assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        assertEquals(1, runBlocking { db.planChanges().changes(db.programs().active()!!.id).size })
    }

    @Test
    fun anInvalidPasteSaysWhatsWrongAndChangesNothing() {
        runStarter()
        openCoach()
        copy("""{"summary": "Rows", "scope": "rest_of_program", "operations": [{"op": "remove", "slot": "rows"}, {"op": "adjust_load", "percent": 80}]}""")
        compose.onNodeWithText("Paste reply").performClick()

        compose.waitFor("2 problems", substring = true)
        compose.onNodeWithText("unknown slot \"rows\"").assertIsDisplayed()
        compose.onNodeWithText("must be −50 to 50").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Accept")).fetchSemanticsNodes().isEmpty())
        assertEquals(0, runBlocking { db.planChanges().changes(db.programs().active()!!.id).size })

        // A reply copied short says so once, with nothing to hand back to Claude.
        copy("Here you go: {\"summary\":\"Hands\",\"scope\":{\"from\":")
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("cut off", substring = true)
        assertTrue(compose.onAllNodes(hasText("problem", substring = true)).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Copy problems")).fetchSemanticsNodes().isEmpty())

        // A reply with no JSON says so too.
        copy("Sounds good, rest up!")
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("No Change, workout or program found", substring = true)
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
    fun yourTrainingDaysCountTimeOffWithNoGymSetUp() {
        runStarter()
        // Nine days since the last session, but you train only on the weekday two days ago: 2 days off.
        val planned = LocalDate.now().minusDays(2).dayOfWeek
        runBlocking {
            db.sessions().insertSession(SessionEntity(startedAt = System.currentTimeMillis() - 9 * DAY, finishedAt = System.currentTimeMillis() - 9 * DAY + 3_600_000))
            com.hyperlog.app.week.WeekdayPlans(db).set(com.hyperlog.app.week.WeekdayPlan(mapOf(planned to null)))
        }
        compose.openTab("Program")
        compose.waitFor("Ramp-in · weeks 1–2", substring = true)
        compose.openTab("Today")
        compose.waitFor("Start session")
        assertTrue(compose.onAllNodes(hasText("since your last session", substring = true)).fetchSemanticsNodes().isEmpty())

        openCoach()
        words().performTextInput("Back today.")
        val name = planned.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        val prompt = ask()
        assertTrue(prompt, prompt.contains("I plan to train on $name, so that's 2 days off."))
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
        """{"workout": {"name": "Hotel dumbbells, 45 min", "summary": "Dumbbells only, rows @ 1.", "groups": [""" +
        """{"slots": [{"id": "row", "exercise": "chest_supported_db_row", "sets": [{"count": 3, "reps": "8-12", "rir": 1}]}]}]}}""" +
        "\n```"

    @Test
    fun withNoProgramOnlyAWorkoutComesBackAndGoesAboveStartSession() {
        compose.waitFor("Run this program")
        openCoach()
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
        // The sets show RIR as a chip; Claude's own words stay as Claude wrote them.
        compose.onNodeWithText("3 × 8–12\u00A01 RIR", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Dumbbells only, rows @ 1.").assertIsDisplayed()
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        compose.onNode(hasTestTag(com.hyperlog.app.ui.today.WorkoutCardTag)).assertIsDisplayed()
        compose.onNodeWithText("Ad-hoc session").assertIsDisplayed()
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
        openCoach()
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
    fun acceptingAChangeWithAWorkoutOnTodayShowsTheProgramDay() {
        runStarter()
        openCoach()
        copy(hotel.replace("\"groups\": [", "\"groups\": [" + List(5) { i -> """{"slots": [{"id": "x$i", "exercise": "db_bench_press", "sets": [{"reps": 8}]}]},""" }.joinToString("")))
        compose.onNodeWithText("Paste reply").performClick()
        compose.waitFor("Add to Today")
        compose.onNodeWithText("Add to Today").performClick()
        compose.waitFor("Hotel dumbbells, 45 min")
        openCoach()
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
