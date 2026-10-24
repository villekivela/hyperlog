package com.hyperlog.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * One host (ADR 0005; SPEC, Coaching → With a Claude key): only the client (`coach/ClaudeClient.kt`)
 * calls a model, and only at `api.anthropic.com`. Nothing else opens a connection but the update
 * check (`update/`).
 */
class OneHostTest {
    /** The app's code, the debug build's own (the scripted coach) and the release build's included. */
    private val sources = listOf("src/main/java", "src/debug/java", "src/release/java").flatMap { File(it).walk().filter { f -> f.extension == "kt" } }
    private val client = File("src/main/java/com/hyperlog/app/coach/ClaudeClient.kt")

    private fun File.isClient() = canonicalFile == client.canonicalFile
    private fun File.isUpdateCheck() = invariantSeparatorsPath.contains("/com/hyperlog/app/update/")

    /** Anthropic's SDK and API, which only the client may use. */
    private val anthropic = Regex("""com\.anthropic|api\.anthropic\.com""")

    /** Every other model SDK and host: OpenAI, Gemini, and the usual wrappers and local runners. */
    private val otherModels = Regex(
        """anthropic-sdk|openai|generativelanguage\.googleapis|aiplatform\.googleapis|com\.google\.ai\.client|firebase-ai|""" +
            """firebase\.vertexai|langchain|mistral\.ai|api\.cohere|huggingface|ollama|claude\.ai/api""",
        RegexOption.IGNORE_CASE,
    )

    /** What opens a connection. java.net.URI and the URL encoders only handle text. */
    private val network = Regex(
        """java\.net\.(?!URI\b|URLEncoder\b|URLDecoder\b)|javax\.net|android\.net\.http|HttpEngine|WebView|DownloadManager|""" +
            """okhttp|ktor|retrofit|volley|cronet""",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun onlyTheClientUsesAnthropicsSdk() {
        assertEquals(emptyList<String>(), sources.filter { !it.isClient() && anthropic.containsMatchIn(it.readText()) }.map { it.path })
    }

    @Test
    fun noSourceOrDependencyCallsAnyOtherModel() {
        val build = listOf(File("build.gradle.kts"), File("../gradle/libs.versions.toml"), File("../build.gradle.kts"))
        val offenders = (sources + build).filter { it.exists() && otherModels.containsMatchIn(it.readText()) }.map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun onlyTheClientAndTheUpdateCheckOpenAConnection() {
        val offenders = sources.filter { network.containsMatchIn(it.readText()) }.filterNot { it.isClient() || it.isUpdateCheck() }.map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun theClientCallsOnlyApiAnthropicCom() {
        val text = client.readText()
        assertEquals(listOf("https://api.anthropic.com"), Regex("""\bhttps?://[^\s"'`)]*""").findAll(text).map { it.value }.distinct().toList())
        // The SDK's environment settings could point it at another host.
        assertFalse(text.contains("fromEnv"))
    }
}
