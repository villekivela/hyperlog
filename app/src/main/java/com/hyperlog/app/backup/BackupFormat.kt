package com.hyperlog.app.backup

/**
 * The backup file (SPEC, Backups; `docs/backups.md`): every table in [Tables], row for row, as
 * JSON objects of column to value.
 *
 * ```json
 * { "format": "hyperlog-backup", "version": 1, "database": 17, "exportedAt": 1760000000000,
 *   "tables": { "gym": [ { "id": 1, "name": "Central", "weekdays": 1 } ], … } }
 * ```
 */
object BackupFormat {
    const val FORMAT = "hyperlog-backup"

    /** The file's own layout. Bump it when the envelope changes, not when a table does. */
    const val VERSION = 1

    /**
     * The Room schema version the tables follow. A backup from an older database needs an upgrade
     * step in [Backups] before it can be restored; a test fixture of each version keeps them working.
     */
    const val DATABASE = 19

    /** The oldest database version a backup can be restored from. */
    const val OLDEST_DATABASE = 14

    /** Every table a backup holds: everything the user made, the running session included. */
    val Tables = listOf(
        // The library: the shipped exercises plus edits, hidden flags and exercises added on the phone.
        "exercise",
        // Gyms, their equipment and ladders, the remembered machine and missing equipment per gym.
        "gym", "equipment_item", "shared_ladder", "gym_exercise_item", "gym_missing_exercise",
        // Programs with their position, and the Changes accepted (and reverted) on them.
        "program", "plan_change",
        // Sessions, the running one with its pad draft and rest timer, their sets and session edits.
        "session", "session_exercise", "logged_set", "set_extension", "set_technique",
        "starting_weight", "bodyweight", "lift_goal",
        // Preferences: "Check for updates" and the update banner dismissed.
        "setting",
    )

    /**
     * What each database version after [OLDEST_DATABASE] added, table to columns (an empty set for
     * a whole new table). A file from before a version may lack them: the table restores empty and
     * the columns take their defaults. Anything else missing from a file makes it damaged.
     */
    val Added: Map<Int, Map<String, Set<String>>> = mapOf(
        15 to mapOf("gym" to setOf("archived"), "equipment_item" to setOf("archived")),
        16 to mapOf("exercise" to setOf("two_dumbbells")),
        17 to mapOf("setting" to emptySet()),
        18 to mapOf("gym" to setOf("pin_plates", "dumbbells", "default_stack"), "equipment_item" to setOf("ladder_state", "cable_station", "kind")),
        19 to mapOf("gym" to setOf("plates")),
    )

    /** The columns of [table] a file made at database version [database] may lack. */
    fun addedSince(database: Int, table: String): Set<String>? =
        Added.filterKeys { it > database }.values.mapNotNull { it[table] }.takeIf { it.isNotEmpty() }?.flatten()?.toSet()

    /**
     * Tables left out of the backup, each with the reason: only caches of something else. A
     * restore leaves them as they are.
     */
    val Excluded: Map<String, String> = mapOf(
        "update_check" to "A cache of GitHub's latest release, read again within 15 minutes.",
    )
}
