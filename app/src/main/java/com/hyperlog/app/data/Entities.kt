package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An exercise in the library. Muscle lists are stored one name per line. */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** An [com.hyperlog.app.library.Equipment] name. */
    val equipment: String,
    /** A [com.hyperlog.app.library.MovementPattern] name. */
    @ColumnInfo(name = "movement_pattern", defaultValue = "''") val movementPattern: String,
    /** A [com.hyperlog.app.library.GripDemand] name. */
    @ColumnInfo(defaultValue = "''") val grip: String,
    @ColumnInfo(defaultValue = "''") val muscles: String,
    @ColumnInfo(name = "secondary_muscles", defaultValue = "''") val secondaryMuscles: String,
    @ColumnInfo(name = "video_url") val videoUrl: String?,
    val note: String?,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** Changed on the phone, so a newer seed no longer overwrites it. */
    @ColumnInfo(defaultValue = "0") val edited: Boolean = false,
    /** Done with a dumbbell in each hand, so its estimate counts both. */
    @ColumnInfo(name = "two_dumbbells", defaultValue = "0") val twoDumbbells: Boolean = false,
)

/** A session. One with no [finishedAt] is running; there is at most one. */
@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(GymEntity::class, ["id"], ["gym_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ProgramEntity::class, ["id"], ["program_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("gym_id"), Index("program_id")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    /** The session exercise on screen. */
    @ColumnInfo(name = "current_session_exercise_id") val currentSessionExerciseId: Long? = null,
    @ColumnInfo(name = "rest_ends_at") val restEndsAt: Long? = null,
    @ColumnInfo(name = "rest_total_ms") val restTotalMs: Long = 0,
    @ColumnInfo(name = "pad_visible") val padVisible: Boolean = true,
    /** What the pad holds for the active row, saved on every tap. */
    @Embedded(prefix = "entry_") val entry: EntryColumns? = null,
    /** Where the session is trained; null when no gym is set up. */
    @ColumnInfo(name = "gym_id") val gymId: Long? = null,
    /** The program whose day this session trains; null for an ad-hoc session. */
    @ColumnInfo(name = "program_id") val programId: Long? = null,
    /** The program day trained: its week and its index in that week's block. */
    val week: Int? = null,
    @ColumnInfo(name = "day_index") val dayIndex: Int? = null,
    /**
     * The logged set whose drops, mini-sets or hold are being logged; null when no set is open.
     * Rest waits until it closes.
     */
    @ColumnInfo(name = "open_set_id") val openSetId: Long? = null,
    /** [restEndsAt] is a myo-reps breathing pause before a mini-set, not rest. */
    @ColumnInfo(defaultValue = "0") val breathing: Boolean = false,
    /** When the running iso hold started; null with no hold timer running. */
    @ColumnInfo(name = "hold_started_at") val holdStartedAt: Long? = null,
)

data class EntryColumns(
    val weight: String,
    val full: String,
    val partial: String,
    val rir: Int?,
    /** A [com.hyperlog.app.domain.Field] name. */
    val field: String,
    @ColumnInfo(name = "partial_mode") val partialMode: Boolean,
    val fresh: Boolean,
    /** The right side's reps of a split unilateral set; null when not split. */
    val right: String? = null,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"]),
        ForeignKey(EquipmentItemEntity::class, ["id"], ["equipment_item_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("session_id"), Index("exercise_id"), Index("equipment_item_id")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    /**
     * The machine or cable station it was done on. Their history is kept per item (ADR 0002);
     * null for free weights and while the session doesn't know the machine yet.
     */
    @ColumnInfo(name = "equipment_item_id") val equipmentItemId: Long? = null,
    /** The program slot it fills, in a session of a program day; null when added ad hoc. */
    @ColumnInfo(name = "slot_id") val slotId: String? = null,
    /**
     * Its sets as edited in this session ([com.hyperlog.app.program.SetPrescriptionJson]); null while
     * they are the program's. Edits apply to this session only.
     */
    val sets: String? = null,
    /** Taken out of this session; kept so the edit can later become a Change. */
    @ColumnInfo(defaultValue = "0") val removed: Boolean = false,
    /** How many of its warm-ups are ticked off. Warm-ups are never logged as sets. */
    @ColumnInfo(name = "warmups_done", defaultValue = "0") val warmupsDone: Int = 0,
    /**
     * Its warm-ups as typed in this session ([com.hyperlog.app.domain.Warmups.encode]); null while
     * they follow the ramp. They last for this session only.
     */
    val warmups: String? = null,
)

@Entity(
    tableName = "logged_set",
    foreignKeys = [
        ForeignKey(SessionExerciseEntity::class, ["id"], ["session_exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("session_exercise_id")],
)
data class LoggedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_exercise_id") val sessionExerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "full_reps") val fullReps: Int,
    @ColumnInfo(name = "partial_reps") val partialReps: Int,
    val rir: Int?,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
    /** Logged with a second ✓ past a weight check. */
    @ColumnInfo(defaultValue = "0") val kept: Boolean = false,
    /** The intensity technique it was done with, as [com.hyperlog.app.program.TechniqueJson]. */
    val technique: String? = null,
    /** The right side's reps of a split unilateral set ([fullReps] are the left's). */
    @ColumnInfo(name = "right_reps") val rightReps: Int? = null,
)

/** A drop, myo mini-set or iso hold of a logged set, in order. A hold's [reps] are its seconds. */
@Entity(
    tableName = "set_extension",
    foreignKeys = [ForeignKey(LoggedSetEntity::class, ["id"], ["logged_set_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("logged_set_id")],
)
data class SetExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "logged_set_id") val loggedSetId: Long,
    val position: Int,
    /** An [com.hyperlog.app.domain.Extension.Kind] name. */
    val kind: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
)

/**
 * A technique added to or changed on a set during the session (set [setIndex] of the exercise).
 * A null [technique] takes the program's technique off the set.
 */
@Entity(
    tableName = "set_technique",
    primaryKeys = ["session_exercise_id", "set_index"],
    foreignKeys = [ForeignKey(SessionExerciseEntity::class, ["id"], ["session_exercise_id"], onDelete = ForeignKey.CASCADE)],
)
data class SetTechniqueEntity(
    @ColumnInfo(name = "session_exercise_id") val sessionExerciseId: Long,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    val technique: String?,
)
