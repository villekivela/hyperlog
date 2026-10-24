package com.hyperlog.app.ui.coach

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

    private fun ask(question: String, vararg answers: String) = CoachReply(
        "",
        listOf(ToolCall("call-${++calls}", CoachTools.ASK, buildJsonObject {
            put("question", question)
            putJsonArray("answers") { answers.forEach { add(JsonPrimitive(it)) } }
            put("own", true)
        })),
    )

    /** The screen over a runner playing [model]. */
    private fun show(model: FakeCoachModel, lowest: List<Pair<String, Double>> = emptyList()) {
        compose.setContent {
            HyperlogTheme {
                val scope = rememberCoroutineScope()
                val runner = remember {
                    CheckInRunner(scope, CheckInStore(File(folder.root, "check_in.json")), { model }, { CheckInWorld(program, ids) }, { CheckInFrame("Context", CoachTools.all(program)) })
                }
                val view by runner.state.collectAsState()
                var inForce by remember { mutableStateOf(false) }
                CheckInScreen(
                    view, plan,
                    onTile = runner::start, onAnswer = runner::answer, onLeave = {}, onBack = runner::back, onClose = runner::close,
                    onTellMeMore = runner::tellMeMore, onTryAgain = runner::tryAgain, lowest = lowest,
                    inForceOpen = inForce, onInForceOpen = { inForce = it },
                    onHowItsGoing = { runner.start(com.hyperlog.app.coach.CheckInContext.howItsGoing(it)) },
                )
            }
        }
    }

    @Test
    fun howItsGoingSendsTheCheckInThatMadeTheChange() {
        val model = FakeCoachModel.replies(CoachReply("Glad the hand is better."))
        show(model)
        compose.click("1 change in force")
        compose.waitFor("No grip work for a week")
        compose.click("How’s it going?")
        compose.waitFor("Glad the hand is better.")
        val opening = (model.requests.single().messages.first() as CoachMessage.User).text
        assertTrue(opening, opening.contains("\"No grip work for a week\" (id 3"))
        assertTrue(opening, opening.contains("The check-in that made it:\n> Something hurts.\nClaude: Which hand?\n> Left, only gripping"))
        // Done ends it: the tiles again.
        compose.click("Done")
        compose.waitFor("What’s going on?")
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
        )
        show(model)
        compose.click("Tired or slept badly")
        compose.waitFor("Couldn’t reach Claude.")
        compose.click("Try again")
        compose.waitFor("Claude doesn’t accept this key. Check it in Settings.")
        compose.onNodeWithText("Ask Claude").assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }
}
