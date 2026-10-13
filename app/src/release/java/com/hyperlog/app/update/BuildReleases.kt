package com.hyperlog.app.update

import android.app.Activity

/** A release build checks GitHub. */
internal object BuildReleases {
    fun source(activity: Activity): ReleaseSource = GitHubReleases()
}
