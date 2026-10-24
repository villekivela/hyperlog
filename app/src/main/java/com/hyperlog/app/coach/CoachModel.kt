package com.hyperlog.app.coach

import kotlinx.serialization.json.JsonObject

/**
 * Claude, as a check-in talks to it (ADR 0005): the stable context, the tools and the turns so
 * far go in, Claude's next reply comes out. [ClaudeClient] is the one that calls the API; tests
 * play scripted turns through a fake.
 */
interface CoachModel {
    /** Claude's next reply. Throws a [CoachError] when there is none. */
    suspend fun reply(request: CoachRequest): CoachReply
}

/**
 * One call: [context] is the stable part (the formats, the library, the program), sent first and
 * cached; [tools] are the replies Claude may make, each `strict`; [messages] is the check-in so far.
 */
data class CoachRequest(
    val context: String,
    val tools: List<CoachTool>,
    val messages: List<CoachMessage>,
)

/** A tool Claude replies through. [inputSchema] is a JSON Schema object with `additionalProperties: false`. */
data class CoachTool(val name: String, val description: String, val inputSchema: JsonObject)

/** A turn of the check-in. */
sealed interface CoachMessage {
    /** Your words, or the app's on your behalf. */
    data class User(val text: String) : CoachMessage

    /** The answers to Claude's tool calls, one for each: an `ask`'s answer, or a reply's problems. */
    data class Results(val results: List<ToolResult>) : CoachMessage

    /** A reply Claude made, sent back as it came. */
    data class Assistant(val reply: CoachReply) : CoachMessage
}

data class ToolCall(val id: String, val name: String, val input: JsonObject)

/** The answer to the call [callId]; [isError] when it hands Claude a reply's problems. */
data class ToolResult(val callId: String, val content: String, val isError: Boolean = false)

/**
 * Claude's reply: its words, its tool calls, and what it cost. [original] is the client's own copy
 * of the reply (its thinking included), which goes back unchanged when the reply is in a later
 * request; a reply the client didn't make has none.
 */
data class CoachReply(
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: List<CoachUsage> = emptyList(),
    val original: Any? = null,
)

/**
 * Tokens one model read and wrote for a reply. A reply that fell back to another model after a
 * refusal has one for each model that ran.
 */
data class CoachUsage(
    val model: String,
    val input: Long = 0,
    val cacheWrite: Long = 0,
    val cacheRead: Long = 0,
    val output: Long = 0,
)

/** Why there is no reply (SPEC, Coaching → With a Claude key, Errors). */
sealed class CoachError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection, a timeout, or an error on Anthropic's side: try again. */
    class NoConnection(cause: Throwable? = null) : CoachError("Couldn't reach Claude", cause)

    /** The API doesn't accept the key. */
    class KeyRefused(cause: Throwable? = null) : CoachError("Claude doesn't accept this key", cause)

    /** The month's spend limit set in the Anthropic Console is reached. */
    class SpendLimit(cause: Throwable? = null) : CoachError("The spend limit is reached", cause)

    /** Claude, and every model it fell back to, declined. [usage] is still paid for. */
    class Refused(val category: String?, val usage: List<CoachUsage> = emptyList()) : CoachError("Claude declined")
}
