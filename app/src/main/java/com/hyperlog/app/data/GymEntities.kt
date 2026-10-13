package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "gym")
data class GymEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The weekday plan: bit 0 is Monday. Starting a session on one of these days picks this gym. */
    @ColumnInfo(defaultValue = "0") val weekdays: Int = 0,
    /** Deleted after sessions were trained at it: hidden everywhere but in the history it keeps. */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
)

/** One equipment item at a gym. [ladder] is an encoded [com.hyperlog.app.domain.LadderSpec]. */
@Entity(
    tableName = "equipment_item",
    foreignKeys = [ForeignKey(GymEntity::class, ["id"], ["gym_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gym_id")],
)
data class EquipmentItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "gym_id") val gymId: Long,
    val name: String,
    val ladder: String,
    /**
     * Deleted after sets were logged on it: hidden from setup and "Which machine?", but kept so
     * its sessions stay its own history (ADR 0002) instead of joining another pool.
     */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
)

/** The machine or cable station an exercise is done on at a gym, remembered from the first time. */
@Entity(
    tableName = "gym_exercise_item",
    primaryKeys = ["gym_id", "exercise_id"],
    foreignKeys = [
        ForeignKey(GymEntity::class, ["id"], ["gym_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"]),
        ForeignKey(EquipmentItemEntity::class, ["id"], ["equipment_item_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exercise_id"), Index("equipment_item_id")],
)
data class GymExerciseItemEntity(
    @ColumnInfo(name = "gym_id") val gymId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "equipment_item_id") val equipmentItemId: Long,
)

/** An exercise a gym doesn't have the equipment for: picking the gym proposes a swap for it. */
@Entity(
    tableName = "gym_missing_exercise",
    primaryKeys = ["gym_id", "exercise_id"],
    foreignKeys = [
        ForeignKey(GymEntity::class, ["id"], ["gym_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"]),
    ],
    indices = [Index("exercise_id")],
)
data class GymMissingExerciseEntity(
    @ColumnInfo(name = "gym_id") val gymId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
)

/** A session's gym and start, for working out the usual gym on a weekday. */
data class Visit(
    @ColumnInfo(name = "gym_id") val gymId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

/** A machine's name and its gym's, as a history line is headed. */
data class MachineName(val id: Long, val name: String, val gym: String)

/** A ladder every gym shares: the dumbbells and the barbell plates. */
@Entity(tableName = "shared_ladder")
data class SharedLadderEntity(
    @PrimaryKey val key: String,
    val ladder: String,
)

@Dao
interface GymDao {
    @Query("SELECT * FROM gym WHERE archived = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeGyms(): Flow<List<GymEntity>>

    @Query("SELECT * FROM equipment_item WHERE archived = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeItems(): Flow<List<EquipmentItemEntity>>

    /** Every machine with its gym's name, deleted ones included, for the history they keep. */
    @Query("SELECT i.id, i.name, g.name AS gym FROM equipment_item i JOIN gym g ON g.id = i.gym_id")
    suspend fun machineNames(): List<MachineName>

    @Insert
    suspend fun insertGym(gym: GymEntity): Long

    @Query("UPDATE gym SET name = :name WHERE id = :id")
    suspend fun renameGym(id: Long, name: String)

    @Query("UPDATE gym SET weekdays = :weekdays WHERE id = :id")
    suspend fun setWeekdays(id: Long, weekdays: Int)

    @Query("SELECT gym_id, started_at FROM session WHERE gym_id IS NOT NULL")
    fun observeVisits(): Flow<List<Visit>>

    @Query("SELECT gym_id, started_at FROM session WHERE gym_id IS NOT NULL")
    suspend fun visits(): List<Visit>

    @Query("SELECT * FROM gym WHERE id = :id")
    suspend fun gym(id: Long): GymEntity?

    @Query("SELECT * FROM equipment_item WHERE gym_id = :gymId ORDER BY name COLLATE NOCASE, id")
    suspend fun items(gymId: Long): List<EquipmentItemEntity>

    @Query("SELECT equipment_item_id FROM gym_exercise_item WHERE gym_id = :gymId AND exercise_id = :exerciseId")
    suspend fun rememberedItem(gymId: Long, exerciseId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun remember(choice: GymExerciseItemEntity)

    @Query("SELECT * FROM gym_missing_exercise")
    fun observeMissing(): Flow<List<GymMissingExerciseEntity>>

    @Query("SELECT exercise_id FROM gym_missing_exercise WHERE gym_id = :gymId")
    suspend fun missing(gymId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markMissing(missing: GymMissingExerciseEntity)

    @Query("DELETE FROM gym_missing_exercise WHERE gym_id = :gymId AND exercise_id = :exerciseId")
    suspend fun unmarkMissing(gymId: Long, exerciseId: String)

    @Query("DELETE FROM gym WHERE id = :id")
    suspend fun deleteGym(id: Long)

    @Query("UPDATE gym SET archived = 1 WHERE id = :id")
    suspend fun archiveGym(id: Long)

    @Query("SELECT EXISTS (SELECT 1 FROM session WHERE gym_id = :id)")
    suspend fun gymUsed(id: Long): Boolean

    @Query("SELECT EXISTS (SELECT 1 FROM session_exercise WHERE equipment_item_id = :id)")
    suspend fun itemUsed(id: Long): Boolean

    @Query("UPDATE equipment_item SET archived = 1 WHERE id = :id")
    suspend fun archiveItem(id: Long)

    @Query("DELETE FROM gym_exercise_item WHERE equipment_item_id = :id")
    suspend fun forgetItem(id: Long)

    @Upsert
    suspend fun upsertItem(item: EquipmentItemEntity): Long

    @Query("DELETE FROM equipment_item WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM shared_ladder")
    fun observeShared(): Flow<List<SharedLadderEntity>>

    @Query("SELECT * FROM shared_ladder")
    suspend fun shared(): List<SharedLadderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setShared(ladder: SharedLadderEntity)
}
