package com.hyperlog.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Every schema change since v1, in order. Each one is frozen once released. */
val Migrations: Array<Migration> = arrayOf(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8, Migration8To9, Migration9To10, Migration10To11, Migration11To12, Migration12To13, Migration13To14, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19, Migration19To20, Migration20To21, Migration21To22)

/**
 * v22: workouts from the coach, a day outside the program (SPEC, Coaching → Workout), and the
 * session that trains one. Existing sessions train none.
 */
object Migration21To22 : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                "`added_at` INTEGER NOT NULL, `added_on` INTEGER NOT NULL, `gym_id` INTEGER, " +
                "FOREIGN KEY(`gym_id`) REFERENCES `gym`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_gym_id` ON `workout` (`gym_id`)")
        db.execSQL("ALTER TABLE session ADD COLUMN workout_id INTEGER REFERENCES workout(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_workout_id` ON `session` (`workout_id`)")
    }
}

/**
 * v21: a set may be logged without an RIR (SPEC, Logging a set). SQLite can't drop NOT NULL in
 * place, so the table is rebuilt. Foreign keys are off while a migration runs, so the drop leaves
 * the sets' drops, mini-sets and holds alone.
 */
object Migration20To21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `logged_set_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_exercise_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `full_reps` INTEGER NOT NULL, `partial_reps` INTEGER NOT NULL, " +
                "`rir` INTEGER, `logged_at` INTEGER NOT NULL, `kept` INTEGER NOT NULL DEFAULT 0, `technique` TEXT, `right_reps` INTEGER, " +
                "FOREIGN KEY(`session_exercise_id`) REFERENCES `session_exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO logged_set_new (id, session_exercise_id, position, weight_kg, full_reps, partial_reps, rir, logged_at, kept, technique, right_reps) " +
                "SELECT id, session_exercise_id, position, weight_kg, full_reps, partial_reps, rir, logged_at, kept, technique, right_reps FROM logged_set",
        )
        db.execSQL("DROP TABLE logged_set")
        db.execSQL("ALTER TABLE logged_set_new RENAME TO logged_set")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_logged_set_session_exercise_id` ON `logged_set` (`session_exercise_id`)")
    }
}

/** v20: warm-ups typed by hand, kept with the session exercise for that session (SPEC, Warm-ups). */
object Migration19To20 : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE session_exercise ADD COLUMN warmups TEXT")
    }
}

/**
 * v19: each gym's own plates, as pairs per size, in place of one shared set (SPEC, Gyms and
 * equipment).
 */
object Migration18To19 : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE gym ADD COLUMN plates TEXT")
        moveSharedPlates(db)
    }

    /**
     * The shared plates go to every gym. A set edited by hand is kept for each gym; the old
     * default (three pairs of 20, then 15, 10, 5, 2.5, 1.25) gives way to the typical gym's, which
     * every gym without plates of its own has. Plate-loaded machines holding a copy of the shared
     * pairs (or of the old default) load their gym's plates from now on. The shared entry keeps
     * only the barbell's weight. Also run on a restored backup from before v19.
     */
    fun moveSharedPlates(db: SupportSQLiteDatabase) {
        val shared = db.query("SELECT ladder FROM shared_ladder WHERE `key` = 'plates'").use { if (it.moveToFirst()) it.getString(0) else null }
        val spec = shared?.let { com.hyperlog.app.domain.LadderSpec.decode(it) } as? com.hyperlog.app.domain.LadderSpec.Plates
        val old = com.hyperlog.app.domain.LadderSpec.OldDefaultPairs.sorted()
        val pairs = spec?.pairs?.filter { it > 0 }?.sorted().orEmpty()
        if (pairs.isNotEmpty() && pairs != old) {
            db.execSQL("UPDATE gym SET plates = ? WHERE plates IS NULL", arrayOf<Any?>(pairs.sortedDescending().joinToString(",") { it.toString() }))
        }
        val copies = setOf(old, pairs).filter { it.isNotEmpty() }
        db.query("SELECT id, ladder FROM equipment_item").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }.forEach { (id, ladder) ->
            val machine = com.hyperlog.app.domain.LadderSpec.decode(ladder) as? com.hyperlog.app.domain.LadderSpec.Plates ?: return@forEach
            if (machine.pairs.filter { it > 0 }.sorted() in copies) {
                db.execSQL("UPDATE equipment_item SET ladder = ? WHERE id = ?", arrayOf<Any?>(machine.copy(pairs = emptyList()).encode(), id))
            }
        }
        if (spec != null) {
            db.execSQL("UPDATE shared_ladder SET ladder = ? WHERE `key` = 'plates'", arrayOf<Any?>(spec.copy(pairs = emptyList()).encode()))
        }
    }
}

/**
 * v18 (easier equipment): a gym's small plates for the pin of its stacks, a dumbbell rack per gym
 * in place of the one shared ladder, how sure each machine's ladder is, the gym's cable station,
 * the stack chosen for setting a gym up from the program, and racks and bars from an imported gym.
 */
object Migration17To18 : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE gym ADD COLUMN pin_plates TEXT")
        db.execSQL("ALTER TABLE gym ADD COLUMN dumbbells TEXT")
        db.execSQL("ALTER TABLE gym ADD COLUMN default_stack TEXT")
        db.execSQL("ALTER TABLE equipment_item ADD COLUMN ladder_state TEXT NOT NULL DEFAULT 'confirmed'")
        db.execSQL("ALTER TABLE equipment_item ADD COLUMN cable_station INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE equipment_item ADD COLUMN kind TEXT NOT NULL DEFAULT 'machine'")
        moveSharedDumbbells(db)
    }

    /**
     * The shared dumbbell ladder goes to every gym's rack. One edited by hand is kept; the old
     * default (2 kg steps to 20, then 22.5, 25 and 5s) gives way to the new one, which every gym
     * without a rack of its own has. Also run on a restored backup from before v18.
     */
    fun moveSharedDumbbells(db: SupportSQLiteDatabase) {
        val shared = db.query("SELECT ladder FROM shared_ladder WHERE `key` = 'dumbbells'").use { if (it.moveToFirst()) it.getString(0) else null }
        val spec = shared?.let { com.hyperlog.app.domain.LadderSpec.decode(it) }
        val old = com.hyperlog.app.domain.LadderSpec.OldDefaultDumbbells
        val edited = spec != null && spec.ladder().rungs != old.ladder().rungs
        if (edited) db.execSQL("UPDATE gym SET dumbbells = ? WHERE dumbbells IS NULL", arrayOf<Any?>(shared))
        db.execSQL("DELETE FROM shared_ladder WHERE `key` = 'dumbbells'")
    }
}

/**
 * v17: preferences the user sets, first of them "Check for updates" and the dismissed update, and
 * the last update check (#23).
 */
object Migration16To17 : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `setting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `update_check` (`id` INTEGER NOT NULL, `checked_at` INTEGER NOT NULL, `version` TEXT, " +
                "`apk_url` TEXT, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v16: two-dumbbell exercises (code review), whose estimate counts both dumbbells. The shipped ones
 * are marked here too, so an exercise edited on the phone (which the seed no longer updates) gets
 * the mark it never had a say in.
 */
object Migration15To16 : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise ADD COLUMN two_dumbbells INTEGER NOT NULL DEFAULT 0")
        val ids = TwoDumbbells.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE exercise SET two_dumbbells = 1 WHERE equipment = 'Dumbbell' AND id IN ($ids)")
    }

    /** The shipped library's two-dumbbell exercises when v16 came. */
    private val TwoDumbbells = listOf(
        "arnold_press_seated", "bent_over_reverse_db_flye", "chest_supported_db_row", "db_bench_press", "db_bicep_curl",
        "db_bulgarian_split_squat", "db_flye", "db_hammer_curl", "db_incline_curl", "db_incline_press", "db_lateral_raise",
        "db_lunge", "db_romanian_deadlift", "db_step_up", "db_walking_lunge", "db_wrist_curl", "db_wrist_extension",
        "dumbbell_shoulder_press_standing", "helms_db_row", "incline_chest_supported_db_row", "incline_db_kelso_shrug",
        "incline_db_y_raise", "inverse_zottman_curl", "lying_reverse_db_flye", "modified_zottman_curl",
        "preacher_hammer_curl", "seated_db_shoulder_press", "spider_curl", "standing_db_arnold_press",
    )
}

/**
 * v15: deleting a gym or machine that has history archives it (code review), so its sessions keep
 * their machine instead of falling into the no-gym pool.
 */
object Migration14To15 : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE gym ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE equipment_item ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
}

/** v14: lift goals (#20). One target for an exercise's estimate, optionally by a date. */
object Migration13To14 : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `lift_goal` (`exercise_id` TEXT NOT NULL, `kg` REAL NOT NULL, `by_day` INTEGER, PRIMARY KEY(`exercise_id`))")
    }
}

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
