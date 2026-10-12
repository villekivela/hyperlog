package com.hyperlog.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.MovementPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** An empty database at [version], from its exported schema. */
    private fun createVersion(version: Int, file: File, fill: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/com.hyperlog.app.data.HyperlogDatabase/$version.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            fill(db)
        }
    }

    /** A v1 database as ticket #2 left it. */
    private fun createVersion1(file: File) = createVersion(1, file) { db ->
        db.execSQL("INSERT INTO exercise VALUES ('db_bench_press', 'DB Bench Press', 'Dumbbell'), ('pull_up', 'Pull-up', 'Bodyweight')")
        db.execSQL("INSERT INTO session (id, started_at, finished_at, rest_total_ms, pad_visible) VALUES (1, 1000, 2000, 0, 1)")
        db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position) VALUES (1, 1, 'db_bench_press', 0), (2, 1, 'pull_up', 1)")
        db.execSQL(
            "INSERT INTO logged_set (session_exercise_id, position, weight_kg, full_reps, partial_reps, rir, logged_at) " +
                "VALUES (2, 0, 0, 8, 0, 2, 1500)",
        )
    }

    @Test
    fun keepsPastSessionsAndMovesStarterIdsToTheLibrary() = runTest {
        val file = context.getDatabasePath("migration-test.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion1(file)

        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val library = ExerciseLibrary(db)
        library.sync(LibrarySeed.fromAssets(context))

        val exercises = db.sessions().sessionExercises(1)
        assertEquals(listOf("db_bench_press", "pullup_wide_grip"), exercises.map { it.exerciseId })
        assertEquals(8, db.sessions().sets(2).single().fullReps)

        val all = library.exercises.first().associateBy { it.id }
        assertEquals(173, all.size)
        assertTrue("pull_up" !in all)
        val pullUp = all.getValue("pullup_wide_grip")
        assertEquals("Wide-Grip Pull-Up", pullUp.name)
        assertEquals(MovementPattern.VerticalPull, pullUp.pattern)
        assertEquals(GripDemand.High, pullUp.grip)
        db.close()
    }

    @Test
    fun version2GainsGymsWithTheSpecDumbbellsAndPlates() = runTest {
        val file = context.getDatabasePath("migration-test-2.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(2, file) { db ->
            db.execSQL("INSERT INTO session (id, started_at, rest_total_ms, pad_visible) VALUES (1, 1000, 0, 1)")
        }

        // Room checks the migrated tables against the v3 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val gyms = Gyms(db)
        assertEquals(LadderSpec.DefaultDumbbells, gyms.dumbbells.first())
        val id = gyms.addGym("Kallio")
        gyms.saveItem(EquipmentItem(gymId = id, name = "Cable station", ladder = LadderSpec.lbStack()))
        assertEquals("Cable station", gyms.gyms.first().single().items.single().name)
        assertEquals(1000L, db.sessions().session(1)!!.startedAt)
        db.close()
    }
}
