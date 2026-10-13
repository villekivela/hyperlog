package com.hyperlog.app.update

import android.app.Activity
import java.io.File

/**
 * A `.dev` build never checks GitHub. To see the banner and the installer on a phone, launch it
 * with a fake release, whose APK is this build's own:
 *
 * `adb shell am start -n com.hyperlog.app.dev/com.hyperlog.app.MainActivity --es fake_release 9.9.9`
 *
 * A dismissed fake version stays hidden; launch with another.
 */
internal object BuildReleases {
    private const val FAKE_RELEASE = "fake_release"

    fun source(activity: Activity): ReleaseSource? {
        val version = activity.intent?.getStringExtra(FAKE_RELEASE)?.let(AppVersion::parse) ?: return null
        return FakeRelease(Release(version, File(activity.applicationInfo.sourceDir).toURI().toString()))
    }
}
