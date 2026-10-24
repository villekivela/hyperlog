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
        PlanChangeEntity::class, GymMissingExerciseEntity::class, BodyweightEntity::class, LiftGoalEntity::class,
        SettingEntity::class, UpdateCheckEntity::class, WorkoutEntity::class, WeekdayPlanEntity::class,
    ],
    version = 25,
)
abstract class HyperlogDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun exercises(): ExerciseDao
    abstract fun gyms(): GymDao
    abstract fun programs(): ProgramDao
    abstract fun planChanges(): PlanChangeDao
    abstract fun startingWeights(): StartingWeightDao
    abstract fun bodyweights(): BodyweightDao
    abstract fun liftGoals(): LiftGoalDao
    abstract fun settings(): SettingDao
    abstract fun workouts(): WorkoutDao
    abstract fun weekdayPlan(): WeekdayPlanDao

    companion object {
        @Volatile private var instance: HyperlogDatabase? = null

        fun get(context: Context): HyperlogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HyperlogDatabase::class.java, NAME)
                .addMigrations(*Migrations)
                .let(forTests)
                .build()
                .also { instance = it }
        }

        /**
         * Lets tests change how the shared database is built, e.g. to run Room on the main thread so
         * the Compose test clock waits for every query and write.
         */
        @VisibleForTesting
        @Volatile var forTests: (RoomDatabase.Builder<HyperlogDatabase>) -> RoomDatabase.Builder<HyperlogDatabase> = { it }

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
