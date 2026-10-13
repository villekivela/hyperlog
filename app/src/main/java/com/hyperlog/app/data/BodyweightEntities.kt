package com.hyperlog.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Bodyweight on one day ([day] is the epoch day): one value a day, a later one replacing it. */
@Entity(tableName = "bodyweight")
data class BodyweightEntity(
    @PrimaryKey val day: Long,
    val kg: Double,
)

@Dao
interface BodyweightDao {
    @Query("SELECT * FROM bodyweight ORDER BY day DESC")
    fun observe(): Flow<List<BodyweightEntity>>

    @Query("SELECT * FROM bodyweight ORDER BY day DESC")
    suspend fun all(): List<BodyweightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: BodyweightEntity)

    @Query("DELETE FROM bodyweight WHERE day = :day")
    suspend fun delete(day: Long)
}
