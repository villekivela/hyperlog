package com.hyperlog.app.update

import com.hyperlog.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A release tag names the version; its code orders releases (SPEC, Distribution). */
class AppVersionTest {
    @Test
    fun aTagGivesTheVersionNameAndCode() {
        val version = AppVersion.parse("v0.2.0")!!
        assertEquals("0.2.0", version.name)
        assertEquals(200, version.code)
        assertEquals(1003, AppVersion.parse("0.10.3")!!.code)
        assertEquals(10_000, AppVersion.parse("v1.0.0")!!.code)
    }

    @Test
    fun aLaterVersionHasTheHigherCode() {
        assertTrue(AppVersion.parse("v1.0.0")!!.code > AppVersion.parse("v0.99.99")!!.code)
        assertTrue(AppVersion.parse("v0.2.0")!!.code > AppVersion.parse("v0.1.12")!!.code)
    }

    @Test
    fun anythingElseIsNoVersion() {
        listOf("latest", "v1.2", "v1.100.0", "v1.0.100", "v1.0.0-beta", "", "vv1.0.0").forEach { assertNull(it, AppVersion.parse(it)) }
    }

    @Test
    fun theBuildsVersionCodeComesFromItsVersionName() {
        // build.gradle.kts works the code out from -PhyperlogVersion the same way.
        assertEquals(BuildConfig.VERSION_CODE, AppVersion.parse(BuildConfig.VERSION_NAME.removeSuffix("-dev"))!!.code)
    }
}
