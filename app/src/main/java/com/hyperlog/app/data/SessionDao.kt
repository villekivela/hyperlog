package com.hyperlog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SessionDao {
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

    @Query("UPDATE session_exercise SET equipment_item_id = :itemId WHERE id = :id")
    suspend fun setEquipmentItem(id: Long, itemId: Long?)
}
