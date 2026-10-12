package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
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

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun program(id: Long): ProgramEntity?

    @Insert
    suspend fun insert(program: ProgramEntity): Long

    @Query("UPDATE program SET active = 0 WHERE active = 1")
    suspend fun deactivateAll()

    /** Moves the position from [week]/[day] to the next day, only if [week]/[day] is still the next day. */
    @Query("UPDATE program SET week = :nextWeek, day_index = :nextDay WHERE id = :id AND week = :week AND day_index = :day")
    suspend fun advance(id: Long, week: Int, day: Int, nextWeek: Int, nextDay: Int)
}
