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
)

/** A ladder every gym shares: the dumbbells and the barbell plates. */
@Entity(tableName = "shared_ladder")
data class SharedLadderEntity(
    @PrimaryKey val key: String,
    val ladder: String,
)

@Dao
interface GymDao {
    @Query("SELECT * FROM gym ORDER BY name COLLATE NOCASE, id")
    fun observeGyms(): Flow<List<GymEntity>>

    @Query("SELECT * FROM equipment_item ORDER BY name COLLATE NOCASE, id")
    fun observeItems(): Flow<List<EquipmentItemEntity>>

    @Insert
    suspend fun insertGym(gym: GymEntity): Long

    @Query("UPDATE gym SET name = :name WHERE id = :id")
    suspend fun renameGym(id: Long, name: String)

    @Query("DELETE FROM gym WHERE id = :id")
    suspend fun deleteGym(id: Long)

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
