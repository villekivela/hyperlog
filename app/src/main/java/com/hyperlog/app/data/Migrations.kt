package com.hyperlog.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Every schema change since v1, in order. Each one is frozen once released. */
val Migrations: Array<Migration> = arrayOf(Migration1To2, Migration2To3)

/**
 * v2: the exercise library (#3). The exercise table gains the library's fields; the library sync
 * fills them in. The ad-hoc starter list's ids that aren't library ids move to the library's
 * exercise, sessions included.
 */
object Migration1To2 : Migration(1, 2) {
    /** Starter-list id -> library id. The other starter ids already were library ids. */
    val renamed = mapOf(
        "pull_up" to "pullup_wide_grip",
        "back_squat" to "barbell_squat",
        "romanian_deadlift" to "barbell_rdl",
        "cable_triceps_pushdown" to "triceps_pressdown",
        "db_curl" to "db_bicep_curl",
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "movement_pattern TEXT NOT NULL DEFAULT ''",
            "grip TEXT NOT NULL DEFAULT ''",
            "muscles TEXT NOT NULL DEFAULT ''",
            "secondary_muscles TEXT NOT NULL DEFAULT ''",
            "video_url TEXT",
            "note TEXT",
            "hidden INTEGER NOT NULL DEFAULT 0",
            "edited INTEGER NOT NULL DEFAULT 0",
        ).forEach { db.execSQL("ALTER TABLE exercise ADD COLUMN $it") }
        renamed.forEach { (old, new) ->
            // The library row must exist before sessions point at it; the sync completes it.
            db.execSQL(
                "INSERT OR IGNORE INTO exercise (id, name, equipment) SELECT ?, name, equipment FROM exercise WHERE id = ?",
                arrayOf(new, old),
            )
            db.execSQL("UPDATE session_exercise SET exercise_id = ? WHERE exercise_id = ?", arrayOf(new, old))
            db.execSQL("DELETE FROM exercise WHERE id = ?", arrayOf(old))
        }
    }
}

/** v3: gyms, their equipment items with weight ladders, and the shared dumbbell and plate ladders (#4). */
object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `gym` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `equipment_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gym_id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `ladder` TEXT NOT NULL, FOREIGN KEY(`gym_id`) REFERENCES `gym`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipment_item_gym_id` ON `equipment_item` (`gym_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `shared_ladder` (`key` TEXT NOT NULL, `ladder` TEXT NOT NULL, PRIMARY KEY(`key`))")
    }
}
