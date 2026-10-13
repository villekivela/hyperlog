package com.hyperlog.app.update

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.data.UpdateCheckEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The update preferences. "Check for updates" and the dismissed version are the user's, so they
 * go into backups (table `setting`). The last check is only a cache of GitHub's answer, which
 * backups leave out (table `update_check`).
 */
class UpdatePreferences(private val db: HyperlogDatabase) {
    /** "Check for updates", on by default. */
    val checkForUpdates: Flow<Boolean> = db.settings().observe(CHECK_FOR_UPDATES).map { it != "false" }

    suspend fun checkForUpdates(): Boolean = db.settings().get(CHECK_FOR_UPDATES) != "false"

    /** Turning checks back on forgets the last answer, so the next check reads GitHub. */
    suspend fun setCheckForUpdates(on: Boolean) {
        db.settings().put(SettingEntity(CHECK_FOR_UPDATES, on.toString()))
        if (on) forgetLastCheck()
    }

    /** The version whose banner was dismissed. */
    suspend fun dismissed(): String? = db.settings().get(DISMISSED_UPDATE)

    suspend fun dismiss(version: AppVersion) = db.settings().put(SettingEntity(DISMISSED_UPDATE, version.name))

    /** When GitHub was last read (epoch ms) and the release it named, null for none. */
    internal suspend fun lastCheck(): Pair<Long, Release?>? = db.settings().lastCheck()?.let { check ->
        val version = check.version?.let(AppVersion::parse)
        check.checkedAt to if (version != null && check.apkUrl != null) Release(version, check.apkUrl) else null
    }

    internal suspend fun rememberCheck(at: Long, release: Release?) =
        db.settings().putLastCheck(UpdateCheckEntity(checkedAt = at, version = release?.version?.name, apkUrl = release?.apkUrl))

    /** Forgets the last check, so the next start reads the source again. */
    internal suspend fun forgetLastCheck() = db.settings().forgetLastCheck()

    private companion object {
        const val CHECK_FOR_UPDATES = "check_for_updates"
        const val DISMISSED_UPDATE = "dismissed_update"
    }
}

/**
 * The check each time the app comes to the foreground: the latest release if it's newer than the
 * installed one ([installed] is its versionCode) and not dismissed. GitHub is read at most once in
 * [THROTTLE_MS], unless checked now; a failure or a read slower than [timeoutMs] is just no
 * release, and the next check tries again.
 */
class UpdateCheck(
    private val source: ReleaseSource,
    private val preferences: UpdatePreferences,
    private val installed: Int,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 10_000,
) {
    /** Checks, from the cache when it is fresh; [now] ("Check for updates now") always reads GitHub. */
    suspend fun run(now: Boolean = false): Release? {
        if (!preferences.checkForUpdates()) return null
        val at = clock()
        val last = if (source is FakeRelease || now) null else preferences.lastCheck()
        val release = if (last != null && at - last.first in 0 until THROTTLE_MS) last.second else read()?.let { found ->
            preferences.rememberCheck(at, found.release)
            found.release
        }
        return release?.takeIf { it.version.code > installed && it.version.name != preferences.dismissed() }
    }

    private class Found(val release: Release?)

    private suspend fun read(): Found? = try {
        withTimeoutOrNull(timeoutMs) { Found(source.latest()) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        /** How long a check's answer stands: coming back to the app within it doesn't ask GitHub again. */
        const val THROTTLE_MS = 15 * 60_000L
    }
}
