package com.hyperlog.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The licences of what the APK bundles ship inside it (SPEC, Stack). */
@RunWith(RobolectricTestRunner::class)
class LicencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun asset(name: String) = context.assets.open("licences/$name").bufferedReader().use { it.readText() }

    @Test
    fun filamentsLicenceAndNoticeAreInTheApk() {
        assertTrue(asset("Filament-LICENSE.txt").contains("Apache License"))
        assertTrue(asset("Filament-NOTICE.txt").contains("Filament 1.77.3"))
    }
}
