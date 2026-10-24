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
    class Answer(val status: Int, val body: String, val contentType: String = "application/json")

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
            val bytes = answer.body.toByteArray()
            exchange.responseHeaders.add("Content-Type", answer.contentType)
            // The SDK retries 5xx and 429; this keeps its wait short.
            exchange.responseHeaders.add("retry-after-ms", "1")
            exchange.sendResponseHeaders(answer.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val url = "http://127.0.0.1:${server.address.port}"

    fun answer(status: Int, body: String) = answers.add(Answer(status, body))

    fun error(status: Int, type: String, message: String = type) =
        answer(status, """{"type":"error","error":{"type":"$type","message":"$message"}}""")

    /** A streamed message, from its events. */
    fun stream(vararg events: String) = answers.add(
        Answer(200, events.joinToString("") { event ->
            val type = Json.parseToJsonElement(event).jsonObject["type"].toString().trim('"')
            "event: $type\ndata: $event\n\n"
        }, "text/event-stream"),
    )

    override fun close() = server.stop(0)

    companion object {
        const val MODELS = """{"data":[{"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-09-01T00:00:00Z"}],"has_more":false,"first_id":"claude-opus-5-5","last_id":"claude-opus-5-5"}"""
    }
}
