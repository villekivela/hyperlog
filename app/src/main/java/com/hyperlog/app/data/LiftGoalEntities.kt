package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A lift goal: a target [kg] for one exercise's estimate, optionally by [byDay] (the epoch day).
 * One goal an exercise, a later one replacing it. Progression never reads this table.
 */
@Entity(tableName = "lift_goal")
data class LiftGoalEntity(
    @PrimaryKey @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val kg: Double,
    @ColumnInfo(name = "by_day") val byDay: Long?,
)

@Dao
interface LiftGoalDao {
    @Query("SELECT * FROM lift_goal ORDER BY exercise_id")
    fun observe(): Flow<List<LiftGoalEntity>>

    @Query("SELECT * FROM lift_goal WHERE exercise_id = :exerciseId")
    suspend fun get(exerciseId: String): LiftGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(goal: LiftGoalEntity)

    @Query("DELETE FROM lift_goal WHERE exercise_id = :exerciseId")
    suspend fun delete(exerciseId: String)
}
