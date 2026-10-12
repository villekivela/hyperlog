package com.hyperlog.app.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.hyperlog.app.library.TechniqueVideo

const val YouTubePackage = "com.google.android.youtube"

private const val CustomTabsService = "android.support.customtabs.action.CustomTabsService"
private const val CustomTabsSession = "android.support.customtabs.extra.SESSION"

/**
 * Opens a technique video at its timestamp in a Custom Tab, which runs in Hyperlog's task, so
 * Back comes straight back to the session. The YouTube app is the fallback without a Custom Tabs
 * browser: Back from it goes home, and it keeps playing in a floating window over the pad.
 */
fun Context.openTechniqueVideo(link: String) {
    val uri = Uri.parse(TechniqueVideo.parse(link)?.watchUrl ?: link)
    customTabsBrowser()?.let { browser ->
        val tab = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(browser)
            // A session extra, even an empty one, is what makes the browser open a Custom Tab.
            .putExtras(Bundle().apply { putBinder(CustomTabsSession, null) })
        if (runCatching { startActivity(tab) }.isSuccess) return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(YouTubePackage))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

/** The default browser if it shows Custom Tabs, else any browser that does; null with none. */
private fun Context.customTabsBrowser(): String? {
    val default = packageManager
        .resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
    val tabs = packageManager.queryIntentServices(Intent(CustomTabsService), 0).map { it.serviceInfo.packageName }
    return default?.takeIf { it in tabs } ?: tabs.firstOrNull()
}
