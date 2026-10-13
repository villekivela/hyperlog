package com.hyperlog.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app makes no network or model calls (ADR 0001): its merged manifest can't open a socket. */
@RunWith(RobolectricTestRunner::class)
class NoNetworkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theAppHasNoInternetPermission() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().toList()
        assertFalse("requested: $permissions", "android.permission.INTERNET" in permissions)
        assertFalse("requested: $permissions", "android.permission.ACCESS_NETWORK_STATE" in permissions)
    }

    @Test
    fun noSourceOpensAConnection() {
        val network = Regex("""java\.net\.(URL|HttpURLConnection|Socket)|javax\.net|okhttp|ktor|retrofit|com\.anthropic""")
        val offenders = java.io.File("src/main/java").walk().filter { it.extension == "kt" }
            .filter { network.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
