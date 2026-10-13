package com.hyperlog.app.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The latest release as GitHub's API serves it, read over plain HttpURLConnection from a local server. */
class GitHubReleasesTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val requests = mutableListOf<HttpExchange>()
    private val hang = CountDownLatch(1)

    @After
    fun stop() {
        hang.countDown()
        server.stop(0)
    }

    private fun serve(path: String, status: Int, body: ByteArray) = server.createContext(path) { exchange ->
        synchronized(requests) { requests += exchange }
        exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) exchange.responseBody.use { it.write(body) } else exchange.close()
    }

    private fun latest(status: Int, body: String) = serve("/repos/villekivela/hyperlog-releases/releases/latest", status, body.toByteArray())

    private val release = """
        { "tag_name": "v0.2.0", "name": "Hyperlog 0.2.0", "html_url": "https://github.com/villekivela/hyperlog-releases/releases/tag/v0.2.0",
          "assets": [
            { "name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt" },
            { "name": "hyperlog-0.2.0.apk", "browser_download_url": "https://example.com/hyperlog-0.2.0.apk" } ] }
    """

    @Test
    fun theLatestReleaseIsItsTagAndApk() = runTest {
        latest(200, release)
        val found = GitHubReleases(api = base).latest()
        assertEquals(Release(AppVersion(0, 2, 0), "https://example.com/hyperlog-0.2.0.apk"), found)
        val request = requests.single()
        assertEquals("GET", request.requestMethod)
        assertNull("sends nothing about the user", request.requestURI.query)
        assertEquals("application/vnd.github+json", request.requestHeaders.getFirst("Accept"))
        assertEquals("Hyperlog", request.requestHeaders.getFirst("User-Agent"))
    }

    @Test
    fun noReleaseYetIsNoRelease() = runTest {
        latest(404, """{ "message": "Not Found" }""")
        assertNull(GitHubReleases(api = base).latest())
    }

    @Test
    fun aReleaseWithoutAnApkOrAVersionTagIsNoRelease() = runTest {
        latest(200, """{ "tag_name": "v0.2.0", "assets": [ { "name": "notes.txt", "browser_download_url": "https://example.com/notes.txt" } ] }""")
        assertNull(GitHubReleases(api = base).latest())
        stop()
        val other = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
        try {
            other.createContext("/repos/villekivela/hyperlog-releases/releases/latest") { e ->
                val body = release.replace("v0.2.0\"", "nightly\"").toByteArray()
                e.sendResponseHeaders(200, body.size.toLong()); e.responseBody.use { it.write(body) }
            }
            assertNull(GitHubReleases(api = "http://127.0.0.1:${other.address.port}").latest())
        } finally {
            other.stop(0)
        }
    }

    @Test
    fun aServerErrorIsAFailure() = runTest {
        latest(500, "")
        try {
            GitHubReleases(api = base).latest()
            fail("expected a failure")
        } catch (_: IOException) {
        }
    }

    @Test
    fun aServerThatNeverAnswersTimesOut() = runTest {
        server.createContext("/repos/villekivela/hyperlog-releases/releases/latest") { hang.await() }
        val started = System.nanoTime()
        try {
            GitHubReleases(api = base, timeoutMs = 300).latest()
            fail("expected a timeout")
        } catch (_: IOException) {
        }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    @Test
    fun theApkDownloadsToTheFile() = runTest {
        val apk = ByteArray(200_000) { (it % 251).toByte() }
        serve("/hyperlog-0.2.0.apk", 200, apk)
        val file = File.createTempFile("update", ".apk").also { it.delete() }
        ApkDownload.fetch("$base/hyperlog-0.2.0.apk", file)
        assertArrayEquals(apk, file.readBytes())
        file.delete()
    }

    @Test
    fun aFailedDownloadLeavesNoFile() = runTest {
        serve("/missing.apk", 404, ByteArray(0))
        val file = File.createTempFile("update", ".apk").also { it.delete() }
        try {
            ApkDownload.fetch("$base/missing.apk", file)
            fail("expected a failure")
        } catch (_: IOException) {
        }
        assertFalse(file.exists())
    }
}
