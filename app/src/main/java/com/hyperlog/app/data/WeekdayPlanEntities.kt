package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One weekday you train on (1 is Monday), and the gym you train at that day; null names none. */
@Entity(
    tableName = "weekday_plan",
    foreignKeys = [ForeignKey(GymEntity::class, ["id"], ["gym_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("gym_id")],
)
data class WeekdayPlanEntity(
    @PrimaryKey val weekday: Int,
    @ColumnInfo(name = "gym_id") val gymId: Long? = null,
)

@Dao
interface WeekdayPlanDao {
    @Query("SELECT * FROM weekday_plan ORDER BY weekday")
    fun observe(): Flow<List<WeekdayPlanEntity>>

    @Query("SELECT * FROM weekday_plan ORDER BY weekday")
    suspend fun all(): List<WeekdayPlanEntity>

    @Query("DELETE FROM weekday_plan")
    suspend fun clear()

    @Insert
    suspend fun insert(days: List<WeekdayPlanEntity>)

    @Transaction
    suspend fun replace(days: List<WeekdayPlanEntity>) {
        clear()
        insert(days)
    }

    /** A gym deleted or archived is no day's gym any more. */
    @Query("UPDATE weekday_plan SET gym_id = NULL WHERE gym_id = :gymId")
    suspend fun forgetGym(gymId: Long)
}
