package com.hyperlog.app.update

import com.hyperlog.app.openTab
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.click
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A newer release shows on Today; tapping it hands the downloaded APK to Android's installer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class UpdateBannerTest {
    // Read when used: each test gets its own application, set up after the test class is made.
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var apk: File
    private var published: Release? = null

    /** The fake GitHub, set before the activity starts. */
    private val releases = object : ExternalResource() {
        override fun before() {
            apk = File(context.filesDir, "published.apk").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(4096) { it.toByte() }) }
            published = Release(AppVersion(0, 2, 0), apk.toURI().toString())
            Updates.forTests = ReleaseSource { published }
            // FileProvider keeps each authority's paths for the process, but every test has its own data directory.
            androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
        }

        override fun after() {
            Updates.forTests = null
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(releases).around(compose)

    private val preferences get() = UpdatePreferences(HyperlogDatabase.get(context))

    @Test
    fun tappingTheBannerOpensAndroidsInstallerOnTheDownloadedApk() {
        compose.waitFor("Hyperlog 0.2.0 is available")
        compose.click("Hyperlog 0.2.0 is available")
        // The download runs off the main thread; the installer opens once it has landed on the main one.
        var opened: Intent? = null
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            opened = shadowOf(compose.activity.application).nextStartedActivity
            opened != null
        }
        val started = opened!!
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("application/vnd.android.package-archive", started.type)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = started.data!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.updates", uri.authority)
        // The installer reads the downloaded copy through the FileProvider.
        val downloaded = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue(apk.readBytes().contentEquals(downloaded))
        // The banner stays, so a cancelled install can be tried again.
        compose.onNodeWithText("Hyperlog 0.2.0 is available").assertExists()
    }

    @Test
    fun dismissingHidesThatVersion() {
        compose.waitFor("Hyperlog 0.2.0 is available")
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.waitGone("Hyperlog 0.2.0 is available")
        assertEquals("0.2.0", runBlocking { preferences.dismissed() })
    }

    @Test
    fun aDownloadThatFailsSaysSoAndCanBeTriedAgain() {
        compose.waitFor("Hyperlog 0.2.0 is available")
        val missing = apk.readBytes()
        apk.delete()
        compose.click("Hyperlog 0.2.0 is available")
        compose.waitFor("Couldn't download it. Tap to try again.")
        apk.writeBytes(missing)
        compose.click("Hyperlog 0.2.0 is available")
        compose.waitGone("Couldn't download it. Tap to try again.")
    }

    @Test
    fun turningOffCheckForUpdatesHidesTheBannerAndStopsTheCheck() {
        compose.waitFor("Hyperlog 0.2.0 is available")
        compose.openTab("Settings")
        compose.waitFor("Check for updates")
        compose.onNode(hasText("Check for updates")).assertIsOn()
        compose.click("Check for updates")
        compose.onNode(hasText("Check for updates")).assertIsOff()
        assertEquals(false, runBlocking { preferences.checkForUpdates() })
        compose.openTab("Today")
        compose.waitFor("Today")
        compose.onNodeWithText("Hyperlog 0.2.0 is available").assertDoesNotExist()
    }

    @Test
    fun theAppMayUseTheNetworkAndAskToInstall() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue(requested.toString(), "android.permission.INTERNET" in requested)
        assertTrue(requested.toString(), "android.permission.REQUEST_INSTALL_PACKAGES" in requested)
        assertNotNull(context.packageManager.resolveContentProvider("${context.packageName}.updates", 0))
    }
}
