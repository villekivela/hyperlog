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

    suspend fun setCheckForUpdates(on: Boolean) = db.settings().put(SettingEntity(CHECK_FOR_UPDATES, on.toString()))

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
 * The check when the app opens: the latest release if it's newer than the installed one
 * ([installed] is its versionCode) and not dismissed. GitHub is read at most once a day; a
 * failure or a read slower than [timeoutMs] is just no release, and the next start tries again.
 */
class UpdateCheck(
    private val source: ReleaseSource,
    private val preferences: UpdatePreferences,
    private val installed: Int,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 10_000,
) {
    suspend fun run(): Release? {
        if (!preferences.checkForUpdates()) return null
        val now = clock()
        val last = if (source is FakeRelease) null else preferences.lastCheck()
        val release = if (last != null && now - last.first in 0 until DAY) last.second else read()?.let { found ->
            preferences.rememberCheck(now, found.release)
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

    private companion object {
        const val DAY = 24 * 3_600_000L
    }
}
