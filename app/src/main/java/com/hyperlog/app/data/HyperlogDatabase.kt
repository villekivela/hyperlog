package com.hyperlog.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class, SessionEntity::class, SessionExerciseEntity::class, LoggedSetEntity::class,
        GymEntity::class, EquipmentItemEntity::class, SharedLadderEntity::class, GymExerciseItemEntity::class,
        ProgramEntity::class, StartingWeightEntity::class, SetExtensionEntity::class, SetTechniqueEntity::class,
    ],
    version = 10,
)
abstract class HyperlogDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun exercises(): ExerciseDao
    abstract fun gyms(): GymDao
    abstract fun programs(): ProgramDao
    abstract fun startingWeights(): StartingWeightDao

    companion object {
        @Volatile private var instance: HyperlogDatabase? = null

        fun get(context: Context): HyperlogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HyperlogDatabase::class.java, NAME)
                .addMigrations(*Migrations)
                .build()
                .also { instance = it }
        }

        /**
         * Deletes the shared database, so the next [get] starts from an empty one. It deletes the
         * file the open instance uses: under Robolectric that can sit in an earlier test's data
         * directory.
         */
        @VisibleForTesting
        fun deleteForTests() = synchronized(this) {
            val open = instance ?: return@synchronized
            val file = open.openHelper.writableDatabase.path?.let(::File)
            open.close()
            instance = null
            file?.let { SQLiteDatabase.deleteDatabase(it) }
        }

        private const val NAME = "hyperlog.db"
    }
}
