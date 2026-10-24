package com.hyperlog.app.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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

/**
 * A tool Claude replies through. [inputSchema] is a JSON Schema object with `additionalProperties:
 * false`. A [strict] tool's input always fits its schema; the API compiles strict schemas into one
 * grammar per request, and refuses one too large, so only small tools are strict.
 */
@Serializable
data class CoachTool(val name: String, val description: String, val inputSchema: JsonObject, val strict: Boolean = true)

/** A turn of the check-in. */
sealed interface CoachMessage {
    /** Your words, or the app's on your behalf. */
    data class User(val text: String) : CoachMessage

    /** The answers to Claude's tool calls, one for each: an `ask`'s answer, or a reply's problems. */
    data class Results(val results: List<ToolResult>) : CoachMessage

    /** A reply Claude made, sent back as it came. */
    data class Assistant(val reply: CoachReply) : CoachMessage
}

@Serializable
data class ToolCall(val id: String, val name: String, val input: JsonObject)

/** The answer to the call [callId]; [isError] when it hands Claude a reply's problems, [attachment] when a file is the answer. */
@Serializable
data class ToolResult(val callId: String, val content: String, val isError: Boolean = false, val attachment: Attachment? = null)

/**
 * A PDF or a photo attached as an answer (SPEC, Coaching → New program), sent to Claude as a
 * document or an image. It is never stored: a check-in saves its [name] and [mediaType], and
 * [data] lives only in memory while the app runs.
 */
@Serializable
data class Attachment(val name: String, val mediaType: String, @Transient val data: ByteArray? = null) {
    val pdf: Boolean get() = mediaType == PDF

    // Equal by the bytes, not the array: a check-in holding a file compares by what it sends.
    override fun equals(other: Any?): Boolean =
        other is Attachment && name == other.name && mediaType == other.mediaType && data.contentEquals(other.data)

    override fun hashCode(): Int = (name.hashCode() * 31 + mediaType.hashCode()) * 31 + data.contentHashCode()

    companion object {
        const val PDF = "application/pdf"
        const val JPEG = "image/jpeg"
    }
}

/**
 * Claude's reply: its words, its tool calls, and what it cost. [original] is the client's own copy
 * of the reply as JSON (its thinking included), which goes back unchanged when the reply is in a
 * later request, a check-in saved and read back too; a reply the client didn't make has none.
 */
@Serializable
data class CoachReply(
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: List<CoachUsage> = emptyList(),
    val original: String? = null,
)

/**
 * Tokens one model read and wrote for a reply. A reply that fell back to another model after a
 * refusal has one for each model that ran.
 */
@Serializable
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

    /** The API turned the request down (a 4xx other than the key, the limit, a timeout or a rate limit): trying again won't help. */
    class Rejected(cause: Throwable? = null) : CoachError("Claude couldn't take this request", cause)

    /** The month's spend limit set in the Anthropic Console is reached. */
    class SpendLimit(cause: Throwable? = null) : CoachError("The spend limit is reached", cause)

    /** Claude, and every model it fell back to, declined. [usage] is still paid for. */
    class Refused(val category: String?, val usage: List<CoachUsage> = emptyList()) : CoachError("Claude declined")
}
