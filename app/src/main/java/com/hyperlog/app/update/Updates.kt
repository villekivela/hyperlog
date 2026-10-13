package com.hyperlog.app.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.BuildConfig
import com.hyperlog.app.data.HyperlogDatabase
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app gets its release source. */
object Updates {
    /** A fake GitHub for tests; null otherwise. */
    @VisibleForTesting
    @Volatile var forTests: ReleaseSource? = null

    /**
     * GitHub in a release build. A `.dev` build doesn't check: its source is the debug override
     * ([BuildReleases]), null unless the launch asked for a fake release.
     */
    fun source(activity: Activity): ReleaseSource? = forTests ?: BuildReleases.source(activity)
}

/** The update banner on Today. */
sealed interface UpdateBanner {
    val release: Release

    data class Available(override val release: Release) : UpdateBanner
    data class Downloading(override val release: Release) : UpdateBanner
    data class Failed(override val release: Release) : UpdateBanner
}

/** The update check on app start, the banner it leads to, and "Check for updates" in Settings. */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UpdatePreferences(HyperlogDatabase.get(application))
    private val _banner = MutableStateFlow<UpdateBanner?>(null)
    val banner: StateFlow<UpdateBanner?> = _banner.asStateFlow()
    val checkForUpdates: StateFlow<Boolean> = preferences.checkForUpdates.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private var started = false

    /** Checks once per process, off the main thread inside [UpdateCheck]; no source, no check. */
    fun start(source: ReleaseSource?) {
        if (started || source == null) return
        started = true
        viewModelScope.launch {
            val release = UpdateCheck(source, preferences, installed = BuildConfig.VERSION_CODE).run()
            if (_banner.value == null) _banner.value = release?.let(UpdateBanner::Available)
        }
    }

    fun dismiss() {
        val release = _banner.value?.release ?: return
        _banner.value = null
        viewModelScope.launch { preferences.dismiss(release.version) }
    }

    fun setCheckForUpdates(on: Boolean) {
        if (!on) _banner.value = null
        viewModelScope.launch { preferences.setCheckForUpdates(on) }
    }

    /**
     * Downloads the APK, then hands it to Android's installer, which asks before it updates the app.
     * [context] is the screen's: its application holds the download and starts the installer.
     */
    fun install(context: Context) {
        val current = _banner.value ?: return
        if (current is UpdateBanner.Downloading) return
        val release = current.release
        _banner.value = UpdateBanner.Downloading(release)
        val app = context.applicationContext
        viewModelScope.launch {
            val apk = try {
                download(app, release)
            } catch (_: IOException) {
                null
            }
            if (apk == null) {
                _banner.value = UpdateBanner.Failed(release)
                return@launch
            }
            app.startActivity(installIntent(app, apk))
            _banner.value = UpdateBanner.Available(release)
        }
    }

    private suspend fun download(context: Context, release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Only the latest download is kept.
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "hyperlog-${release.version.name}.apk").also { ApkDownload.fetch(release.apkUrl, it) }
    }

    private fun installIntent(context: Context, apk: File): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
