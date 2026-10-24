package com.hyperlog.app.coach

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

/**
 * The one test that calls the real API (SPEC, Coaching → With a Claude key). It runs only with
 * `ANTHROPIC_API_KEY` set and is skipped otherwise, so no test or CI job needs a key. It costs a
 * cent or two. On Robolectric, where the client's Logcat warnings work.
 */
@RunWith(RobolectricTestRunner::class)
class ClaudeClientLiveTest {
    private val key: String? = System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }

    private val ask = CoachTool(
        name = "ask",
        description = "Ask the lifter one question, with 2 to 4 short answers to tap.",
        inputSchema = Json.parseToJsonElement(
            """{"type":"object","properties":{"question":{"type":"string"},"answers":{"type":"array","items":{"type":"string"}}},""" +
                """"required":["question","answers"],"additionalProperties":false}""",
        ).jsonObject,
    )

    @Test
    fun aKeyIsCheckedAndAQuestionComesBackThroughAsk() = runTest(timeout = 5.minutes) {
        assumeTrue("ANTHROPIC_API_KEY isn't set", key != null)
        val client = ClaudeClient(key!!)
        client.checkKey()
        val reply = client.reply(
            CoachRequest(
                context = "You coach a lifter on a 3-day full-body program. Ask what you need through the ask tool, one question at a time.",
                tools = listOf(ask),
                messages = listOf(CoachMessage.User("Something hurts: my right hand, when I grip.")),
            ),
        )
        assertEquals("ask", reply.toolCalls.single().name)
        assertTrue(reply.toolCalls.single().input.containsKey("question"))
        assertTrue(reply.usage.sumOf { it.input + it.cacheWrite + it.cacheRead } > 0)
        assertTrue(reply.usage.sumOf { it.output } > 0)
    }

    /**
     * A whole check-in's first two calls with the app's own frame: the API compiles the strict
     * schemas, Claude asks through ask, and its reply, thinking included, goes back with the answer.
     */
    @Test
    fun aCheckInWithTheAppsToolsAsksAndGoesOn() = runTest(timeout = 5.minutes) {
        assumeTrue("ANTHROPIC_API_KEY isn't set", key != null)
        val library = com.hyperlog.app.library.LibrarySeed.parse(java.io.File("src/main/assets/exercises.json").readText())
        val ids = library.exercises.map { it.id }.toSet()
        val program = (com.hyperlog.app.program.ProgramParser.parse(
            java.io.File("src/main/assets/${com.hyperlog.app.program.StarterProgram.ASSET}").readText(), ids, library.merged,
        ) as com.hyperlog.app.program.ProgramParser.Result.Valid).program
        val plan = com.hyperlog.app.program.ActiveProgram(1, program, com.hyperlog.app.program.Position(1, 1), today = java.time.LocalDate.now())
        val frame = CheckInContext.frame(plan, emptyList(), library.exercises)
        val coach = CheckInCoach(ClaudeClient(key!!), CheckInWorld(program, ids))
        val checkIn = coach.start(Tile.Hurts, frame)
        val first = coach.step(checkIn)
        assertTrue(first.toString(), first is CheckInStep.Question)
        val next = coach.answer(checkIn, (first as CheckInStep.Question).answers.first())
        val step = coach.step(next)
        assertTrue(step.toString(), step !is CheckInStep.Failed && step !is CheckInStep.Problems)
    }

    @Test
    fun aRefusedKeySaysSo() = runTest(timeout = 1.minutes) {
        assumeTrue("ANTHROPIC_API_KEY isn't set", key != null)
        try {
            ClaudeClient("sk-ant-api03-not-a-real-key").checkKey()
            error("The made-up key was accepted")
        } catch (_: CoachError.KeyRefused) {
        }
    }
}
