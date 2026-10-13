package com.hyperlog.app.ui.library

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A technique video opens where Back returns to the session: a Custom Tab in Hyperlog's own task.
 * The YouTube app is only the fallback, because Back from it goes home and it keeps playing in a
 * floating window over the pad.
 */
@RunWith(RobolectricTestRunner::class)
class OpenVideoTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val activity by lazy { Robolectric.buildActivity(ComponentActivity::class.java).setup().get() }
    private val link = "https://youtu.be/SJqInYJcg5k?t=653"

    private fun installBrowser(pkg: String) {
        val pm = shadowOf(app.packageManager)
        val activity = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(activity)
        pm.addIntentFilterForActivity(
            activity,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
        val service = ComponentName(pkg, "$pkg.CustomTabs")
        pm.addServiceIfNotPresent(service)
        pm.addIntentFilterForService(service, IntentFilter("android.support.customtabs.action.CustomTabsService"))
    }

    @Test
    fun theVideoOpensInACustomTabAtItsTimestamp() {
        installBrowser("com.android.chrome")
        activity.openTechniqueVideo(link)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("com.android.chrome", started.`package`)
        assertEquals(Uri.parse("https://www.youtube.com/watch?v=SJqInYJcg5k&t=653s"), started.data)
        // The session extra makes it a Custom Tab, which stays in Hyperlog's task.
        assertTrue(started.extras!!.containsKey("android.support.customtabs.extra.SESSION"))
    }

    @Test
    fun withNoCustomTabsBrowserItFallsBackToTheYouTubeApp() {
        activity.openTechniqueVideo(link)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(YouTubePackage, started.`package`)
        assertEquals("https://www.youtube.com/watch?v=SJqInYJcg5k&t=653s", started.dataString)
    }
}
