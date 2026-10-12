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
 * An imported program: the file as imported (it is parsed again when read) and its position.
 * One program is [active] at a time; importing another keeps the old row for its sessions.
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

    @Query("UPDATE program SET active = 0 WHERE active = 1")
    suspend fun deactivateAll()

    /** Moves the position from [week]/[day] to the next day, only if [week]/[day] is still the next day. */
    @Query("UPDATE program SET week = :nextWeek, day_index = :nextDay WHERE id = :id AND week = :week AND day_index = :day")
    suspend fun advance(id: Long, week: Int, day: Int, nextWeek: Int, nextDay: Int)

    @Query("UPDATE program SET week = :week, day_index = :day WHERE id = :id")
    suspend fun setPosition(id: Long, week: Int, day: Int)
}

/**
 * An accepted Change (SPEC, Coaching), kept as history: its JSON (deload weeks resolved), when it
 * was accepted, the position it moved from and to, and the block it was accepted in. A reverted
 * Change stays, marked [revertedAt].
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
