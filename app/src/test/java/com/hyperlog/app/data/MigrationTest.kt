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
        assertEquals(176, all.size)
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
        val id = gyms.addGym("Central")
        assertEquals(LadderSpec.DefaultDumbbells, gyms.gym(id)!!.dumbbells)
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
            db.execSQL("INSERT INTO gym (id, name) VALUES (1, 'Central')")
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
            db.execSQL("INSERT INTO gym (id, name, weekdays) VALUES (1, 'Harbour', 0)")
        }

        // Room checks the migrated tables against the v12 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val gym = com.hyperlog.app.gym.Gyms(db).gym(1)!!
        assertEquals("Harbour", gym.name)
        assertEquals(emptySet<String>(), gym.missing)
        db.close()
    }

    @Test
    fun version12StartsWithNoBodyweight() = runTest {
        val file = context.getDatabasePath("migration-test-12.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(12, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays) VALUES (1, 'Harbour', 0)")
        }

        // Room checks the migrated tables against the v13 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(null, com.hyperlog.app.history.Bodyweights(db).latest())
        assertEquals("Harbour", com.hyperlog.app.gym.Gyms(db).gym(1)!!.name)
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
            db.execSQL("INSERT INTO gym (id, name, weekdays) VALUES (1, 'Harbour', 0)")
            db.execSQL("INSERT INTO equipment_item (id, gym_id, name, ladder) VALUES (1, 1, 'Cable left', 'list;5,10')")
        }

        // Room checks the migrated tables against the v15 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(listOf("Cable left"), Gyms(db).gyms.first().single().items.map { it.name })
        assertEquals(mapOf(1L to "Cable left · Harbour"), Gyms(db).machineNames())
        db.close()
    }

    @Test
    fun version15MarksTheShippedTwoDumbbellExercisesEditedOrNot() = runTest {
        val file = context.getDatabasePath("migration-test-15.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(15, file) { db ->
            db.execSQL(
                "INSERT INTO exercise (id, name, equipment, movement_pattern, grip, muscles, secondary_muscles, hidden, edited) VALUES " +
                    "('db_bench_press', 'DB Bench Press', 'Dumbbell', 'HorizontalPush', 'High', 'Chest', '', 0, 1), " +
                    "('goblet_squat', 'Goblet Squat', 'Dumbbell', 'Squat', 'Low', 'Quads', '', 0, 0)",
            )
        }

        // Room checks the migrated tables against the v16 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val byId = db.exercises().all().associateBy { it.id }
        assertEquals(true, byId.getValue("db_bench_press").twoDumbbells)
        assertEquals(false, byId.getValue("goblet_squat").twoDumbbells)
        db.close()
    }

    @Test
    fun version16GetsSettingsWithUpdatesOn() = runTest {
        val file = context.getDatabasePath("migration-test-16.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(16, file) {}

        // Room checks the migrated tables against the v17 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val preferences = com.hyperlog.app.update.UpdatePreferences(db)
        assertEquals(true, preferences.checkForUpdates())
        preferences.setCheckForUpdates(false)
        assertEquals(false, preferences.checkForUpdates())
        db.close()
    }

    @Test
    fun version17DumbbellsEditedByHandGoToEveryGym() = runTest {
        val file = context.getDatabasePath("migration-test-17b.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(17, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays, archived) VALUES (1, 'Harbour', 0, 0), (2, 'Home', 0, 0)")
            db.execSQL("INSERT INTO shared_ladder (`key`, ladder) VALUES ('dumbbells', 'steps;Kg;2.0;2.0;20'), ('plates', 'plates;20.0;20.0,10.0')")
        }
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val gyms = Gyms(db).gyms.first()
        assertEquals(listOf(LadderSpec.Steps(2.0, 2.0, com.hyperlog.app.domain.WeightUnit.Kg, 20)), gyms.map { it.dumbbells }.distinct())
        assertEquals(2, gyms.size)
        // Later, v19 moves the edited plates to every gym too.
        assertEquals(listOf(listOf(20.0, 10.0)), gyms.map { it.plates }.distinct())
        db.close()
    }

    @Test
    fun version17UntouchedDumbbellsBecomeTheNewRack() = runTest {
        val file = context.getDatabasePath("migration-test-17c.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(17, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays, archived) VALUES (1, 'Harbour', 0, 0)")
            db.execSQL("INSERT INTO shared_ladder (`key`, ladder) VALUES ('dumbbells', '${LadderSpec.OldDefaultDumbbells.encode()}')")
        }
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(LadderSpec.DefaultDumbbells, Gyms(db).gyms.first().single().dumbbells)
        db.close()
    }

    @Test
    fun version17GymsGetTheDefaultPinPlates() = runTest {
        val file = context.getDatabasePath("migration-test-17.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(17, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays, archived) VALUES (1, 'Harbour', 0, 0)")
            db.execSQL("INSERT INTO equipment_item (id, gym_id, name, ladder, archived) VALUES (1, 1, 'Cable left', 'list;5,10', 0)")
        }

        // Room checks the migrated tables against the v18 schema when it opens.
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val gym = Gyms(db).gyms.first().single()
        assertEquals(LadderSpec.DefaultPinPlates, gym.pinPlates)
        assertEquals(listOf("Cable left"), gym.items.map { it.name })
        db.close()
    }

    @Test
    fun version18PlatesEditedByHandGoToEveryGymAndCopiesLoadTheGyms() = runTest {
        val file = context.getDatabasePath("migration-test-18a.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(18, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays, archived) VALUES (1, 'Harbour', 0, 0), (2, 'Home', 0, 0)")
            db.execSQL("INSERT INTO shared_ladder (`key`, ladder) VALUES ('plates', 'plates;15.0;20.0,20.0,10.0')")
            db.execSQL(
                "INSERT INTO equipment_item (id, gym_id, name, ladder) VALUES " +
                    "(1, 1, 'Leg press', 'plates;0.0;20.0,20.0,10.0'), (2, 1, 'Hack squat', 'plates;0.0;20.0,20.0,20.0,15.0,10.0,5.0,2.5,1.25'), " +
                    "(3, 1, 'Sled', 'plates;30.0;25.0,25.0')",
            )
        }
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val gyms = Gyms(db).gyms.first()
        assertEquals(listOf(listOf(20.0, 20.0, 10.0)), gyms.map { it.plates }.distinct())
        assertEquals(15.0, Gyms(db).barbell.first(), 0.0)
        val items = gyms.first { it.name == "Harbour" }.items.associate { it.name to it.ladder }
        // Copies of the shared or old default plates load the gym's; a machine's own plates stay.
        assertEquals(LadderSpec.Plates(0.0, emptyList()), items["Leg press"])
        assertEquals(LadderSpec.Plates(0.0, emptyList()), items["Hack squat"])
        assertEquals(LadderSpec.Plates(30.0, listOf(25.0, 25.0)), items["Sled"])
        db.close()
    }

    @Test
    fun version18UntouchedPlatesBecomeTheTypicalGyms() = runTest {
        val file = context.getDatabasePath("migration-test-18b.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(18, file) { db ->
            db.execSQL("INSERT INTO gym (id, name, weekdays, archived) VALUES (1, 'Harbour', 0, 0)")
            db.execSQL("INSERT INTO shared_ladder (`key`, ladder) VALUES ('plates', 'plates;20.0;20.0,20.0,20.0,15.0,10.0,5.0,2.5,1.25')")
        }
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(LadderSpec.DefaultGymPlates, Gyms(db).gyms.first().single().plates)
        db.close()
    }

    @Test
    fun version20SetsKeepEverythingAndMayNowLackAnRir() = runTest {
        val file = context.getDatabasePath("migration-test-20.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(20, file) { db ->
            db.execSQL("INSERT INTO exercise (id, name, equipment) VALUES ('pull_up', 'Pull-up', 'Bodyweight')")
            db.execSQL("INSERT INTO session (id, started_at, finished_at, rest_total_ms, pad_visible) VALUES (1, 1000, 2000, 0, 1)")
            db.execSQL("INSERT INTO session_exercise (id, session_id, exercise_id, position) VALUES (1, 1, 'pull_up', 0)")
            db.execSQL(
                "INSERT INTO logged_set (id, session_exercise_id, position, weight_kg, full_reps, partial_reps, rir, logged_at, kept, right_reps) " +
                    "VALUES (7, 1, 0, 0, 8, 0, 2, 1500, 1, 7)",
            )
            db.execSQL("INSERT INTO set_extension (logged_set_id, position, kind, weight_kg, reps) VALUES (7, 0, 'Drop', 0, 5)")
        }
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        val sets = db.openHelper.writableDatabase
        sets.query("SELECT id, full_reps, rir, kept, right_reps FROM logged_set").use {
            assertTrue(it.moveToFirst())
            assertEquals(listOf(7L, 8L, 2L, 1L, 7L), (0..4).map { c -> it.getLong(c) })
        }
        sets.query("SELECT COUNT(*) FROM set_extension").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        sets.execSQL("INSERT INTO logged_set (session_exercise_id, position, weight_kg, full_reps, partial_reps, rir, logged_at) VALUES (1, 1, 0, 6, 0, NULL, 1600)")
        db.close()
    }

    @Test
    fun version21SessionsTrainNoWorkoutAndWorkoutsCanBeAdded() = runTest {
        val file = context.getDatabasePath("migration-test-21.db").also { it.parentFile!!.mkdirs(); it.delete() }
        createVersion(21, file) { db ->
            db.execSQL("INSERT INTO session (id, started_at, finished_at, rest_total_ms, pad_visible) VALUES (1, 1000, 2000, 0, 1)")
        }
        val db = Room.databaseBuilder(context, HyperlogDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations)
            .allowMainThreadQueries()
            .build()
        assertEquals(null, db.sessions().session(1)!!.workoutId)
        val id = db.workouts().insert(WorkoutEntity(name = "Hotel", json = "{}", addedAt = 3000, addedOn = 20_000))
        assertEquals(listOf("Hotel"), db.workouts().waitingFlow().first().map { it.name })
        db.sessions().updateSession(db.sessions().session(1)!!.copy(workoutId = id))
        assertEquals(emptyList<WorkoutEntity>(), db.workouts().waitingFlow().first())
        db.close()
    }
}
