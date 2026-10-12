package com.hyperlog.app.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hyperlog.app.library.TechniqueVideo

const val YouTubePackage = "com.google.android.youtube"

/** Opens a technique video in the YouTube app at its timestamp, or in the browser without the app. */
fun Context.openTechniqueVideo(link: String) {
    val uri = Uri.parse(TechniqueVideo.parse(link)?.watchUrl ?: link)
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(YouTubePackage))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
