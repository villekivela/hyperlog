package com.hyperlog.app.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

/**
 * Android Auto Backup is on and takes the database (`docs/backups.md`): the cloud backup and a
 * device-to-device transfer on Android 12+, and the older full-backup rules below that.
 */
@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File(path)).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.domains() = children("include").map { it.getAttribute("domain") to it.getAttribute("path") }

    private val manifest by lazy { xml("src/main/AndroidManifest.xml").children("application").single() }

    @Test
    fun backupIsAllowed() {
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertEquals("true", manifest.getAttribute("android:allowBackup"))
    }

    @Test
    fun android12AndLaterBackUpAndTransferTheDatabase() {
        assertEquals("@xml/data_extraction_rules", manifest.getAttribute("android:dataExtractionRules"))
        val rules = xml("src/main/res/xml/data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { kind ->
            assertEquals(kind, listOf("database" to "."), rules.children(kind).single().domains())
        }
    }

    @Test
    fun olderAndroidBacksUpTheDatabase() {
        assertEquals("@xml/backup_rules", manifest.getAttribute("android:fullBackupContent"))
        assertEquals(listOf("database" to "."), xml("src/main/res/xml/backup_rules.xml").domains())
    }

    @Test
    fun theDatabaseLivesInTheBackedUpDomain() {
        assertEquals("databases", context.getDatabasePath("hyperlog.db").parentFile!!.name)
    }
}
