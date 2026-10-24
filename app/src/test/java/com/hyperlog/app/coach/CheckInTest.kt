package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A check-in with Claude (SPEC, Coaching → Check-in), through the fake [CoachModel]: a tile, Claude's
 * questions one a screen, and its ending.
 */
class CheckInTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val world = CheckInWorld(program, ids)
    private val frame = CheckInFrame("The program, the library and the sessions.", CoachTools.all(program))

    private fun coach(model: CoachModel) = CheckInCoach(model, world)

    @Test
    fun aTileGoesToClaudeAndEndsInWordsAfterAQuestion() = runTest {
        val model = FakeCoachModel.replies(ask("Which hand, and does pressing hurt?", "Left, only gripping", "Right, pressing too"), words("Use straps and train as planned."))
        val coach = coach(model)
        var checkIn = coach.start(Tile.Hurts, frame)
        assertEquals(CheckInStep.Question("Which hand, and does pressing hurt?", listOf("Left, only gripping", "Right, pressing too"), own = true), coach.step(checkIn))
        checkIn = coach.answer(checkIn, "Left, only gripping")
        assertEquals(CheckInStep.Words("Use straps and train as planned."), coach.step(checkIn))
        // Claude was told the tile, then the answer to its question, with the stable context first.
        assertEquals(listOf(CoachMessage.User("Something hurts.")), model.requests[0].messages)
        assertEquals(frame.context, model.requests[0].context)
        val answer = model.requests[1].messages.last() as CoachMessage.Results
        assertEquals(listOf(ToolResult("call-1", "Left, only gripping")), answer.results)
    }

    /** A Change for the starter program: its first slot swapped for an alternative, from today on. */
    private val swap: JsonObject
        get() {
            val slot = program.blocks.first().days.first().groups.first().slots.first { it.alternatives.isNotEmpty() }
            return change(slot.id, slot.alternatives.first().exercise)
        }

    private fun change(slot: String, exercise: String) = buildJsonObject {
        put("summary", "Swap for good")
        put("scope", "rest_of_program")
        putJsonArray("operations") {
            add(buildJsonObject { put("op", "swap"); put("slot", slot); put("exercise", exercise); put("reason", "hands") })
        }
    }

    @Test
    fun aChangeThroughItsToolEndsInItsReview() = runTest {
        val coach = coach(FakeCoachModel.replies(call(CoachTools.PROPOSE_CHANGE, swap)))
        val step = coach.step(coach.start(Tile.ChangeProgram, frame)) as CheckInStep.ChangeReview
        assertEquals(Scope.RestOfProgram, step.change.scope)
        assertEquals("Swap for good", step.change.summary)
    }

    @Test
    fun aReplyWithProblemsGoesBackTwiceThenShowsThemWithTryAgain() = runTest {
        val wrong = change("rows", "bb_row")
        val model = FakeCoachModel.replies(
            call(CoachTools.PROPOSE_CHANGE, wrong), call(CoachTools.PROPOSE_CHANGE, wrong), call(CoachTools.PROPOSE_CHANGE, wrong),
            call(CoachTools.PROPOSE_CHANGE, swap),
        )
        val coach = coach(model)
        val checkIn = coach.start(Tile.Hurts, frame)
        val problems = listOf("operations[0].slot: unknown slot \"rows\"", "operations[0].exercise: unknown exercise \"bb_row\"")
        // Claude was sent the problems as its tool's error twice, then you see them.
        assertEquals(CheckInStep.Problems(problems), coach.step(checkIn))
        assertEquals(3, model.requests.size)
        listOf(1, 2).forEach { i ->
            val sent = model.requests[i].messages.last() as CoachMessage.Results
            assertEquals(listOf(ToolResult("call-$i", problems.joinToString("\n"), isError = true)), sent.results)
        }
        // Try again is a new call, with the problems sent back once more.
        val again = coach.tryAgain(checkIn)
        assertEquals(4, model.requests.size)
        assertEquals(ToolResult("call-3", problems.joinToString("\n"), isError = true), (model.requests[3].messages.last() as CoachMessage.Results).results.single())
        assertTrue(coach.step(again) is CheckInStep.ChangeReview)
    }

    @Test
    fun backGoesToTheQuestionBeforeToChangeItsAnswerAndClaudeAsksAgainFromThere() = runTest {
        val model = FakeCoachModel.replies(
            ask("Which hand?", "Left", "Right"), ask("Does pressing hurt?", "Yes", "No"), ask("Does pressing hurt?", "Yes", "No"),
        )
        val coach = coach(model)
        var checkIn = coach.answer(coach.start(Tile.Hurts, frame), "Left")
        assertEquals("Does pressing hurt?", (coach.step(checkIn) as CheckInStep.Question).question)
        checkIn = coach.back(checkIn)!!
        assertEquals("Which hand?", (coach.step(checkIn) as CheckInStep.Question).question)
        assertEquals(2, model.requests.size)
        checkIn = coach.answer(checkIn, "Right")
        // Claude is asked again from the changed answer; the first answer is gone.
        val sent = model.requests[2].messages
        assertEquals(3, sent.size)
        assertEquals("Right", (sent.last() as CoachMessage.Results).results.single().content)
        assertEquals("Does pressing hurt?", (coach.step(checkIn) as CheckInStep.Question).question)
        // From the first question, Back goes to the tiles.
        assertEquals(null, coach.back(coach.back(checkIn)!!))
    }

    @Test
    fun backFromAFailedCallOrWhileThinkingShowsTheQuestionJustAnswered() = runTest {
        val model = FakeCoachModel(FakeCoachModel.Turn.Reply(ask("Which hand?", "Left", "Right")), FakeCoachModel.Turn.Fails(CoachError.NoConnection()))
        val coach = coach(model)
        val failed = coach.answer(coach.start(Tile.Hurts, frame), "Left")
        assertEquals(CheckInStep.Failed(CheckInFailure.NoConnection), coach.step(failed))
        assertEquals(CheckInStep.Question("Which hand?", listOf("Left", "Right"), own = true), coach.step(coach.back(failed)!!))
        // A call under way: the answer is in, nothing has come back.
        val thinking = coach.answered(coach.back(failed)!!, "Right")
        assertEquals(CheckInStep.Thinking, coach.step(thinking))
        assertEquals("Which hand?", (coach.step(coach.back(thinking)!!) as CheckInStep.Question).question)
    }

    @Test
    fun aRequestTheApiTurnsDownIsNotNoConnection() = runTest {
        val coach = coach(FakeCoachModel(FakeCoachModel.Turn.Fails(CoachError.Rejected())))
        assertEquals(CheckInStep.Failed(CheckInFailure.Rejected), coach.step(coach.start(Tile.Hurts, frame)))
    }

    @Test
    fun tellMeMoreGoesOnFromTheWordsAndDoneIsTheEnd() = runTest {
        val model = FakeCoachModel.replies(words("Train as planned."), words("Ramp-in already eases effort."))
        val coach = coach(model)
        val more = coach.tellMeMore(coach.start(Tile.Tired, frame))
        // A new step after the words: the first answer stays, above the new one.
        assertEquals(CheckInStep.Words("Ramp-in already eases effort.", earlier = listOf("Train as planned.")), coach.step(more))
        assertEquals(CoachMessage.User(CheckInCoach.TELL_ME_MORE), model.requests[1].messages.last())
        // Back from there shows the words before.
        assertEquals(CheckInStep.Words("Train as planned."), coach.step(coach.back(more)!!))
    }

    @Test
    fun aGymAnsweredGoesWithItsEquipmentAndIsWhereAWorkoutStarts() = runTest {
        val harbour = com.hyperlog.app.gym.Gym(4, "Harbour", emptyList(), missing = setOf("seated_cable_row"))
        val model = FakeCoachModel.replies(ask("Where are you training?", "Harbour", "Elsewhere"), words("Fine."), words("Fine."))
        val coach = CheckInCoach(model, world.copy(gyms = listOf(harbour), names = mapOf("seated_cable_row" to "Seated Cable Row")))
        val asked = coach.start(Tile.Gym, frame)
        val atHarbour = coach.answer(asked, "Harbour")
        val sent = (model.requests[1].messages.last() as CoachMessage.Results).results.single().content
        assertTrue(sent, sent.startsWith("Harbour\n\n## Gym: Harbour"))
        assertTrue(sent, sent.contains("Not here, so swap them out: seated_cable_row (Seated Cable Row)"))
        assertEquals(4L, atHarbour.gym)
        // Elsewhere: no gym, and Claude knows the equipment is in my words.
        val elsewhere = coach.answer(coach.back(atHarbour)!!, "Elsewhere")
        assertEquals(null, elsewhere.gym)
        assertTrue(elsewhere.elsewhere)
        val told = (model.requests[2].messages.last() as CoachMessage.Results).results.single().content
        assertTrue(told, told.startsWith("Elsewhere\n\nI'm not at any of my gyms."))
    }

    @Test
    fun everyTileReachesWordsAChangeAndAWorkout() = runTest {
        val workout = Json.parseToJsonElement(WorkoutJson.encode(WorkoutFormat.example(library.exercises))).jsonObject.getValue("workout").jsonObject
        Tile.entries.forEach { tile ->
            val endings = listOf(words("Train as planned."), call(CoachTools.PROPOSE_CHANGE, swap), call(CoachTools.PROPOSE_WORKOUT, workout))
            val steps = endings.map { ending ->
                val coach = coach(FakeCoachModel.replies(ask("Tell me more?", "Yes", "No"), ending))
                val checkIn = coach.answer(coach.start(tile, frame), "Yes")
                assertEquals(CoachMessage.User(tile.words), checkIn.messages.first())
                coach.step(checkIn)
            }
            assertTrue("$tile: ${steps[0]}", steps[0] is CheckInStep.Words)
            assertTrue("$tile: ${steps[1]}", steps[1] is CheckInStep.ChangeReview)
            assertTrue("$tile: ${steps[2]}", steps[2] is CheckInStep.WorkoutReview)
        }
    }

    @Test
    fun theCheckInAChangeKeepsIsItsOpeningAndEachQuestionWithYourAnswer() = runTest {
        val model = FakeCoachModel.replies(
            ask("Which hand?", "Left", "Right"), call(CoachTools.PROPOSE_CHANGE, change("rows", "x")), ask("Does pressing hurt?", "Yes", "No"),
            call(CoachTools.PROPOSE_CHANGE, swap),
        )
        val coach = coach(model)
        val checkIn = coach.answer(coach.answer(coach.start(Tile.Hurts, frame), "Left"), "No")
        assertEquals(
            SavedCheckIn("Something hurts.", listOf(SavedCheckIn.Step("Which hand?", "Left"), SavedCheckIn.Step("Does pressing hurt?", "No"))),
            CheckInCoach.saved(checkIn),
        )
    }

    @Test
    fun aProposalWinsOverAQuestionAndOnlyItsCallGetsItsProblems() = runTest {
        val both = CoachReply(
            "",
            ask("Which hand?", "Left", "Right").toolCalls + call(CoachTools.PROPOSE_CHANGE, change("rows", "x")).toolCalls,
        )
        val model = FakeCoachModel.replies(both, words("Fine."))
        val coach = coach(model)
        coach.start(Tile.Hurts, frame)
        val sent = (model.requests[1].messages.last() as CoachMessage.Results).results
        assertEquals(listOf("call-1", "call-2"), sent.map { it.callId })
        assertEquals(CheckInCoach.ONE_AT_A_TIME, sent[0].content)
        assertTrue(sent[1].isError && sent[1].content.contains("unknown slot"))
    }

    /** A new program as propose_program sends it: the starter program's file, with one exercise renamed to [first]'s. */
    private fun newProgram(first: String = "close_grip_bench_press") = buildJsonObject {
        put("program", Json.parseToJsonElement(File("src/main/assets/${StarterProgram.ASSET}").readText().replace("close_grip_bench_press", first)))
        putJsonArray("swaps") { add(buildJsonObject { put("from", "Close-Grip Floor Press"); put("to", "close_grip_bench_press") }) }
        putJsonArray("newExercises") {}
    }

    @Test
    fun aNewProgramEndsInItsReviewWithItsSwaps() = runTest {
        val step = coach(FakeCoachModel.replies(call(CoachTools.PROPOSE_PROGRAM, newProgram()))).let { it.step(it.start(Tile.NewProgram, frame)) }
        val proposal = (step as CheckInStep.ProgramReview).proposal
        assertEquals(program.title, proposal.program.title)
        assertEquals(listOf(ProgramProposal.Swap("Close-Grip Floor Press", "close_grip_bench_press")), proposal.swaps)
    }

    @Test
    fun aNewProgramWithProblemsGoesBackToClaudeToFix() = runTest {
        val model = FakeCoachModel.replies(call(CoachTools.PROPOSE_PROGRAM, newProgram("no_such_press")), call(CoachTools.PROPOSE_PROGRAM, newProgram()))
        val coach = coach(model)
        val checkIn = coach.start(Tile.NewProgram, frame)
        assertTrue(coach.step(checkIn) is CheckInStep.ProgramReview)
        val sent = (model.requests[1].messages.last() as CoachMessage.Results).results.single()
        assertTrue(sent.isError)
        assertTrue(sent.content, sent.content.lines().all { it.startsWith("program.blocks[") && it.endsWith("unknown exercise \"no_such_press\"") })
    }

    @Test
    fun aStepCanOfferAttachAFileWhichGoesToClaudeAsTheAnswer() = runTest {
        val model = FakeCoachModel.replies(ask("Build it from a file you have?", "From my answers", "From scratch", attach = true), words("Read it."))
        val coach = coach(model)
        val asked = coach.start(Tile.NewProgram, frame)
        assertEquals(CheckInStep.Question("Build it from a file you have?", listOf("From my answers", "From scratch"), own = true, attach = true), coach.step(asked))
        val file = Attachment("Nippard PPL.pdf", Attachment.PDF, byteArrayOf(1, 2, 3))
        val attached = coach.attach(asked, file)
        val sent = (model.requests[1].messages.last() as CoachMessage.Results).results.single()
        assertEquals("I attached Nippard PPL.pdf.", sent.content)
        assertEquals(file, sent.attachment)
        // The check-in shows the file's name as the answer, and Back opens no words of yours.
        assertEquals(SavedCheckIn.Step("Build it from a file you have?", "Nippard PPL.pdf"), CheckInCoach.saved(attached).steps.single())
        // A program from it says which file.
        assertEquals("Nippard PPL.pdf", attached.file)
        assertEquals(null, CheckInCoach.ownAnswer(attached))
    }

    @Test
    fun aFileIsntKeptSoACheckInReadBackWithoutItAsksForItAgainBeforeTheNextCall() = runTest {
        val model = FakeCoachModel.replies(ask("Attach the program?", "From my answers", "From scratch", attach = true), call(CoachTools.PROPOSE_PROGRAM, newProgram()))
        val coach = coach(model)
        val attached = coach.attach(coach.start(Tile.NewProgram, frame), Attachment("Nippard PPL.pdf", Attachment.PDF, byteArrayOf(1, 2, 3)))
        // Saved and read back, the file's bytes are gone; the review it led to still shows.
        val json = Json { ignoreUnknownKeys = true }
        val read = json.decodeFromString(CheckIn.serializer(), json.encodeToString(CheckIn.serializer(), attached))
        assertEquals(null, read.turns.first().answer!!.results.single().attachment!!.data)
        assertTrue(coach.step(read) is CheckInStep.ProgramReview)
        // A call that would carry it goes back to the step that took it instead, and Claude isn't asked.
        val back = coach.run(read.copy(turns = read.turns.dropLast(1) + read.turns.last().copy(answer = Answer(text = "Fewer days."))))
        assertEquals("Attach the program?", (coach.step(back) as CheckInStep.Question).question)
        assertEquals(1, back.turns.size)
        // The step says why it's back: the file to attach again.
        assertEquals("Nippard PPL.pdf", back.lostFile)
        assertEquals(null, coach.attached(back, Attachment("Nippard PPL.pdf", Attachment.PDF, byteArrayOf(1))).lostFile)
        assertEquals(2, model.requests.size)
    }

    @Test
    fun anAttachmentIsEqualByItsBytes() {
        assertEquals(Attachment("a.pdf", Attachment.PDF, byteArrayOf(1, 2)), Attachment("a.pdf", Attachment.PDF, byteArrayOf(1, 2)))
        assertTrue(Attachment("a.pdf", Attachment.PDF, byteArrayOf(1)) != Attachment("a.pdf", Attachment.PDF, byteArrayOf(2)))
    }

    /** Claude's calls, numbered in each test. */
    private var calls = 0

    fun ask(question: String, vararg answers: String, own: Boolean = true, attach: Boolean = false) = CoachReply(
        text = "",
        toolCalls = listOf(ToolCall("call-${++calls}", CoachTools.ASK, buildJsonObject {
            put("question", question)
            putJsonArray("answers") { answers.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            put("own", own)
            put("attach", attach)
        })),
    )

    fun words(text: String) = CoachReply(text)

    fun call(name: String, input: JsonObject) = CoachReply("", listOf(ToolCall("call-${++calls}", name, input)))
}
