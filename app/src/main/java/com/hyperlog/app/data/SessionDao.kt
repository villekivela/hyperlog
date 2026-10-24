package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** An exercise done in an earlier session, and the program day that session trained (if any). */
data class PastExercise(
    val id: Long,
    @ColumnInfo(name = "slot_id") val slotId: String?,
    @ColumnInfo(name = "program_id") val programId: Long?,
    val week: Int?,
    @ColumnInfo(name = "day_index") val dayIndex: Int?,
    /** Its sets as edited in that session; null when they were the program's. */
    val sets: String? = null,
    /** When its session started: the plan is read for that date. */
    @ColumnInfo(name = "started_at") val startedAt: Long = 0,
    /** When its session finished (null while it runs): the Changes in force then are its plan's. */
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    /** The workout its session trained, outside the program; null otherwise. */
    @ColumnInfo(name = "workout_id") val workoutId: Long? = null,
)

/** An exercise as done in a finished session, for History. */
data class TrainedExercise(
    val id: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "equipment_item_id") val equipmentItemId: Long?,
    @ColumnInfo(name = "slot_id") val slotId: String?,
    @ColumnInfo(name = "program_id") val programId: Long?,
    val week: Int?,
    @ColumnInfo(name = "day_index") val dayIndex: Int?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

/** A logged set of an exercise from an earlier session, with when that session started. */
data class EarlierSet(
    @Embedded val set: LoggedSetEntity,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

@Dao
interface SessionDao {
    /**
     * Every exercise done in a finished session with sets logged, oldest first; only [exerciseId]
     * when given.
     */
    @Query(
        """
        SELECT se.id, se.session_id, se.exercise_id, se.equipment_item_id, se.slot_id, s.program_id, s.week, s.day_index, s.started_at
        FROM session_exercise se JOIN session s ON s.id = se.session_id
        WHERE s.finished_at IS NOT NULL AND se.removed = 0 AND (:exerciseId IS NULL OR se.exercise_id = :exerciseId)
          AND EXISTS (SELECT 1 FROM logged_set x WHERE x.session_exercise_id = se.id)
        ORDER BY s.started_at, se.id
        """,
    )
    suspend fun trained(exerciseId: String? = null): List<TrainedExercise>

    @Query("SELECT * FROM session WHERE finished_at IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun runningSession(): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long

    @Query("SELECT * FROM session_exercise WHERE session_id = :sessionId ORDER BY position")
    suspend fun sessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Insert
    suspend fun insertSet(set: LoggedSetEntity): Long

    @Query("SELECT * FROM logged_set WHERE session_exercise_id = :sessionExerciseId ORDER BY position")
    suspend fun sets(sessionExerciseId: Long): List<LoggedSetEntity>

    /** Set [index] of [sessionExerciseId] (its position); null while it isn't logged. */
    @Query("SELECT * FROM logged_set WHERE session_exercise_id = :sessionExerciseId AND position = :index")
    suspend fun setAt(sessionExerciseId: Long, index: Int): LoggedSetEntity?

    /** The sets of [exerciseId] from the latest other session in which any were logged. */
    @Query(
        """
        SELECT * FROM logged_set WHERE session_exercise_id = (
            SELECT se.id FROM session_exercise se JOIN session s ON s.id = se.session_id
            WHERE se.exercise_id = :exerciseId AND se.session_id != :sessionId
              AND EXISTS (SELECT 1 FROM logged_set x WHERE x.session_exercise_id = se.id)
            ORDER BY s.started_at DESC, se.id DESC LIMIT 1
        ) ORDER BY position
        """,
    )
    suspend fun lastTime(exerciseId: String, sessionId: Long): List<LoggedSetEntity>

    /**
     * Like [lastTime], but only sessions where it was done on equipment item [itemId]: machines and
     * cables keep a history per item (ADR 0002). A null [itemId] matches sets with no known item.
     */
    @Query(
        """
        SELECT * FROM logged_set WHERE session_exercise_id = (
            SELECT se.id FROM session_exercise se JOIN session s ON s.id = se.session_id
            WHERE se.exercise_id = :exerciseId AND se.session_id != :sessionId
              AND se.equipment_item_id IS :itemId
              AND EXISTS (SELECT 1 FROM logged_set x WHERE x.session_exercise_id = se.id)
            ORDER BY s.started_at DESC, se.id DESC LIMIT 1
        ) ORDER BY position
        """,
    )
    suspend fun lastTimeOn(exerciseId: String, sessionId: Long, itemId: Long?): List<LoggedSetEntity>

    /**
     * The latest [limit] other sessions with sets of [exerciseId], newest first, with the program
     * day each trained. With [anyItem] false only those on equipment item [itemId] (ADR 0002).
     */
    @Query(
        """
        SELECT se.id, se.slot_id, s.program_id, s.week, s.day_index, se.sets, s.started_at, s.finished_at, s.workout_id
        FROM session_exercise se JOIN session s ON s.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND se.session_id != :sessionId
          AND (:anyItem OR se.equipment_item_id IS :itemId)
          AND EXISTS (SELECT 1 FROM logged_set x WHERE x.session_exercise_id = se.id)
        ORDER BY s.started_at DESC, se.id DESC LIMIT :limit
        """,
    )
    suspend fun history(exerciseId: String, sessionId: Long, anyItem: Boolean, itemId: Long?, limit: Int): List<PastExercise>

    /** The technique logged set [id] is done with, as [com.hyperlog.app.program.TechniqueJson]. */
    @Query("UPDATE logged_set SET technique = :technique WHERE id = :id")
    suspend fun setLoggedTechnique(id: Long, technique: String)

    @Insert
    suspend fun insertExtension(extension: SetExtensionEntity): Long

    @Query("SELECT * FROM set_extension WHERE logged_set_id = :loggedSetId ORDER BY position")
    suspend fun extensions(loggedSetId: Long): List<SetExtensionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTechnique(technique: SetTechniqueEntity)

    @Query("SELECT * FROM set_technique WHERE session_exercise_id = :sessionExerciseId")
    suspend fun techniques(sessionExerciseId: Long): List<SetTechniqueEntity>

    @Query("UPDATE session_exercise SET equipment_item_id = :itemId WHERE id = :id")
    suspend fun setEquipmentItem(id: Long, itemId: Long?)

    @Query("UPDATE session_exercise SET exercise_id = :exerciseId, equipment_item_id = NULL, sets = NULL, warmups_done = 0, warmups = NULL WHERE id = :id")
    suspend fun setExercise(id: Long, exerciseId: String)

    @Query("DELETE FROM set_technique WHERE session_exercise_id = :sessionExerciseId")
    suspend fun clearTechniques(sessionExerciseId: Long)

    @Query("DELETE FROM set_technique WHERE session_exercise_id = :sessionExerciseId AND set_index = :setIndex")
    suspend fun clearTechnique(sessionExerciseId: Long, setIndex: Int)

    /** Set [setIndex] is gone: the session's techniques on the sets after it move up one. */
    @Query("UPDATE set_technique SET set_index = set_index - 1 WHERE session_exercise_id = :sessionExerciseId AND set_index > :setIndex")
    suspend fun shiftTechniques(sessionExerciseId: Long, setIndex: Int)

    @Query("UPDATE session_exercise SET sets = :sets WHERE id = :id")
    suspend fun setSets(id: Long, sets: String?)

    @Query("UPDATE session_exercise SET warmups_done = :done WHERE id = :id")
    suspend fun setWarmupsDone(id: Long, done: Int)

    /** The warm-ups typed by hand ([com.hyperlog.app.domain.Warmups.encode]), and how many are ticked off. */
    @Query("UPDATE session_exercise SET warmups = :warmups, warmups_done = :done WHERE id = :id")
    suspend fun setWarmups(id: Long, warmups: String?, done: Int)

    @Query("DELETE FROM logged_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE session_exercise SET removed = 1 WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("UPDATE session_exercise SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Corrects a logged set's values. A corrected weight is typed on purpose, so it's no longer a kept one. */
    @Query(
        """
        UPDATE logged_set SET weight_kg = :weightKg, full_reps = :fullReps, partial_reps = :partialReps,
            rir = :rir, right_reps = :rightReps, kept = 0
        WHERE id = :id
        """,
    )
    suspend fun correctSet(id: Long, weightKg: Double, fullReps: Int, partialReps: Int, rir: Int?, rightReps: Int?)

    @Query("UPDATE logged_set SET kept = 1 WHERE id = :id")
    suspend fun setKept(id: Long)

    /** Mini-sets and a hold are done at their set's weight, so they follow a corrected one. */
    @Query("UPDATE set_extension SET weight_kg = :weightKg WHERE logged_set_id = :loggedSetId AND kind IN ('MiniSet', 'Hold')")
    suspend fun moveSetWeight(loggedSetId: Long, weightKg: Double)

    @Query("UPDATE set_extension SET weight_kg = :weightKg, reps = :reps WHERE id = :id")
    suspend fun correctExtension(id: Long, weightKg: Double, reps: Int)

    /** When the last finished session started; null with none. The coach counts time off from it. */
    @Query("SELECT MAX(started_at) FROM session WHERE finished_at IS NOT NULL")
    suspend fun lastTrained(): Long?

    /** Every finished session, newest first. */
    @Query("SELECT * FROM session WHERE finished_at IS NOT NULL ORDER BY started_at DESC, id DESC")
    suspend fun finishedSessions(): List<SessionEntity>

    /**
     * The sets of [exerciseId] from the latest session before [before] in which any were logged:
     * last time, as a past session saw it. With [anyItem] false only those on equipment item [itemId].
     */
    @Query(
        """
        SELECT * FROM logged_set WHERE session_exercise_id = (
            SELECT se.id FROM session_exercise se JOIN session s ON s.id = se.session_id
            WHERE se.exercise_id = :exerciseId AND se.session_id != :sessionId AND s.started_at < :before
              AND (:anyItem OR se.equipment_item_id IS :itemId)
              AND EXISTS (SELECT 1 FROM logged_set x WHERE x.session_exercise_id = se.id)
            ORDER BY s.started_at DESC, se.id DESC LIMIT 1
        ) ORDER BY position
        """,
    )
    suspend fun lastTimeBefore(exerciseId: String, sessionId: Long, before: Long, anyItem: Boolean, itemId: Long?): List<LoggedSetEntity>

    /**
     * Every set of [exerciseId] logged in finished sessions that started before session [sessionId], which
     * started at [before], oldest first. With [anyItem] false only those on equipment item [itemId]
     * (ADR 0002). Records are measured against them.
     */
    @Query(
        """
        SELECT ls.*, s.started_at FROM logged_set ls
        JOIN session_exercise se ON se.id = ls.session_exercise_id JOIN session s ON s.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND s.id != :sessionId AND s.finished_at IS NOT NULL
          AND (s.started_at < :before OR (s.started_at = :before AND s.id < :sessionId))
          AND (:anyItem OR se.equipment_item_id IS :itemId)
        ORDER BY s.started_at, s.id, se.id, ls.position
        """,
    )
    suspend fun setsBefore(exerciseId: String, sessionId: Long, before: Long, anyItem: Boolean, itemId: Long?): List<EarlierSet>
}
