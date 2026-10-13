package com.hyperlog.app.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/** A published release: its version and the APK to install. */
data class Release(val version: AppVersion, val apkUrl: String)

/** Where the latest release comes from: GitHub in a release build, a fake one in tests and the debug override. */
fun interface ReleaseSource {
    /** The latest release, or null if there is none with a version tag and an APK. Throws [IOException] when it can't tell. */
    suspend fun latest(): Release?
}

/** A fixed release for the debug override, read afresh on every start rather than once a day. */
class FakeRelease(private val release: Release) : ReleaseSource {
    override suspend fun latest() = release
}

/**
 * The latest release of the public releases repo, from GitHub's REST API with no token. The
 * request carries nothing about the user: a GET of a fixed URL with the app's name as User-Agent,
 * which GitHub requires.
 */
class GitHubReleases(
    private val api: String = "https://api.github.com",
    private val repo: String = "villekivela/hyperlog-releases",
    private val timeoutMs: Int = 5_000,
) : ReleaseSource {
    override suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val connection = URL("$api/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Hyperlog")
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parse(connection.inputStream.use { it.bufferedReader().readText() })
                // No release published yet.
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub answered $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private class Latest(@SerialName("tag_name") val tag: String, val assets: List<Asset> = emptyList())

    @Serializable
    private class Asset(val name: String, @SerialName("browser_download_url") val url: String)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): Release? {
            val latest = runCatching { json.decodeFromString<Latest>(body) }.getOrElse { throw IOException("Unreadable release", it) }
            val version = AppVersion.parse(latest.tag) ?: return null
            val apk = latest.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
            return Release(version, apk.url)
        }
    }
}

/** Downloads a release's APK. GitHub's asset links redirect to its file host, which HttpURLConnection follows. */
object ApkDownload {
    suspend fun fetch(url: String, into: File, timeoutMs: Int = 30_000) = withContext(Dispatchers.IO) {
        val partial = File(into.parentFile, into.name + ".part")
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", "Hyperlog")
            if (connection is HttpURLConnection && connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download answered ${connection.responseCode}")
            }
            connection.getInputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(into)) throw IOException("Couldn't save the download")
        } finally {
            partial.delete()
        }
    }
}
