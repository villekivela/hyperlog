package com.hyperlog.app

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIdentityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun debugBuildInstallsBesideTheReleaseBuild() {
        assertEquals("com.hyperlog.app.dev", context.packageName)
    }

    @Test
    fun launcherIconIsAdaptiveWithAThemedLayer() {
        val icon = context.getDrawable(context.applicationInfo.icon)!!
        assertTrue("launcher icon is ${icon.javaClass}", icon is AdaptiveIconDrawable)
        assertNotNull("themed icons need a monochrome layer", (icon as AdaptiveIconDrawable).monochrome)
    }

    @Test
    fun theDebugBuildWearsADevBadgeAndLabel() {
        assertEquals("Hyperlog DEV", context.getString(context.applicationInfo.labelRes))
        // Its own launcher icon: the logo on amber with a DEV badge, themed icon included.
        val badge = context.resources.getIdentifier("ic_launcher_dev_badge", "drawable", context.packageName)
        assertTrue("no DEV badge in the debug build", badge != 0)
        val background = context.resources.getIdentifier("ic_launcher_background_dev", "color", context.packageName)
        assertTrue("no debug icon background", background != 0)
        val icon = context.getDrawable(context.applicationInfo.icon) as AdaptiveIconDrawable
        assertEquals(context.getColor(background), (icon.background as android.graphics.drawable.ColorDrawable).color)
    }
}
