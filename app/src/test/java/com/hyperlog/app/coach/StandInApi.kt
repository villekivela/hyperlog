package com.hyperlog.app.coach

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A stand-in for `api.anthropic.com` on the loopback address, for [ClaudeClient] tests: it answers
 * each request with the next queued answer and keeps what it was sent. No key and no network.
 */
class StandInApi : AutoCloseable {
    /**
     * An answer: a body sent at once, or with [events] a stream sent event by event, [gapMs] apart,
     * that stops sending for [stallMs] after [stallAfter] events.
     */
    class Answer(
        val status: Int,
        val body: String,
        val contentType: String = "application/json",
        val events: List<String>? = null,
        val gapMs: Long = 0,
        val stallAfter: Int? = null,
        val stallMs: Long = 0,
    )

    class Received(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
        val json: JsonObject get() = Json.parseToJsonElement(body).jsonObject
    }

    private val answers = ConcurrentLinkedQueue<Answer>()
    val received = ConcurrentLinkedQueue<Received>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
            received += Received(exchange.requestMethod, exchange.requestURI.path, headers, exchange.requestBody.readBytes().decodeToString())
            val answer = answers.poll() ?: Answer(500, """{"type":"error","error":{"type":"api_error","message":"No answer queued"}}""")
            exchange.responseHeaders.add("Content-Type", answer.contentType)
            // The SDK retries 5xx and 429; this keeps its wait short.
            exchange.responseHeaders.add("retry-after-ms", "1")
            val events = answer.events
            if (events == null) {
                val bytes = answer.body.toByteArray()
                exchange.sendResponseHeaders(answer.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else {
                // Chunked, flushed event by event, as a slow or stalled reply comes.
                exchange.sendResponseHeaders(answer.status, 0)
                runCatching {
                    exchange.responseBody.use { out ->
                        events.forEachIndexed { i, event ->
                            if (i == answer.stallAfter) Thread.sleep(answer.stallMs)
                            out.write(sse(event).toByteArray())
                            out.flush()
                            Thread.sleep(answer.gapMs)
                        }
                    }
                }
            }
        }
        // A stalled answer mustn't hold up the next request.
        executor = java.util.concurrent.Executors.newCachedThreadPool()
        start()
    }

    val url = "http://127.0.0.1:${server.address.port}"

    fun answer(status: Int, body: String) = answers.add(Answer(status, body))

    fun error(status: Int, type: String, message: String = type) =
        answer(status, """{"type":"error","error":{"type":"$type","message":"$message"}}""")

    /** A streamed message, from its events. */
    fun stream(vararg events: String) = answers.add(Answer(200, events.joinToString("") { sse(it) }, "text/event-stream"))

    /** A streamed message sent event by event, [gapMs] apart, stopping for [stallMs] after [stallAfter] events. */
    fun slowStream(vararg events: String, gapMs: Long = 0, stallAfter: Int? = null, stallMs: Long = 0) =
        answers.add(Answer(200, "", "text/event-stream", events.toList(), gapMs, stallAfter, stallMs))

    private fun sse(event: String): String {
        val type = Json.parseToJsonElement(event).jsonObject["type"].toString().trim('"')
        return "event: $type\ndata: $event\n\n"
    }

    override fun close() = server.stop(0)

    companion object {
        const val MODELS = """{"data":[{"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-09-01T00:00:00Z"}],"has_more":false,"first_id":"claude-opus-5-5","last_id":"claude-opus-5-5"}"""
    }
}
