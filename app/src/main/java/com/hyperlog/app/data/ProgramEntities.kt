package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A program the app holds (SPEC, Program → Programs): the file as imported (it is parsed again when
 * read) and its position. One program is [active] at a time; starting another keeps the old row for
 * its sessions, [endedAt] set. A row never [startedAt] is a program saved for later, with the
 * [newExercises] it adds to the library when it starts.
 */
@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** The program file's JSON, as imported. */
    val source: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    /** The next day's week, from 1; past the last week once the program is finished. */
    val week: Int = 1,
    /** The next day's index in its week's block. */
    @ColumnInfo(name = "day_index") val dayIndex: Int = 0,
    val active: Boolean = true,
    /** When it started running; null for a program saved for later. */
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    /** When another program replaced it; null while it runs or waits. */
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    /** A saved program's new exercises, as `newExercises` in Claude's reply; null with none. */
    @ColumnInfo(name = "new_exercises") val newExercises: String? = null,
)

@Dao
interface ProgramDao {
    @Query("SELECT * FROM program WHERE active = 1 ORDER BY id DESC LIMIT 1")
    fun activeFlow(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE active = 1 ORDER BY id DESC LIMIT 1")
    suspend fun active(): ProgramEntity?

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun program(id: Long): ProgramEntity?

    @Insert
    suspend fun insert(program: ProgramEntity): Long

    /** Every program held, newest first. */
    @Query("SELECT * FROM program ORDER BY id DESC")
    fun allFlow(): Flow<List<ProgramEntity>>

    /** The program running stops at [at], replaced. */
    @Query("UPDATE program SET active = 0, ended_at = :at WHERE active = 1")
    suspend fun deactivateAll(at: Long)

    /** Saved program [id] starts at [at], from week 1, day 1; its new exercises are in the library by then. */
    @Query("UPDATE program SET active = 1, started_at = :at, week = 1, day_index = 0, new_exercises = NULL WHERE id = :id")
    suspend fun start(id: Long, at: Long)

    /** Drops program [id] if it is saved for later: one that has run keeps its sessions and Changes. */
    @Query("DELETE FROM program WHERE id = :id AND started_at IS NULL")
    suspend fun delete(id: Long): Int

    /** Moves the position from [week]/[day] to the next day, only if [week]/[day] is still the next day. */
    @Query("UPDATE program SET week = :nextWeek, day_index = :nextDay WHERE id = :id AND week = :week AND day_index = :day")
    suspend fun advance(id: Long, week: Int, day: Int, nextWeek: Int, nextDay: Int)

    @Query("UPDATE program SET week = :week, day_index = :day WHERE id = :id")
    suspend fun setPosition(id: Long, week: Int, day: Int)
}

/**
 * An accepted Change (SPEC, Coaching), kept as history: its JSON (deload weeks resolved), when it
 * was accepted, the position it moved from and to, and the block it was accepted in. A reverted
 * Change stays, marked [revertedAt]. [checkIn] is the check-in that made it, as JSON; null for one
 * pasted or proposed in a session.
 */
@Entity(
    tableName = "plan_change",
    foreignKeys = [ForeignKey(ProgramEntity::class, ["id"], ["program_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("program_id")],
)
data class PlanChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "program_id") val programId: Long,
    val json: String,
    @ColumnInfo(name = "accepted_at") val acceptedAt: Long,
    /** The day it was accepted on, as an epoch day. */
    @ColumnInfo(name = "accepted_on") val acceptedOn: Long,
    @ColumnInfo(name = "week_before") val weekBefore: Int,
    @ColumnInfo(name = "day_before") val dayBefore: Int,
    @ColumnInfo(name = "week_after") val weekAfter: Int,
    @ColumnInfo(name = "day_after") val dayAfter: Int,
    @ColumnInfo(name = "block_id") val blockId: String?,
    @ColumnInfo(name = "reverted_at") val revertedAt: Long? = null,
    @ColumnInfo(name = "check_in") val checkIn: String? = null,
)

@Dao
interface PlanChangeDao {
    /** [programId]'s Changes, oldest first, reverted ones included. */
    @Query("SELECT * FROM plan_change WHERE program_id = :programId ORDER BY id")
    fun changesFlow(programId: Long): Flow<List<PlanChangeEntity>>

    @Query("SELECT * FROM plan_change WHERE program_id = :programId ORDER BY id")
    suspend fun changes(programId: Long): List<PlanChangeEntity>

    @Query("SELECT * FROM plan_change WHERE id = :id")
    suspend fun change(id: Long): PlanChangeEntity?

    @Insert
    suspend fun insert(change: PlanChangeEntity): Long

    @Query("UPDATE plan_change SET reverted_at = :at WHERE id = :id AND reverted_at IS NULL")
    suspend fun revert(id: Long, at: Long): Int
}

/**
 * A workout from Claude (SPEC, Coaching → Workout): its JSON as [com.hyperlog.app.coach.WorkoutJson]
 * writes it, the day it was added for, and the gym picked on Coach. It waits on Today that day
 * until a session trains it; that session keeps it, for History.
 */
@Entity(
    tableName = "workout",
    foreignKeys = [ForeignKey(GymEntity::class, ["id"], ["gym_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("gym_id")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val json: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    /** The day it was added, as an epoch day: Today shows it on that day only. */
    @ColumnInfo(name = "added_on") val addedOn: Long,
    /** The gym picked on Coach, which the start sheet picks; null for Elsewhere or with no gyms. */
    @ColumnInfo(name = "gym_id") val gymId: Long? = null,
)

@Dao
interface WorkoutDao {
    /** The workouts no session has trained yet, newest first. */
    @Query("SELECT * FROM workout w WHERE NOT EXISTS (SELECT 1 FROM session s WHERE s.workout_id = w.id) ORDER BY id DESC")
    fun waitingFlow(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    /** Drops workout [id] unless a session trains it. */
    @Query("DELETE FROM workout WHERE id = :id AND NOT EXISTS (SELECT 1 FROM session s WHERE s.workout_id = workout.id)")
    suspend fun remove(id: Long)

    /** Drops every workout no session has trained. */
    @Query("DELETE FROM workout WHERE NOT EXISTS (SELECT 1 FROM session s WHERE s.workout_id = workout.id)")
    suspend fun removeWaiting()
}
