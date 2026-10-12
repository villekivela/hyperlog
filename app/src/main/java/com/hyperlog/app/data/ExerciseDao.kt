package com.hyperlog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE, name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Insert
    suspend fun insert(exercises: List<ExerciseEntity>)

    @Update
    suspend fun update(exercises: List<ExerciseEntity>)

    @Query("UPDATE exercise SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)
}
