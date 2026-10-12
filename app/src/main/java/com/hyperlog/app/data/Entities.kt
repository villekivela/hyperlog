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
)

/** A session. One with no [finishedAt] is running; there is at most one. */
@Entity(tableName = "session")
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
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"]),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
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
    val rir: Int,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
)
