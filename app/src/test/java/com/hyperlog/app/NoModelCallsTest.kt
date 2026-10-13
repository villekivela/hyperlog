package com.hyperlog.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The app makes no model calls (ADR 0001): no model SDK and no model host anywhere in its code or
 * build. The network is only for the update check, so only `update/` may open a connection.
 */
class NoModelCallsTest {
    private val sources = File("src/main/java").walk().filter { it.extension == "kt" }.toList()

    /** Model SDKs and hosts: Anthropic's API, OpenAI, Gemini, and the usual wrappers and local runners. */
    private val model = Regex(
        """com\.anthropic|api\.anthropic\.com|anthropic-sdk|openai|generativelanguage\.googleapis|aiplatform\.googleapis|""" +
            """com\.google\.ai\.client|firebase-ai|firebase\.vertexai|langchain|mistral\.ai|api\.cohere|huggingface|ollama|claude\.ai/api""",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun noSourceOrDependencyCallsAModel() {
        val build = listOf(File("build.gradle.kts"), File("../gradle/libs.versions.toml"), File("../build.gradle.kts"))
        val offenders = (sources + build).filter { it.exists() && model.containsMatchIn(it.readText()) }.map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun onlyTheUpdateCheckUsesTheNetwork() {
        // java.net.URI and the URL encoders only handle text.
        val network = Regex("""java\.net\.(?!URI\b|URLEncoder\b|URLDecoder\b)|javax\.net|okhttp|ktor|retrofit|volley|cronet""")
        val offenders = sources.filter { network.containsMatchIn(it.readText()) }
            .filterNot { it.invariantSeparatorsPath.contains("/com/hyperlog/app/update/") }.map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }
}
