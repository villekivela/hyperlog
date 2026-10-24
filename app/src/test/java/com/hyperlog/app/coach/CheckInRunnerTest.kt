package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A check-in as the Coach tab runs it: kept step by step in a file, so it survives a tab switch and
 * leaving the app until it ends (SPEC, Coaching → Check-in).
 */
class CheckInRunnerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val frame = CheckInFrame("Context", CoachTools.all(program))
    private val file by lazy { File(folder.root, "check_in.json") }

    /** A runner whose file work runs on the test's clock. */
    private fun TestScope.runner(scope: CoroutineScope, model: CoachModel) = CheckInRunner(
        scope, CheckInStore(file), model = { model }, world = { CheckInWorld(program, ids) }, frame = { frame },
        io = StandardTestDispatcher(testScheduler),
    )

    private var calls = 0

    private fun ask(question: String, vararg answers: String) = CoachReply(
        "",
        listOf(ToolCall("call-${++calls}", CoachTools.ASK, buildJsonObject {
            put("question", question)
            putJsonArray("answers") { answers.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            put("own", true)
        })),
    )

    private fun TestScope.step(runner: CheckInRunner): CheckInStep? {
        runCurrent()
        return runner.state.value?.step
    }

    @Test
    fun anUnfinishedCheckInComesBackAfterLeavingTheApp() = runTest {
        val first = runner(backgroundScope, FakeCoachModel.replies(ask("Which hand?", "Left", "Right")))
        first.start(Tile.Hurts)
        assertEquals("Which hand?", (step(first) as CheckInStep.Question).question)
        // The app starts again: the same step, with no call.
        val model = FakeCoachModel.replies()
        val again = runner(backgroundScope, model)
        again.restore()
        assertEquals("Which hand?", (step(again) as CheckInStep.Question).question)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun aCheckInLeftWhileThinkingAsksAgainOnComingBack() = runTest {
        val hanging = object : CoachModel {
            val reply = CompletableDeferred<CoachReply>()
            override suspend fun reply(request: CoachRequest) = reply.await()
        }
        val first = runner(backgroundScope, hanging)
        first.start(Tile.Tired)
        assertEquals(CheckInStep.Thinking, step(first))
        val model = FakeCoachModel.replies(CoachReply("Train as planned."))
        val again = runner(backgroundScope, model)
        again.restore()
        assertEquals(CheckInStep.Words("Train as planned."), step(again))
        assertEquals(listOf(CoachMessage.User(Tile.Tired.words)), model.requests.single().messages)
    }

    @Test
    fun closeDropsTheCheckInAndBackWhileThinkingStopsTheCall() = runTest {
        val hanging = object : CoachModel {
            var asked = 0
            override suspend fun reply(request: CoachRequest): CoachReply {
                if (++asked == 1) return ask("Which hand?", "Left", "Right")
                return CompletableDeferred<CoachReply>().await()
            }
        }
        val runner = runner(backgroundScope, hanging)
        runner.start(Tile.Hurts)
        step(runner)
        runner.answer("Left")
        assertEquals(CheckInStep.Thinking, step(runner))
        assertTrue(runner.thinking.value)
        runner.back()
        assertEquals("Which hand?", (step(runner) as CheckInStep.Question).question)
        assertFalse(runner.thinking.value)
        // × drops it, here and for good.
        runner.close()
        assertNull(step(runner))
        assertNull(CheckInStore(file).load())
    }

    @Test
    fun aSlowDiskNeverHoldsUpAnAnswer() = runTest {
        // The file's writes never finish: what shows, and the next call, don't wait for them.
        val stalled = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) = Unit
        }
        val model = FakeCoachModel.replies(ask("Which hand?", "Left", "Right"), ask("Does pressing hurt?", "Yes", "No"))
        val runner = CheckInRunner(backgroundScope, CheckInStore(file), { model }, { CheckInWorld(program, ids) }, { frame }, io = stalled)
        runner.start(Tile.Hurts)
        assertEquals("Which hand?", (step(runner) as CheckInStep.Question).question)
        runner.answer("Left")
        assertEquals("Does pressing hurt?", (step(runner) as CheckInStep.Question).question)
    }
}
