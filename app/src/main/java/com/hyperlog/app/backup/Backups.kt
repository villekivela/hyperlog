package com.hyperlog.app.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.hyperlog.app.data.HyperlogDatabase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Manual backups (SPEC, Backups): [export] writes everything on the phone to one JSON file, and
 * [restore] replaces everything on the phone with a file's contents, all or nothing.
 */
class Backups(private val db: HyperlogDatabase, private val clock: () -> Long = System::currentTimeMillis) {

    sealed interface Result {
        data object Restored : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        /** Not a Hyperlog backup at all (a program file, the old app's backup, anything else). */
        NotABackup,
        /** Made by a newer Hyperlog than this one. */
        Newer,
        /** Made by a Hyperlog older than any this one can read. */
        Older,
        /** A backup, but its rows are broken or don't fit together. */
        Damaged,
    }

    /** Every table in [BackupFormat.Tables], read in one transaction so the file is consistent. */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        db.runInTransaction<String> {
            val sql = db.openHelper.writableDatabase
            val tables = BackupFormat.Tables.associateWith { table ->
                JsonArray(sql.query("SELECT * FROM `$table` ORDER BY rowid").use { c -> buildList { while (c.moveToNext()) add(c.row()) } })
            }
            JsonObject(
                mapOf(
                    "format" to JsonPrimitive(BackupFormat.FORMAT),
                    "version" to JsonPrimitive(BackupFormat.VERSION),
                    "database" to JsonPrimitive(BackupFormat.DATABASE),
                    "exportedAt" to JsonPrimitive(clock()),
                    "tables" to JsonObject(tables),
                ),
            ).toString()
        }
    }

    /** What [file] holds, read without touching the phone: when it was made, or why it can't be restored. */
    sealed interface Contents {
        data class Readable(val exportedAt: Long) : Contents
        data class Unreadable(val reason: Reason) : Contents
    }

    /** Reads [file] to say when it was made before anything is replaced. */
    suspend fun read(file: String): Contents = withContext(Dispatchers.IO) {
        when (val parsed = parse(file)) {
            is Parsed.Rows -> Contents.Readable(parsed.exportedAt)
            is Parsed.Refused -> Contents.Unreadable(parsed.reason)
        }
    }

    /**
     * Replaces everything on the phone with [file]'s contents. Any problem leaves the phone as it
     * was: the file is checked first, and the rows go in inside one transaction that rolls back
     * unless every row fits and every reference between them holds.
     */
    suspend fun restore(file: String): Result = withContext(Dispatchers.IO) {
        val parsed = when (val p = parse(file)) {
            is Parsed.Refused -> return@withContext Result.Invalid(p.reason)
            is Parsed.Rows -> p
        }
        val rows = parsed.rows
        val database = parsed.database
        try {
            db.runInTransaction {
                val sql = db.openHelper.writableDatabase
                // References are checked once every row is in, so the tables can go in any order.
                sql.execSQL("PRAGMA defer_foreign_keys = ON")
                BackupFormat.Tables.asReversed().forEach { sql.execSQL("DELETE FROM `$it`") }
                // Autoincrement counters follow the ids put back.
                sql.execSQL("DELETE FROM sqlite_sequence")
                BackupFormat.Tables.forEach { table ->
                    rows[table].orEmpty().forEach { sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, it) }
                }
                if (sql.query("PRAGMA foreign_key_check").use { it.moveToFirst() }) throw Mismatch()
                // Upgrade steps for files from older databases, as their migrations did.
                if (database < 18) com.hyperlog.app.data.Migration17To18.moveSharedDumbbells(sql)
                if (database < 19) com.hyperlog.app.data.Migration18To19.moveSharedPlates(sql)
            }
            Result.Restored
        } catch (_: android.database.SQLException) {
            Result.Invalid(Reason.Damaged)
        } catch (_: Mismatch) {
            Result.Invalid(Reason.Damaged)
        }
    }

    private sealed interface Parsed {
        data class Rows(val exportedAt: Long, val rows: Map<String, List<ContentValues>>, val database: Int) : Parsed
        data class Refused(val reason: Reason) : Parsed
    }

    /** The file's rows, checked against the tables and columns on the phone, or why it is refused. */
    private fun parse(file: String): Parsed {
        val backup = runCatching { Json.parseToJsonElement(file) as? JsonObject }.getOrNull()
        if (backup == null || (backup["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content != BackupFormat.FORMAT) {
            return Parsed.Refused(Reason.NotABackup)
        }
        val version = (backup["version"] as? JsonPrimitive)?.intOrNull ?: return Parsed.Refused(Reason.Damaged)
        val database = (backup["database"] as? JsonPrimitive)?.intOrNull ?: return Parsed.Refused(Reason.Damaged)
        when {
            version > BackupFormat.VERSION || database > BackupFormat.DATABASE -> return Parsed.Refused(Reason.Newer)
            database < BackupFormat.OLDEST_DATABASE -> return Parsed.Refused(Reason.Older)
        }
        val exportedAt = (backup["exportedAt"] as? JsonPrimitive)?.longOrNull ?: return Parsed.Refused(Reason.Damaged)
        val stored = backup["tables"] as? JsonObject ?: return Parsed.Refused(Reason.Damaged)
        if (!BackupFormat.Tables.containsAll(stored.keys)) return Parsed.Refused(Reason.Damaged)
        val tables = if (database < 23) runCatching { movedWeekdays(stored) }.getOrNull() ?: return Parsed.Refused(Reason.Damaged) else stored
        // A table left out of the file would be wiped, so every one must be there unless it is newer than the file.
        val missing = BackupFormat.Tables - tables.keys
        if (missing.any { BackupFormat.addedSince(database, it)?.isEmpty() != true }) return Parsed.Refused(Reason.Damaged)
        return try {
            Parsed.Rows(exportedAt, tables.mapValues { (table, rows) -> (rows as JsonArray).map { contentValues(table, it as JsonObject, database) } }, database)
        } catch (_: RuntimeException) {
            Parsed.Refused(Reason.Damaged)
        }
    }

    /**
     * A file from before v23, when each gym had its weekdays: they become the weekday plan, each day
     * with its gym, as [com.hyperlog.app.data.Migration22To23] moves them, and leave the gym rows.
     */
    private fun movedWeekdays(tables: JsonObject): JsonObject {
        val gyms = tables["gym"]?.let { it as JsonArray }.orEmpty().map { it as JsonObject }
        fun JsonObject.long(key: String) = (get(key) as JsonPrimitive).longOrNull!!
        // Not deleted, in the gym list's order: by name ignoring ASCII case (SQLite's NOCASE), then id.
        val listed = gyms.filter { (it["archived"] as? JsonPrimitive)?.longOrNull != 1L }
            .sortedWith(compareBy<JsonObject>({ (it["name"] as JsonPrimitive).content.asciiLowercase() }, { it.long("id") }))
        val plan = com.hyperlog.app.data.Migration22To23.planFrom(listed.map { it.long("id") to ((it["weekdays"] as? JsonPrimitive)?.longOrNull ?: 0L).toInt() })
        val days = plan.entries.sortedBy { it.key }.map { (day, gym) ->
            JsonObject(mapOf("weekday" to JsonPrimitive(day), "gym_id" to JsonPrimitive(gym)))
        }
        return JsonObject(tables + mapOf("gym" to JsonArray(gyms.map { JsonObject(it - "weekdays") }), "weekday_plan" to JsonArray(days)))
    }

    private fun String.asciiLowercase() = map { if (it in 'A'..'Z') it + ('a' - 'A') else it }.joinToString("")

    private class Mismatch : RuntimeException()

    private val columns = mutableMapOf<String, Set<String>>()

    private fun columnsOf(table: String): Set<String> = columns.getOrPut(table) {
        db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(name)) }
        }
    }

    /**
     * One row as the database takes it. Throws on a column the table doesn't have, a value it can't
     * hold, or a column missing that a file from [database] should have.
     */
    private fun contentValues(table: String, row: JsonObject, database: Int): ContentValues {
        val known = columnsOf(table)
        val required = known - BackupFormat.addedSince(database, table).orEmpty()
        require(row.keys.containsAll(required)) { "$table lacks ${required - row.keys}" }
        return ContentValues().apply {
            row.forEach { (column, value) ->
                require(column in known) { "$table has no column $column" }
                when {
                    value is JsonNull -> putNull(column)
                    value !is JsonPrimitive -> throw IllegalArgumentException("$table.$column is not a value")
                    value.isString -> put(column, value.content)
                    else -> value.longOrNull?.let { put(column, it) } ?: put(column, value.content.toDouble())
                }
            }
        }
    }

    private fun Cursor.row(): JsonObject = JsonObject(
        (0 until columnCount).associate { i ->
            getColumnName(i) to when (getType(i)) {
                Cursor.FIELD_TYPE_NULL -> JsonNull
                Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
                Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(i))
                else -> error("No table holds blobs")
            }
        },
    )

    companion object {
        /** The name the export suggests: `hyperlog-backup-2026-10-13.json`. */
        fun fileName(date: LocalDate) = "hyperlog-backup-$date.json"
    }
}
