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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * The one client (SPEC, Coaching → With a Claude key), against [StandInApi]: the key check, the
 * request it makes, the reply it reads, and the typed errors. On Robolectric, so the SDK runs with
 * Android's classes and the warnings land in Logcat's stand-in.
 */
@RunWith(RobolectricTestRunner::class)
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
        assertEquals("2023-06-01", sent.headers["anthropic-version"])
        // A bearer token beside the key makes the API refuse both.
        assertNull(sent.headers["authorization"])
    }

    /** What the client warned in Logcat. */
    private fun warnings() = ShadowLog.getLogsForTag(ClaudeClient.TAG).map { it.msg }

    @Test
    fun aKeyTheApiRefusesSaysSoAndLogsWhyWithoutTheKey() = runTest {
        api.error(401, "authentication_error", "invalid x-api-key")
        fails<CoachError.KeyRefused> { client.checkKey() }
        val warning = warnings().single()
        assertTrue(warning, warning.contains("KeyRefused"))
        assertTrue(warning, warning.contains("HTTP 401"))
        assertTrue(warning, warning.contains("authentication_error"))
        assertTrue(warning, warning.contains("invalid x-api-key"))
        assertFalse(warning, warning.contains(key))
        assertFalse(warning, warning.contains("a1b2"))
    }

    @Test
    fun aForbiddenKeyIsRefusedAndLogged() = runTest {
        api.error(403, "permission_error", "Your API key does not have permission to use the specified resource.")
        fails<CoachError.KeyRefused> { client.checkKey() }
        val warning = warnings().single()
        assertTrue(warning, warning.contains("HTTP 403 permission_error: Your API key does not have permission"))
    }

    @Test
    fun everyErrorIsLogged() = runTest {
        api.error(400, "invalid_request_error", "Your credit balance is too low to access the Anthropic API.")
        fails<CoachError.SpendLimit> { client.reply(request) }
        api.error(400, "invalid_request_error", "messages: roles must alternate")
        fails<CoachError.Rejected> { client.reply(request) }
        val closed = ServerSocket(0).use { it.localPort }
        fails<CoachError.NoConnection> { ClaudeClient(key, baseUrl = "http://127.0.0.1:$closed").checkKey() }
        val warnings = warnings()
        assertEquals(warnings.toString(), 3, warnings.size)
        assertTrue(warnings[0], warnings[0].contains("SpendLimit") && warnings[0].contains("credit balance is too low"))
        assertTrue(warnings[1], warnings[1].contains("Rejected") && warnings[1].contains("roles must alternate"))
        assertTrue(warnings[2], warnings[2].contains("NoConnection"))
        warnings.forEach { assertFalse(it, it.contains(key)) }
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
    fun anAttachedPdfGoesAsADocumentAPhotoAsAnImageAndASpreadsheetAsTextInTheAnswer() = runTest {
        question()
        val reply = client.reply(request)
        val pdf = Attachment("Nippard PPL.pdf", Attachment.PDF, byteArrayOf(1, 2, 3))
        val photo = Attachment("Whiteboard.jpg", "image/jpeg", byteArrayOf(4, 5))
        val sheet = Attachment("Essentials.xlsx", Attachment.TEXT, "## Sheet: Week 1\nSquat,4-6".toByteArray())
        listOf(pdf, photo, sheet).forEach { file ->
            question()
            client.reply(request.copy(messages = request.messages + CoachMessage.Assistant(reply) + CoachMessage.Results(listOf(ToolResult("toolu_1", "I attached ${file.name}.", attachment = file)))))
        }
        val (document, image, text) = api.received.toList().takeLast(3).map {
            it.json["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray.map { b -> b.jsonObject }
        }
        assertEquals("I attached Nippard PPL.pdf.", document[0]["text"]!!.jsonPrimitive.content)
        assertEquals("document", document[1]["type"]!!.jsonPrimitive.content)
        assertEquals("Nippard PPL.pdf", document[1]["title"]!!.jsonPrimitive.content)
        val pdfSource = document[1]["source"]!!.jsonObject
        assertEquals(listOf("base64", "application/pdf", "AQID"), listOf("type", "media_type", "data").map { pdfSource[it]!!.jsonPrimitive.content })
        assertEquals("image", image[1]["type"]!!.jsonPrimitive.content)
        val imageSource = image[1]["source"]!!.jsonObject
        assertEquals(listOf("base64", "image/jpeg", "BAU="), listOf("type", "media_type", "data").map { imageSource[it]!!.jsonPrimitive.content })
        // A spreadsheet's sheets go as a plain-text document, under the file's name.
        assertEquals("document", text[1]["type"]!!.jsonPrimitive.content)
        assertEquals("Essentials.xlsx", text[1]["title"]!!.jsonPrimitive.content)
        val textSource = text[1]["source"]!!.jsonObject
        assertEquals(listOf("text", "text/plain", "## Sheet: Week 1\nSquat,4-6"), listOf("type", "media_type", "data").map { textSource[it]!!.jsonPrimitive.content })
    }

    /** [question]'s events: thinking, then words, then the ask. */
    private val questionEvents = listOf(
        """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":2000,"output_tokens":1}}}""",
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

    /** Packets at most a second apart, so a test can stall past it. */
    private val quick = com.anthropic.core.Timeout.builder()
        .connect(java.time.Duration.ofSeconds(1)).read(java.time.Duration.ofSeconds(1)).write(java.time.Duration.ofSeconds(1))
        .request(java.time.Duration.ofSeconds(20)).build()

    @Test
    fun aSlowReplyIsReadToItsEndAndSaysWhatClaudeIsDoing() = runTest {
        // Three seconds in all, under a second between events.
        api.slowStream(*questionEvents.toTypedArray(), gapMs = 250)
        val progress = mutableListOf<CoachProgress>()
        val reply = ClaudeClient(key, baseUrl = api.url, timeout = quick).reply(request) { progress += it }
        assertEquals("One question first.", reply.text)
        assertEquals(listOf(CoachProgress.Thinking, CoachProgress.Answering, CoachProgress.Writing(CoachTools.ASK)), progress)
    }

    @Test
    fun aReplyThatStopsMidStreamIsDroppedAndItsWholeCauseIsLogged() = runTest {
        api.slowStream(*questionEvents.toTypedArray(), stallAfter = 2, stallMs = 3_000)
        fails<CoachError.Dropped> { ClaudeClient(key, baseUrl = api.url, timeout = quick).reply(request) }
        val warning = warnings().single()
        // The error, then each cause by class and message.
        assertTrue(warning, warning.startsWith("Reply failed: Dropped"))
        assertTrue(warning, warning.contains("SocketTimeoutException"))
        assertFalse(warning, warning.contains(key))
    }

    @Test
    fun theTimeoutsFitALongProgram() {
        // Packets come every few seconds while Claude thinks (the stream's pings); a program reply
        // of 64k tokens takes many minutes in all.
        assertEquals(java.time.Duration.ofSeconds(30), ClaudeClient.TIMEOUT.connect())
        assertEquals(java.time.Duration.ofMinutes(5), ClaudeClient.TIMEOUT.read())
        assertEquals(java.time.Duration.ofMinutes(45), ClaudeClient.TIMEOUT.request())
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
        assertTrue(warnings().single().contains("Refused (cyber)"))
        assertEquals(listOf(CoachUsage("claude-opus-5-5", input = 2_000)), refused.usage)
    }

    @Test
    fun aSpendLimitIsTypedAndOtherRequestErrorsAreRejected() = runTest {
        api.error(400, "invalid_request_error", "You have reached your specified API usage limits. You will regain access on 2026-11-01 at 00:00 UTC.")
        fails<CoachError.SpendLimit> { client.reply(request) }
        api.error(400, "invalid_request_error", "Your credit balance is too low to access the Anthropic API.")
        fails<CoachError.SpendLimit> { client.reply(request) }
        api.error(401, "authentication_error")
        fails<CoachError.KeyRefused> { client.reply(request) }
        api.error(400, "invalid_request_error", "messages: roles must alternate")
        fails<CoachError.Rejected> { client.reply(request) }
        // What the owner's phone met: a request the API turns down is never "Couldn't reach Claude".
        api.error(400, "invalid_request_error", "The compiled grammar is too large, which would cause performance issues. Simplify your tool schemas or reduce the number of strict tools.")
        fails<CoachError.Rejected> { client.reply(request) }
        assertTrue(warnings().last(), warnings().last().contains("Rejected, HTTP 400 invalid_request_error: The compiled grammar is too large"))
        api.error(404, "not_found_error", "model: claude-opus-5-5")
        fails<CoachError.Rejected> { client.reply(request) }
    }

    @Test
    fun aToolThatIsntStrictGoesWithoutStrict() = runTest {
        question()
        client.reply(request.copy(tools = listOf(ask, ask.copy(name = "propose_workout", strict = false))))
        val tools = api.received.single().json["tools"]!!.jsonArray.map { it.jsonObject }
        assertEquals(true, tools[0]["strict"]!!.jsonPrimitive.boolean)
        assertTrue(tools[1].toString(), tools[1]["strict"]?.jsonPrimitive?.boolean != true)
    }
}
