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

    @Test
    fun version3SessionsKeepNoGymAndGymsGainAnEmptyWeekdayPlan() = runTest {
        val file = context.getDatabasePath("migration-test-3.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(3, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('leg_extension', 'Leg Extension', 'Machine')")
            db.execSQL("INSERT INTO gym (id, name) VALUES (1, 'Kallio')")
            db.execSQL("INSERT INTO equipment_item (id, gym_id, name, ladder) VALUES (1, 1, 'Leg extension', 'steps;Lb;2.5;5.0;40')")
            db.execSQL("INSERT INTO session (id, started_at, finished_at, rest_total_ms, pad_visible) VALUES (1, 1000, 2000, 0, 1)")
            db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position) VALUES (1, 1, 'leg_extension', 0)")
        }

        // Room checks the migrated tables against the v4 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(null, db.sessions().session(1)!!.gymId)
        assertEquals(null, db.sessions().sessionExercises(1).single().equipmentItemId)
        val gyms = Gyms(db)
        assertEquals(emptySet<java.time.DayOfWeek>(), gyms.gyms.first().single().weekdays)
        gyms.remember(1, "leg_extension", 1)
        assertEquals(1L, gyms.remembered(1, "leg_extension"))
        // Deleting the machine forgets it.
        gyms.deleteItem(1)
        assertEquals(null, gyms.remembered(1, "leg_extension"))
        db.close()
    }

    @Test
    fun version4SessionsStayAdHocAndAProgramCanBeImported() = runTest {
        val file = context.getDatabasePath("migration-test-4.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(4, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('leg_extension', 'Leg Extension', 'Machine')")
            db.execSQL("INSERT INTO session (id, started_at, finished_at, rest_total_ms, pad_visible) VALUES (1, 1000, 2000, 0, 1)")
            db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position) VALUES (1, 1, 'leg_extension', 0)")
        }

        // Room checks the migrated tables against the v5 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(null, db.sessions().session(1)!!.programId)
        assertEquals(null, db.sessions().sessionExercises(1).single().slotId)
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
        val programs = com.hyperlog.app.program.Programs(db)
        assertTrue(programs.import(com.hyperlog.app.program.TestPrograms.twoDays("After v4")) is com.hyperlog.app.program.ProgramParser.Result.Valid)
        assertEquals("After v4", programs.active.first()!!.program.title)
        db.close()
    }

    @Test
    fun version5SetsWereNotKeptPastAWeightCheck() = runTest {
        val file = context.getDatabasePath("migration-test-5.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(5, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('leg_extension', 'Leg Extension', 'Machine')")
            db.execSQL("INSERT INTO session (id, started_at, finished_at, rest_total_ms, pad_visible) VALUES (1, 1000, 2000, 0, 1)")
            db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position) VALUES (1, 1, 'leg_extension', 0)")
            db.execSQL(
                "INSERT INTO logged_set (session_exercise_id, position, weight_kg, full_reps, partial_reps, rir, logged_at) " +
                    "VALUES (1, 0, 60, 12, 0, 1, 1500)",
            )
        }

        // Room checks the migrated tables against the v6 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(false, db.sessions().sets(1).single().kept)
        db.close()
    }

    @Test
    fun version6GainsEmptyStartingWeights() = runTest {
        val file = context.getDatabasePath("migration-test-6.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(6, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('leg_extension', 'Leg Extension', 'Machine')")
            db.execSQL("INSERT INTO session (id, started_at, finished_at, rest_total_ms, pad_visible) VALUES (1, 1000, 2000, 0, 1)")
        }

        // Room checks the migrated tables against the v7 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(0, db.startingWeights().observeCount().first())
        assertEquals(2000L, db.sessions().session(1)!!.finishedAt)
        db.close()
    }

    @Test
    fun version7SetsKeepNoTechniqueAndNothingOpen() = runTest {
        val file = context.getDatabasePath("migration-test-7.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(7, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('leg_extension', 'Leg Extension', 'Machine')")
            db.execSQL("INSERT INTO session (id, started_at, rest_total_ms, pad_visible, entry_weight, entry_full, entry_partial, entry_field, entry_partial_mode, entry_fresh) VALUES (1, 1000, 0, 1, '60', '12', '', 'Reps', 0, 1)")
            db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position) VALUES (1, 1, 'leg_extension', 0)")
            db.execSQL(
                "INSERT INTO logged_set (id, session_exercise_id, position, weight_kg, full_reps, partial_reps, rir, logged_at) " +
                    "VALUES (1, 1, 0, 60, 12, 0, 1, 1500)",
            )
        }

        // Room checks the migrated tables against the v8 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val set = db.sessions().sets(1).single()
        assertEquals(null, set.technique)
        assertEquals(null, set.rightReps)
        assertEquals(emptyList<SetExtensionEntity>(), db.sessions().extensions(1))
        val session = db.sessions().session(1)!!
        assertEquals(null, session.openSetId)
        assertEquals(false, session.breathing)
        assertEquals(null, session.entry!!.right)
        assertEquals("60", session.entry!!.weight)
        db.close()
    }

    @Test
    fun version8SessionExercisesKeepTheProgramsSetsAndStayIn() = runTest {
        val file = context.getDatabasePath("migration-test-8.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(8, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('leg_extension', 'Leg Extension', 'Machine')")
            db.execSQL("INSERT INTO session (id, started_at, rest_total_ms, pad_visible, breathing) VALUES (1, 1000, 0, 1, 0)")
            db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position, slot_id) VALUES (1, 1, 'leg_extension', 0, 'legs')")
        }

        // Room checks the migrated tables against the v9 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val exercise = db.sessions().sessionExercises(1).single()
        assertEquals(null, exercise.sets)
        assertEquals(false, exercise.removed)
        assertEquals("legs", exercise.slotId)
        db.close()
    }

    @Test
    fun version9SessionExercisesStartWithNoWarmUpsTicked() = runTest {
        val file = context.getDatabasePath("migration-test-9.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(9, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('barbell_bench_press', 'Barbell Bench Press', 'Barbell')")
            db.execSQL("INSERT INTO session (id, started_at, rest_total_ms, pad_visible, breathing) VALUES (1, 1000, 0, 1, 0)")
            db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position, removed) VALUES (1, 1, 'barbell_bench_press', 0, 0)")
        }

        // Room checks the migrated tables against the v10 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(0, db.sessions().sessionExercises(1).single().warmupsDone)
        db.close()
    }

    @Test
    fun version10ProgramsStartWithNoChanges() = runTest {
        val file = context.getDatabasePath("migration-test-10.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(10, file) { db ->
            db.execSQL("INSERT INTO program (id, title, source, imported_at, week, day_index, active) VALUES (1, 'P', '{}', 0, 3, 1, 1)")
        }

        // Room checks the migrated tables against the v11 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(emptyList<PlanChangeEntity>(), db.planChanges().changes(1))
        assertEquals(3, db.programs().program(1)!!.week)
        db.close()
    }

    @Test
    fun version11GymsStartWithNothingMissing() = runTest {
        val file = context.getDatabasePath("migration-test-11.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(11, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays) VALUES (1, 'Kamppi', 0)")
        }

        // Room checks the migrated tables against the v12 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val gym = com.hyperlog.app.gym.Gyms(db).gym(1)!!
        assertEquals("Kamppi", gym.name)
        assertEquals(emptySet<String>(), gym.missing)
        db.close()
    }

    @Test
    fun version12StartsWithNoBodyweight() = runTest {
        val file = context.getDatabasePath("migration-test-12.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(12, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays) VALUES (1, 'Kamppi', 0)")
        }

        // Room checks the migrated tables against the v13 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(null, com.hyperlog.app.history.Bodyweights(db).latest())
        assertEquals("Kamppi", com.hyperlog.app.gym.Gyms(db).gym(1)!!.name)
        db.close()
    }

    @Test
    fun version13StartsWithNoLiftGoals() = runTest {
        val file = context.getDatabasePath("migration-test-13.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(13, file) { db ->
            db.execSQL("INSERT INTO bodyweight (day, kg) VALUES (20000, 82.4)")
        }

        // Room checks the migrated tables against the v14 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(null, com.hyperlog.app.history.LiftGoals(db).of("barbell_bench_press"))
        assertEquals(82.4, com.hyperlog.app.history.Bodyweights(db).latest()!!, 0.001)
        db.close()
    }

    @Test
    fun version14KeepsEveryGymAndMachineInUse() = runTest {
        val file = context.getDatabasePath("migration-test-14.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(14, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays) VALUES (1, 'Kamppi', 0)")
            db.execSQL("INSERT INTO equipment_item (id, gym_id, name, ladder) VALUES (1, 1, 'Cable left', 'list;5,10')")
        }

        // Room checks the migrated tables against the v15 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(listOf("Cable left"), Gyms(db).gyms.first().single().items.map { it.name })
        assertEquals(mapOf(1L to "Cable left · Kamppi"), Gyms(db).machineNames())
        db.close()
    }
}
