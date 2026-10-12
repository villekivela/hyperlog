package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** An exercise's starting weight from the old app (SPEC, Fresh start). Not a session: no history. */
@Entity(tableName = "starting_weight")
data class StartingWeightEntity(
    @PrimaryKey @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    /** Null when the old set had no RPE. */
    val rir: Int?,
    /** When the old session was, epoch ms. */
    @ColumnInfo(name = "done_at") val doneAt: Long,
)

@Dao
interface StartingWeightDao {
    @Query("SELECT * FROM starting_weight WHERE exercise_id = :exerciseId")
    suspend fun get(exerciseId: String): StartingWeightEntity?

    @Query("SELECT COUNT(*) FROM starting_weight")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM starting_weight")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(weights: List<StartingWeightEntity>)

    /** Replaces an earlier import as a whole. */
    @Transaction
    suspend fun replaceAll(weights: List<StartingWeightEntity>) {
        deleteAll()
        insert(weights)
    }
}
