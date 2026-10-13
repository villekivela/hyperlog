package com.hyperlog.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Every schema change since v1, in order. Each one is frozen once released. */
val Migrations: Array<Migration> = arrayOf(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8, Migration8To9, Migration9To10, Migration10To11, Migration11To12, Migration12To13)

/** v13: History (#19). A dated bodyweight log, one value a day. */
object Migration12To13 : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `bodyweight` (`day` INTEGER NOT NULL, `kg` REAL NOT NULL, PRIMARY KEY(`day`))")
    }
}

/** v12: missing equipment (#17). A gym keeps the exercises it has no equipment for. */
object Migration11To12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gym_missing_exercise` (`gym_id` INTEGER NOT NULL, `exercise_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`gym_id`, `exercise_id`), " +
                "FOREIGN KEY(`gym_id`) REFERENCES `gym`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gym_missing_exercise_exercise_id` ON `gym_missing_exercise` (`exercise_id`)")
    }
}

/** v11: Changes (#16). Accepted Changes are kept per program, with the position they moved. */
object Migration10To11 : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `plan_change` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `program_id` INTEGER NOT NULL, " +
                "`json` TEXT NOT NULL, `accepted_at` INTEGER NOT NULL, `accepted_on` INTEGER NOT NULL, `week_before` INTEGER NOT NULL, " +
                "`day_before` INTEGER NOT NULL, `week_after` INTEGER NOT NULL, `day_after` INTEGER NOT NULL, `block_id` TEXT, " +
                "`reverted_at` INTEGER, FOREIGN KEY(`program_id`) REFERENCES `program`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_change_program_id` ON `plan_change` (`program_id`)")
    }
}

/** v10: warm-ups (#14). A session exercise counts the warm-ups ticked off before its first work set. */
object Migration9To10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE session_exercise ADD COLUMN warmups_done INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v9: editing a session on the fly (#12). A session exercise gains its own sets, when they were
 * edited in the session, and a mark for having been taken out of it.
 */
object Migration8To9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE session_exercise ADD COLUMN sets TEXT")
        db.execSQL("ALTER TABLE session_exercise ADD COLUMN removed INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v8: intensity techniques (#10). Logged sets gain their technique, their drops, mini-sets and
 * holds, and a right side's reps; a session gains its open set, the myo breathing pause and the
 * hold timer, and its exercises' sets can have their technique changed.
 */
object Migration7To8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE logged_set ADD COLUMN technique TEXT")
        db.execSQL("ALTER TABLE logged_set ADD COLUMN right_reps INTEGER")
        db.execSQL("ALTER TABLE session ADD COLUMN open_set_id INTEGER")
        db.execSQL("ALTER TABLE session ADD COLUMN breathing INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE session ADD COLUMN hold_started_at INTEGER")
        db.execSQL("ALTER TABLE session ADD COLUMN entry_right TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `set_extension` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `logged_set_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `kind` TEXT NOT NULL, `weight_kg` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
                "FOREIGN KEY(`logged_set_id`) REFERENCES `logged_set`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_extension_logged_set_id` ON `set_extension` (`logged_set_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `set_technique` (`session_exercise_id` INTEGER NOT NULL, `set_index` INTEGER NOT NULL, " +
                "`technique` TEXT, PRIMARY KEY(`session_exercise_id`, `set_index`), " +
                "FOREIGN KEY(`session_exercise_id`) REFERENCES `session_exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/** v7: starting weights from the old app (#9), one per exercise. */
object Migration6To7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `starting_weight` (`exercise_id` TEXT NOT NULL, `weight_kg` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
                "`rir` INTEGER, `done_at` INTEGER NOT NULL, PRIMARY KEY(`exercise_id`))",
        )
    }
}

/**
 * v6: a logged set remembers whether it was kept past a weight check (#8), so a kept typo doesn't
 * become the next set's prefill or reference. Earlier sets weren't.
 */
object Migration5To6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE logged_set ADD COLUMN kept INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v5: programs (#6). Imported programs with their position; sessions gain the program day they
 * train, and session exercises the slot they fill. Existing sessions stay ad hoc.
 */
object Migration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `program` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `imported_at` INTEGER NOT NULL, `week` INTEGER NOT NULL, `day_index` INTEGER NOT NULL, " +
                "`active` INTEGER NOT NULL)",
        )
        db.execSQL("ALTER TABLE session ADD COLUMN program_id INTEGER REFERENCES program(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("ALTER TABLE session ADD COLUMN week INTEGER")
        db.execSQL("ALTER TABLE session ADD COLUMN day_index INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_program_id` ON `session` (`program_id`)")
        db.execSQL("ALTER TABLE session_exercise ADD COLUMN slot_id TEXT")
    }
}

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

/**
 * v4: the gym at session start (#5). Sessions gain their gym, gyms a weekday plan, and session
 * exercises the machine they were done on; each gym remembers which machine an exercise uses.
 * Existing sessions keep no gym and no machine.
 */
object Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE gym ADD COLUMN weekdays INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE session ADD COLUMN gym_id INTEGER REFERENCES gym(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_gym_id` ON `session` (`gym_id`)")
        db.execSQL(
            "ALTER TABLE session_exercise ADD COLUMN equipment_item_id INTEGER " +
                "REFERENCES equipment_item(id) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_equipment_item_id` ON `session_exercise` (`equipment_item_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gym_exercise_item` (`gym_id` INTEGER NOT NULL, `exercise_id` TEXT NOT NULL, " +
                "`equipment_item_id` INTEGER NOT NULL, PRIMARY KEY(`gym_id`, `exercise_id`), " +
                "FOREIGN KEY(`gym_id`) REFERENCES `gym`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                "FOREIGN KEY(`equipment_item_id`) REFERENCES `equipment_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gym_exercise_item_exercise_id` ON `gym_exercise_item` (`exercise_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_gym_exercise_item_equipment_item_id` ON `gym_exercise_item` (`equipment_item_id`)")
    }
}
