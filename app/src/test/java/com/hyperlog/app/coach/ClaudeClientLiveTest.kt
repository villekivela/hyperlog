package com.hyperlog.app.coach

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The one test that calls the real API (SPEC, Coaching → With a Claude key). It runs only with
 * `ANTHROPIC_API_KEY` set and is skipped otherwise, so no test or CI job needs a key. It costs a
 * cent or two.
 */
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
