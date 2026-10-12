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
}
