package com.hyperlog.app.coach

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.BetaMessageAccumulator
import com.anthropic.models.ErrorType
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.BetaTool
import com.anthropic.models.beta.messages.BetaToolChoiceAuto
import com.anthropic.models.beta.messages.BetaToolResultBlockParam
import com.anthropic.models.beta.messages.BetaToolUseBlockParam
import com.anthropic.models.beta.messages.BetaUsage
import com.anthropic.models.beta.messages.MessageCreateParams
import com.fasterxml.jackson.databind.JsonNode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * The one client that calls Claude (ADR 0005; SPEC, Coaching → With a Claude key), through the
 * official Anthropic SDK. It reaches only `api.anthropic.com`; no other code in the app calls a
 * model, and only it and the update check open a connection. [baseUrl] is for tests' stand-in.
 */
class ClaudeClient(apiKey: String, baseUrl: String = HOST) : CoachModel {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .build()

    /** Checks the key with the models list, which costs nothing. Throws a [CoachError] if it fails. */
    suspend fun checkKey() = call("Key check") { client.models().list() }

    /**
     * Streams Claude's reply: Opus 5.5 with adaptive thinking at medium effort, the tools `strict`
     * with `tool_choice` auto, and the server-side fallback for a refusal. The context is cached,
     * and so is the conversation up to its last turn, so a check-in's later steps read both from
     * the cache.
     */
    override suspend fun reply(request: CoachRequest): CoachReply = call("Reply") {
        val accumulator = BetaMessageAccumulator.create()
        client.beta().messages().createStreaming(params(request)).use { stream ->
            // The read blocks; a check-in that is left closes the stream to end it.
            val closing = currentCoroutineContext()[Job]?.invokeOnCompletion { stream.close() }
            try {
                stream.stream().forEach { accumulator.accumulate(it) }
            } finally {
                closing?.dispose()
            }
        }
        val message = accumulator.message()
        val usage = usage(message)
        if (message.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
            throw CoachError.Refused(message.stopDetails().flatMap { it.category() }.map { it.asString() }.orElse(null), usage)
        }
        CoachReply(
            text = message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("\n\n"),
            toolCalls = message.content().mapNotNull { block ->
                block.toolUse().map { ToolCall(it.id(), it.name(), json(it._input()).jsonObject) }.orElse(null)
            },
            usage = usage,
            original = jsonMapper().writeValueAsString(message),
        )
    }

    private fun params(request: CoachRequest): MessageCreateParams {
        val cached = BetaCacheControlEphemeral.builder().build()
        val builder = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .thinking(BetaThinkingConfigAdaptive.builder().build())
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
            .systemOfBetaTextBlockParams(listOf(BetaTextBlockParam.builder().text(request.context).cacheControl(cached).build()))
            .cacheControl(cached)
            .fallbacksDefault()
            .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
        request.tools.forEach { builder.addTool(tool(it)) }
        if (request.tools.isNotEmpty()) builder.toolChoice(BetaToolChoiceAuto.builder().build())
        request.messages.forEach { message ->
            when (message) {
                is CoachMessage.User -> builder.addMessage(user(listOf(BetaContentBlockParam.ofText(message.text))))
                is CoachMessage.Results -> builder.addMessage(user(message.results.map { result ->
                    BetaContentBlockParam.ofToolResult(
                        BetaToolResultBlockParam.builder().toolUseId(result.callId).content(result.content).isError(result.isError).build(),
                    )
                }))
                // Claude's own reply goes back as it came, its thinking included, as the API requires.
                is CoachMessage.Assistant -> when (val original = message.reply.original) {
                    null -> builder.addMessage(assistant(message.reply))
                    else -> builder.addMessage(jsonMapper().readValue(original, BetaMessage::class.java))
                }
            }
        }
        return builder.build()
    }

    private fun user(content: List<BetaContentBlockParam>) =
        BetaMessageParam.builder().role(BetaMessageParam.Role.USER).contentOfBetaContentBlockParams(content).build()

    /** A reply this client didn't make (a scripted one), as plain text and tool calls. */
    private fun assistant(reply: CoachReply): BetaMessageParam {
        val text = listOfNotNull(reply.text.takeIf { it.isNotEmpty() }?.let { BetaContentBlockParam.ofText(it) })
        val calls = reply.toolCalls.map { call ->
            BetaContentBlockParam.ofToolUse(BetaToolUseBlockParam.builder().id(call.id).name(call.name).input(value(call.input)).build())
        }
        return BetaMessageParam.builder().role(BetaMessageParam.Role.ASSISTANT).contentOfBetaContentBlockParams(text + calls).build()
    }

    private fun tool(tool: CoachTool): BetaTool {
        val schema = BetaTool.InputSchema.builder()
        tool.inputSchema.forEach { (name, value) -> if (name != "type") schema.putAdditionalProperty(name, value(value)) }
        return BetaTool.builder().name(tool.name).description(tool.description).inputSchema(schema.build()).strict(true).build()
    }

    /** Each model's tokens: one for each that ran when a refusal fell back, else the reply's. */
    private fun usage(message: BetaMessage): List<CoachUsage> {
        val model = message.model().asString()
        val hops = message.usage().iterations().orElse(emptyList()).filter { it.isMessage() || it.isFallbackMessage() }
        if (hops.isEmpty()) return listOf(usage(model, message.usage()))
        return hops.map {
            CoachUsage(
                model = it.model().map { m -> m.asString() }.orElse(model),
                input = it.inputTokens(),
                cacheWrite = it.cacheCreationInputTokens(),
                cacheRead = it.cacheReadInputTokens(),
                output = it.outputTokens(),
            )
        }
    }

    private fun usage(model: String, usage: BetaUsage) = CoachUsage(
        model = model,
        input = usage.inputTokens(),
        cacheWrite = usage.cacheCreationInputTokens().orElse(0),
        cacheRead = usage.cacheReadInputTokens().orElse(0),
        output = usage.outputTokens(),
    )

    /**
     * Runs [block] off the main thread, with the SDK's errors as [CoachError]s. Each failure is
     * warned in Logcat under [TAG] with what the API said: the HTTP status, the error type and its
     * message. Never the key, nor the headers that carry it.
     */
    private suspend fun <T> call(what: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = when (e) {
                is CoachError -> e
                is UnauthorizedException, is PermissionDeniedException -> CoachError.KeyRefused(e)
                is AnthropicServiceException -> if (spendLimit(e)) CoachError.SpendLimit(e) else CoachError.NoConnection(e)
                // The connection, a timeout, or a reply the SDK couldn't read.
                is AnthropicException, is IOException -> CoachError.NoConnection(e)
                else -> throw e
            }
            Log.w(TAG, "$what failed: ${describe(error)}")
            throw error
        }
    }

    /** `KeyRefused, HTTP 401 authentication_error: invalid x-api-key`, from the error and its cause. */
    private fun describe(error: CoachError): String {
        val name = error.javaClass.simpleName
        if (error is CoachError.Refused) return "$name (${error.category ?: "no category"})"
        return when (val cause = error.cause) {
            is AnthropicServiceException -> {
                val body = runCatching { cause.body().convert(JsonNode::class.java)?.path("error") }.getOrNull()
                val type = body?.path("type")?.asText().orEmpty().ifEmpty { cause.errorType().map { it.toString() }.orElse("no type") }
                val message = body?.path("message")?.asText().orEmpty()
                "$name, HTTP ${cause.statusCode()} $type: $message"
            }
            null -> name
            else -> "$name, ${cause.javaClass.simpleName}: ${cause.message}"
        }
    }

    /**
     * A billing error, or the 400 the API answers once the Console's spend limit or the credit
     * balance runs out. That 400 is an `invalid_request_error` like any other, so only its
     * message tells it apart; if Anthropic rewords it, a spent key reads as no connection.
     */
    private fun spendLimit(e: AnthropicServiceException): Boolean {
        if (e.statusCode() == 402 || e.errorType().map { it.known() == ErrorType.Known.BILLING_ERROR }.orElse(false)) return true
        val message = e.body().toString().lowercase()
        return e.statusCode() == 400 && ("usage limits" in message || "credit balance" in message)
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
        const val HOST = "https://api.anthropic.com"

        /** Logcat's tag for the client's warnings. */
        const val TAG = "ClaudeClient"

        /** Room for thinking and a whole program; streaming keeps a long reply inside the timeout. */
        private const val MAX_TOKENS = 64_000L

        private fun json(value: JsonValue): JsonElement = Json.parseToJsonElement(value.convert(JsonNode::class.java).toString())

        private fun value(element: JsonElement): JsonValue = JsonValue.from(plain(element))

        private fun plain(element: JsonElement): Any? = when (element) {
            is JsonNull -> null
            is JsonObject -> element.mapValues { plain(it.value) }
            is JsonArray -> element.map(::plain)
            is JsonPrimitive -> when {
                element.isString -> element.content
                else -> element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull
            }
        }
    }
}
