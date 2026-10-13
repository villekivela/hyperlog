package com.hyperlog.app.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The adb checks in docs/backups.md run as written on the phone (Android 17). */
class BackupsDocTest {
    private val doc = File("../docs/backups.md").readText()

    @Test
    fun aRestoreNamesItsRestoreSetAndOnlyThisPackage() {
        // Found on the phone: `bmgr restore <package>` is "no longer supported"; it takes a restore
        // set's token, and a token alone restores every app on the phone.
        val restores = Regex("""adb shell bmgr restore [^\n#]*""").findAll(doc).map { it.value.trim() }.toList()
        assertTrue("no bmgr restore in the doc", restores.isNotEmpty())
        restores.forEach { assertTrue(it, Regex("""adb shell bmgr restore \S+ com\.hyperlog\.app(\.dev)?""").matches(it)) }
    }
}
