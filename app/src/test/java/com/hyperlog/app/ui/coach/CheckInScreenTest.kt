package com.hyperlog.app.ui.coach

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hyperlog.app.click
import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.CheckInFrame
import com.hyperlog.app.coach.CheckInRunner
import com.hyperlog.app.coach.CheckInStore
import com.hyperlog.app.coach.CheckInWorld
import com.hyperlog.app.coach.CoachError
import com.hyperlog.app.coach.CoachMessage
import com.hyperlog.app.coach.CoachReply
import com.hyperlog.app.coach.CoachTools
import com.hyperlog.app.coach.FakeCoachModel
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.SavedCheckIn
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.ToolCall
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.waitFor
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** The check-in's screens through the fake [com.hyperlog.app.coach.CoachModel] (SPEC, Coaching → Check-in). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class CheckInScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val made = SavedCheckIn("Something hurts.", listOf(SavedCheckIn.Step("Which hand?", "Left, only gripping")))
    private val record = ChangeRecord(
        AcceptedChange(3, Change("No grip work for a week", Scope.RestOfProgram, listOf(Operation.AdjustRir(null, 1))), oct13.minusDays(2), Position(1, 1), Position(1, 1), program.blockAt(1)!!.id),
        acceptedAt = 3, revertedAt = null, checkIn = made,
    )
    private val plan = ActiveProgram(1, program, Position(1, 1), history = listOf(record), today = oct13)

    private var calls = 0

    private fun ask(question: String, vararg answers: String, attach: Boolean = false) = CoachReply(
        "",
        listOf(ToolCall("call-${++calls}", CoachTools.ASK, buildJsonObject {
            put("question", question)
            putJsonArray("answers") { answers.forEach { add(JsonPrimitive(it)) } }
            put("own", true)
            put("attach", attach)
        })),
    )

    /** Attach was tapped: the caller opens the file picker. */
    private var attaching = 0

    /** The screen over a runner playing [model]. */
    private fun show(model: com.hyperlog.app.coach.CoachModel, lowest: List<Pair<String, Double>> = emptyList(), attachNote: String? = null, onRunner: (CheckInRunner) -> Unit = {}) {
        compose.setContent {
            HyperlogTheme {
                val scope = rememberCoroutineScope()
                val runner = remember {
                    CheckInRunner(scope, CheckInStore(File(folder.root, "check_in.json")), { model }, { CheckInWorld(program, ids) }, { CheckInFrame("Context", CoachTools.all(program)) })
                }
                val view by runner.state.collectAsState()
                val progress by runner.progress.collectAsState()
                LaunchedEffect(runner) { onRunner(runner) }
                var inForce by remember { mutableStateOf(false) }
                CheckInScreen(
                    view, plan,
                    onTile = runner::start, onAnswer = runner::answer, onLeave = {}, onBack = runner::back, onClose = runner::close,
                    onTellMeMore = runner::tellMeMore, onTryAgain = runner::tryAgain, lowest = lowest,
                    inForceOpen = inForce, onInForceOpen = { inForce = it },
                    onHowItsGoing = { runner.start(com.hyperlog.app.coach.CheckInContext.howItsGoing(it), com.hyperlog.app.coach.Opening.HowsItGoing) },
                    onAttach = { attaching++ }, attachNote = attachNote, progress = progress,
                )
            }
        }
    }

    @Test
    fun howItsGoingSendsTheCheckInThatMadeTheChange() {
        val model = FakeCoachModel.replies(CoachReply("Glad the hand is better."), CoachReply("Keep the straps for two more weeks."))
        show(model)
        compose.click("1 change in force")
        compose.waitFor("No grip work for a week")
        compose.click("How’s it going?")
        compose.waitFor("Glad the hand is better.")
        // Tell me more adds Claude's next words under the first.
        compose.click("Tell me more")
        compose.waitFor("Keep the straps for two more weeks.")
        compose.onNodeWithText("Glad the hand is better.").assertExists()
        val opening = (model.requests.first().messages.first() as CoachMessage.User).text
        assertTrue(opening, opening.contains("\"No grip work for a week\" (id 3"))
        assertTrue(opening, opening.contains("The check-in that made it:\n> Something hurts.\nClaude: Which hand?\n> Left, only gripping"))
        // Done ends it: the tiles again.
        compose.click("Done")
        compose.waitFor("What’s going on?")
    }

    @Test
    fun aStepThatOffersAFileHasAttachUnderItsAnswers() {
        show(FakeCoachModel.replies(ask("Do you have the program as a file?", "Build it from my answers", "I'll describe it", attach = true)), attachNote = "Couldn’t read that file.")
        compose.click("New program")
        compose.waitFor("Do you have the program as a file?")
        compose.onNodeWithText("Couldn’t read that file.").assertExists()
        compose.click("Attach a spreadsheet, PDF or photo")
        assertEquals(1, attaching)
    }

    /** Claude that says it's writing [tool] after the step answered, and then waits. */
    private class Writing(private val first: CoachReply, private val tool: String) : com.hyperlog.app.coach.CoachModel {
        private var calls = 0
        override suspend fun reply(request: com.hyperlog.app.coach.CoachRequest, progress: (com.hyperlog.app.coach.CoachProgress) -> Unit): CoachReply {
            if (calls++ == 0) return first
            progress(com.hyperlog.app.coach.CoachProgress.Thinking)
            kotlinx.coroutines.delay(1_000)
            progress(com.hyperlog.app.coach.CoachProgress.Writing(tool))
            return kotlinx.coroutines.CompletableDeferred<CoachReply>().await()
        }
    }

    @Test
    fun whileClaudeRepliesTheStepSaysWhatItIsDoing() {
        var runner: CheckInRunner? = null
        show(Writing(ask("Do you have the program as a file?", "From my answers", "I'll describe it", attach = true), CoachTools.PROPOSE_PROGRAM), onRunner = { runner = it })
        compose.click("New program")
        compose.waitFor("Do you have the program as a file?")
        runner!!.attach(com.hyperlog.app.coach.Attachment("Essentials.xlsx", com.hyperlog.app.coach.Attachment.TEXT, "## Sheet: Week 1".toByteArray()))
        // Reading the file first, then writing the program.
        compose.waitFor("Reading your spreadsheet…")
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitFor("Writing the program…")
    }

    @Test
    fun aReplyCutOffSaysSoAndOffersTryAgain() {
        show(FakeCoachModel(FakeCoachModel.Turn.Fails(com.hyperlog.app.coach.CoachError.Dropped()), FakeCoachModel.Turn.Reply(CoachReply("Fine."))))
        compose.click("Something hurts")
        compose.waitFor("Claude’s reply was cut off.")
        compose.click("Try again")
        compose.waitFor("Fine.")
    }

    @Test
    fun aStepThatDoesntOfferAFileHasNoAttach() {
        show(FakeCoachModel.replies(ask("Which hand?", "Left", "Right")))
        compose.click("Something hurts")
        compose.waitFor("Which hand?")
        compose.onNodeWithText("Attach a spreadsheet, PDF or photo").assertDoesNotExist()
    }

    @Test
    fun focusAnswersCarryTheirMarksAndYouMayTypeYourOwn() {
        val model = FakeCoachModel.replies(ask("What to focus on?", "Back", "Legs", "Balanced full body"), CoachReply("Fine."))
        show(model, lowest = listOf("Back" to 4.0, "Legs" to 0.0))
        compose.click("Build me a workout")
        compose.waitFor("Back · 4 sets")
        compose.onNodeWithText("Legs · 0 sets").assertExists()
        compose.click("Type my own")
        compose.onNode(hasSetTextAction()).performTextInput("Shoulders, my left one is fine now")
        compose.onNodeWithText("Send", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Send")).performClick()
        compose.waitFor("Fine.")
        val answer = (model.requests[1].messages.last() as CoachMessage.Results).results.single().content
        assertEquals("Shoulders, my left one is fine now", answer)
    }

    @Test
    fun noConnectionOffersTryAgainAndARefusedKeyOffersAskClaude() {
        val model = FakeCoachModel(
            FakeCoachModel.Turn.Fails(CoachError.NoConnection()),
            FakeCoachModel.Turn.Fails(CoachError.KeyRefused()),
            FakeCoachModel.Turn.Fails(CoachError.Rejected()),
        )
        show(model)
        compose.click("Tired or slept badly")
        compose.waitFor("Couldn’t reach Claude.")
        compose.click("Try again")
        compose.waitFor("Claude doesn’t accept this key. Check it in Settings.")
        compose.onNodeWithText("Ask Claude").assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        // A request the API turns down says so, with Try again and Ask Claude.
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.click("Something hurts")
        compose.waitFor("Claude couldn’t take this request.")
        compose.onNodeWithText("Ask Claude").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }
}
