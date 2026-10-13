package com.hyperlog.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A preference the user set, by [key]; backed up with everything else. Keys are listed where they're read. */
@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * The last read of the latest release (one row): when, and the release it named (null [version]
 * for none). A cache of GitHub's answer, so backups leave it out.
 */
@Entity(tableName = "update_check")
data class UpdateCheckEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
    val version: String?,
    @ColumnInfo(name = "apk_url") val apkUrl: String?,
)

@Dao
interface SettingDao {
    @Query("SELECT value FROM setting WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("SELECT * FROM update_check WHERE id = 0")
    suspend fun lastCheck(): UpdateCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLastCheck(check: UpdateCheckEntity)

    @Query("DELETE FROM update_check")
    suspend fun forgetLastCheck()
}
