package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A training day program [programId] missed on epoch day [date], with the day it used up, kept
 * when the rotation is set again from a later day (days moved, a Change moving the position, the
 * program replaced), which would otherwise no longer reach it (ADR 0006).
 */
@Entity(
    tableName = "missed_day",
    primaryKeys = ["program_id", "date"],
    foreignKeys = [ForeignKey(ProgramEntity::class, ["id"], ["program_id"], onDelete = ForeignKey.CASCADE)],
)
data class MissedDayEntity(
    @ColumnInfo(name = "program_id") val programId: Long,
    val date: Long,
    val week: Int,
    @ColumnInfo(name = "day_index") val dayIndex: Int,
)

@Dao
interface MissedDayDao {
    @Query("SELECT * FROM missed_day WHERE program_id = :programId ORDER BY date")
    fun flow(programId: Long): Flow<List<MissedDayEntity>>

    @Query("SELECT * FROM missed_day WHERE program_id = :programId ORDER BY date")
    suspend fun all(programId: Long): List<MissedDayEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(days: List<MissedDayEntity>)
}
