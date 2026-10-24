package com.hyperlog.app.coach

import java.net.ServerSocket
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The one client (SPEC, Coaching → With a Claude key), against [StandInApi]: the key check, the
 * request it makes, the reply it reads, and the typed errors.
 */
class ClaudeClientTest {
    private val api = StandInApi()
    private val key = "sk-ant-api03-test-a1b2"
    private val client = ClaudeClient(key, baseUrl = api.url)

    @After
    fun close() = api.close()

    private inline fun <reified E : CoachError> fails(block: () -> Unit): E {
        try {
            block()
        } catch (e: CoachError) {
            if (e is E) return e
            fail("Expected ${E::class.simpleName}, got $e")
        }
        fail("Expected ${E::class.simpleName}, got none")
        error("unreachable")
    }

    @Test
    fun theKeyIsCheckedWithTheModelsList() = runTest {
        api.answer(200, StandInApi.MODELS)
        client.checkKey()
        val sent = api.received.single()
        assertEquals("GET", sent.method)
        assertEquals("/v1/models", sent.path)
        assertEquals(key, sent.headers["x-api-key"])
    }

    @Test
    fun aKeyTheApiRefusesSaysSo() = runTest {
        api.error(401, "authentication_error", "invalid x-api-key")
        fails<CoachError.KeyRefused> { client.checkKey() }
    }

    @Test
    fun noConnection() = runTest {
        val closed = ServerSocket(0).use { it.localPort }
        fails<CoachError.NoConnection> { ClaudeClient(key, baseUrl = "http://127.0.0.1:$closed").checkKey() }
        fails<CoachError.NoConnection> { ClaudeClient(key, baseUrl = "http://127.0.0.1:$closed").reply(request) }
    }

    private val ask = CoachTool(
        name = "ask",
        description = "Ask one question with 2-4 answers to tap.",
        inputSchema = Json.parseToJsonElement(
            """{"type":"object","properties":{"question":{"type":"string"}},"required":["question"],"additionalProperties":false}""",
        ).jsonObject,
    )

    private val request = CoachRequest(
        context = "The program: Full body, 3 days.",
        tools = listOf(ask),
        messages = listOf(CoachMessage.User("Something hurts: my right hand")),
    )

    /** A question through `ask`, after thinking, from the cache's first write. */
    private fun question() = api.stream(
        """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":2000,"cache_creation_input_tokens":30000,"cache_read_input_tokens":0,"output_tokens":1}}}""",
        """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
        """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig-1"}}""",
        """{"type":"content_block_stop","index":0}""",
        """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
        """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"One question first."}}""",
        """{"type":"content_block_stop","index":1}""",
        """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"ask","input":{}}}""",
        """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"question\":\"Which hand?\"}"}}""",
        """{"type":"content_block_stop","index":2}""",
        """{"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":1500}}""",
        """{"type":"message_stop"}""",
    )

    @Test
    fun aReplyStreamsFromOpusWithTheContextCachedAndTheFallbackOn() = runTest {
        question()
        val reply = client.reply(request)
        assertEquals("One question first.", reply.text)
        assertEquals(listOf(ToolCall("toolu_1", "ask", buildJsonObject { put("question", "Which hand?") })), reply.toolCalls)
        assertEquals(listOf(CoachUsage("claude-opus-5-5", input = 2_000, cacheWrite = 30_000, cacheRead = 0, output = 1_500)), reply.usage)

        val sent = api.received.single()
        assertEquals("/v1/messages", sent.path)
        assertEquals(key, sent.headers["x-api-key"])
        assertTrue(sent.headers["anthropic-beta"]!!.split(",").contains("server-side-fallback-2026-07-01"))
        val body = sent.json
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals(true, body["stream"]!!.jsonPrimitive.boolean)
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("auto", body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val system = body["system"]!!.jsonArray.single().jsonObject
        assertEquals("The program: Full body, 3 days.", system["text"]!!.jsonPrimitive.content)
        assertEquals("ephemeral", system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val tool = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("ask", tool["name"]!!.jsonPrimitive.content)
        assertEquals(true, tool["strict"]!!.jsonPrimitive.boolean)
        assertEquals(ask.inputSchema, tool["input_schema"])
        val user = body["messages"]!!.jsonArray.single().jsonObject
        assertEquals("user", user["role"]!!.jsonPrimitive.content)
        assertEquals("Something hurts: my right hand", user["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun theReplyGoesBackWithItsThinkingAndTheAnswerAsItsToolResult() = runTest {
        question()
        val reply = client.reply(request)
        api.stream(
            """{"type":"message_start","message":{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":300,"cache_creation_input_tokens":0,"cache_read_input_tokens":30000,"output_tokens":1}}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Use straps and train as planned."}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":400}}""",
            """{"type":"message_stop"}""",
        )
        val next = client.reply(
            request.copy(
                messages = request.messages + CoachMessage.Assistant(reply) + CoachMessage.Results(listOf(ToolResult("toolu_1", "The right"))),
            ),
        )
        assertEquals("Use straps and train as planned.", next.text)
        assertEquals(listOf(CoachUsage("claude-opus-5-5", input = 300, cacheRead = 30_000, output = 400)), next.usage)

        val messages = api.received.last().json["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        val thinking = messages[1]["content"]!!.jsonArray[0].jsonObject
        assertEquals("thinking", thinking["type"]!!.jsonPrimitive.content)
        assertEquals("sig-1", thinking["signature"]!!.jsonPrimitive.content)
        val result = messages[2]["content"]!!.jsonArray.single().jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("toolu_1", result["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun aRefusalIsTypedAndCarriesItsUsage() = runTest {
        api.stream(
            """{"type":"message_start","message":{"id":"msg_3","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":2000,"output_tokens":0}}}""",
            """{"type":"message_delta","delta":{"stop_reason":"refusal","stop_sequence":null,"stop_details":{"type":"refusal","category":"cyber","explanation":"Declined."}},"usage":{"output_tokens":0}}""",
            """{"type":"message_stop"}""",
        )
        val refused = fails<CoachError.Refused> { client.reply(request) }
        assertEquals("cyber", refused.category)
        assertEquals(listOf(CoachUsage("claude-opus-5-5", input = 2_000)), refused.usage)
    }

    @Test
    fun aSpendLimitIsTypedAndOtherErrorsAreNoConnection() = runTest {
        api.error(400, "invalid_request_error", "You have reached your specified API usage limits. You will regain access on 2026-11-01 at 00:00 UTC.")
        fails<CoachError.SpendLimit> { client.reply(request) }
        api.error(400, "invalid_request_error", "Your credit balance is too low to access the Anthropic API.")
        fails<CoachError.SpendLimit> { client.reply(request) }
        api.error(401, "authentication_error")
        fails<CoachError.KeyRefused> { client.reply(request) }
        api.error(400, "invalid_request_error", "messages: roles must alternate")
        fails<CoachError.NoConnection> { client.reply(request) }
    }
}
