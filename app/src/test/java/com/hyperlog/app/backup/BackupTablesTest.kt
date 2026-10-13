package com.hyperlog.app.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every Room table is either in the backup or excluded with a reason, so a new table can't be
 * forgotten: adding one fails this test until it is listed in [BackupFormat].
 */
@RunWith(RobolectricTestRunner::class)
class BackupTablesTest {
    private lateinit var db: HyperlogDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    private fun roomTables(): Set<String> = db.openHelper.readableDatabase
        .query("SELECT name FROM sqlite_master WHERE type = 'table'")
        .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        .filterNot { it.startsWith("sqlite_") || it == "android_metadata" || it == "room_master_table" }
        .toSet()

    @Test
    fun everyTableIsBackedUpOrExcludedWithAReason() {
        val listed = BackupFormat.Tables.toSet() + BackupFormat.Excluded.keys
        assertEquals("tables neither backed up nor excluded", emptySet<String>(), roomTables() - listed)
        assertEquals("listed tables that don't exist", emptySet<String>(), listed - roomTables())
    }

    @Test
    fun noTableIsBothBackedUpAndExcluded() {
        assertEquals(emptySet<String>(), BackupFormat.Tables.toSet() intersect BackupFormat.Excluded.keys)
        assertEquals(BackupFormat.Tables.size, BackupFormat.Tables.toSet().size)
    }

    @Test
    fun everyExclusionSaysWhy() {
        BackupFormat.Excluded.forEach { (table, reason) -> assertTrue("$table has no reason", reason.isNotBlank()) }
    }

    @Test
    fun theBackupKnowsTheDatabaseVersion() {
        assertEquals(db.openHelper.readableDatabase.version, BackupFormat.DATABASE)
    }
}
