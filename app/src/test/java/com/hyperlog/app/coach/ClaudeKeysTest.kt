package com.hyperlog.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.backup.Backups
import com.hyperlog.app.data.HyperlogDatabase
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Claude key stays on the phone, sealed, and never in a backup; a restore keeps the phone's
 * key (SPEC, Coaching → With a Claude key).
 */
@RunWith(RobolectricTestRunner::class)
class ClaudeKeysTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = ClaudeKeys.file(context)
    private val open = mutableListOf<HyperlogDatabase>()

    /** Robolectric has no Android Keystore; this stands in for it, and isn't the key in the clear. */
    private val seal = object : KeySeal {
        override fun seal(plain: ByteArray) = plain.reversedArray().map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun open(sealed: ByteArray) = sealed.map { (it.toInt() xor 0x5a).toByte() }.toByteArray().reversedArray()
    }

    private fun keys() = ClaudeKeys(file, seal)

    private fun phone() = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build().also { open += it }

    @After
    fun close() {
        open.forEach { it.close() }
        file.delete()
    }

    private val key = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz-0123456789-a1b2"

    @Test
    fun aSavedKeyReadsBackAfterARestartAndShowsOnlyItsEnding() {
        keys().save(key)
        val restarted = keys()
        assertEquals(key, restarted.key())
        assertEquals("a1b2", restarted.ending.value)
        assertFalse(String(file.readBytes()).contains("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun removeForgetsIt() {
        val keys = keys()
        keys.save(key)
        keys.remove()
        assertNull(keys.key())
        assertNull(keys.ending.value)
        assertFalse(file.exists())
        assertNull(keys().key())
    }

    @Test
    fun theKeyLivesWhereAndroidNeverBacksUp() {
        assertEquals(context.noBackupFilesDir, file.parentFile)
        // Auto Backup includes only the database (AutoBackupTest); no rule may reach for no_backup.
        val rules = listOf("src/main/res/xml/data_extraction_rules.xml", "src/main/res/xml/backup_rules.xml").map { File(it).readText() }
        rules.forEach { rule -> assertEquals(setOf("database"), Regex("""domain="([^"]*)"""").findAll(rule).map { it.groupValues[1] }.toSet()) }
    }

    @Test
    fun aBackupRoundTripLeavesTheKeyOutAndKeepsThePhonesKey() = runTest {
        val keys = keys()
        keys.save(key)
        val old = phone()
        MeteredCoach(
            FakeCoachModel.replies(CoachReply("Which hand?", usage = listOf(CoachUsage(ClaudeClient.MODEL, output = 1_000)))),
            CoachCost(old) { YearMonth.of(2026, 10) },
        ).reply(CoachRequest("The program", emptyList(), listOf(CoachMessage.User("Something hurts"))))
        val file = Backups(old).export()
        assertFalse(file.contains(key))
        assertFalse(file.contains("a1b2"))
        // The month's cost is the user's record, so it travels with the backup.
        assertTrue(file.contains("coach_cost_2026-10"))

        // Restored on a phone with a key of its own, which it keeps.
        val other = "sk-ant-api03-another-key-c3d4"
        keys.save(other)
        val restored = phone()
        assertEquals(Backups.Result.Restored, Backups(restored).restore(file))
        assertEquals(other, keys.key())
        assertEquals(20_000L, CoachCost(restored) { YearMonth.of(2026, 10) }.thisMonth.first())
    }
}
