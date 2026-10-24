package com.hyperlog.app.backup

/**
 * The backup file (SPEC, Backups; `docs/backups.md`): every table in [Tables], row for row, as
 * JSON objects of column to value.
 *
 * ```json
 * { "format": "hyperlog-backup", "version": 1, "database": 17, "exportedAt": 1760000000000,
 *   "tables": { "gym": [ { "id": 1, "name": "Central", "archived": 0, … } ], "weekday_plan": [ { "weekday": 1, "gym_id": 1 } ], … } }
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
    const val DATABASE = 24

    /** The oldest database version a backup can be restored from. */
    const val OLDEST_DATABASE = 14

    /** Every table a backup holds: everything the user made, the running session included. */
    val Tables = listOf(
        // The library: the shipped exercises plus edits, hidden flags and exercises added on the phone.
        "exercise",
        // Gyms, their equipment and ladders, the remembered machine and missing equipment per gym.
        "gym", "equipment_item", "shared_ladder", "gym_exercise_item", "gym_missing_exercise",
        // Your training days, each with its gym.
        "weekday_plan",
        // Programs with their position, and the Changes accepted (and reverted) on them.
        "program", "plan_change",
        // Workouts from the coach: the one waiting on Today, and those sessions trained.
        "workout",
        // Sessions, the running one with its pad draft and rest timer, their sets and session edits.
        "session", "session_exercise", "logged_set", "set_extension", "set_technique",
        "starting_weight", "bodyweight", "lift_goal",
        // Preferences: "Check for updates", the update banner dismissed, the last export's time
        // (written after its file, so a backup holds the export before it), and each month's coach
        // cost. The Claude key isn't here: it stays on the phone, outside the database.
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
        20 to mapOf("session_exercise" to setOf("warmups")),
        22 to mapOf("workout" to emptySet(), "session" to setOf("workout_id")),
        23 to mapOf("weekday_plan" to emptySet()),
        24 to mapOf("plan_change" to setOf("check_in")),
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
