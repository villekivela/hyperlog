package com.hyperlog.app.backup

import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.ChangeJson
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.StartingWeightEntity
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.history.LiftGoals
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.update.AppVersion
import com.hyperlog.app.update.UpdatePreferences
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Export, then restore on a fresh install, gives back everything exactly; a bad file changes nothing. */
@RunWith(RobolectricTestRunner::class)
class BackupsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val seed by lazy { LibrarySeed.fromAssets(context) }
    private var now = 1_760_000_000_000L
    private val open = mutableListOf<HyperlogDatabase>()

    private val program = """
        { "format": 1, "title": "Backup test", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 4,
            "days": [ { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 2 } ] } ] },
              { "slots": [ { "id": "lateral", "exercise": "cable_lateral_raise", "sets": [ { "count": 2, "reps": "10-12", "rir": 0 } ] } ] } ] } ] } ] }
    """

    private val yRaise = Exercise(
        "kneeling_cable_y_raise", "Kneeling Cable Y-Raise", com.hyperlog.app.library.Equipment.Cable,
        com.hyperlog.app.library.MovementPattern.LateralRaise, com.hyperlog.app.library.GripDemand.Low, listOf("Side Delts"), listOf("Traps"),
    )

    /** A fresh install: an empty database with the shipped library synced, as the app starts. */
    private suspend fun freshInstall(): HyperlogDatabase =
        Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
            .also { open += it; ExerciseLibrary(it).sync(seed) }

    @Before
    fun zone() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Helsinki"))

    @After
    fun close() = open.forEach { it.close() }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.log(kg: String, reps: Int, rir: Int) {
        now += 200_000
        closeWarmup()
        focus(Field.Weight); type(kg); done()
        type("$reps"); selectRir(rir); done()
    }

    /** A phone that has been used: every table holds something the user made. */
    private suspend fun usedPhone(): HyperlogDatabase {
        val db = freshInstall()
        val library = ExerciseLibrary(db)
        library.add(Exercise("", "Landmine Press", com.hyperlog.app.library.Equipment.Barbell,
            com.hyperlog.app.library.MovementPattern.VerticalPush, com.hyperlog.app.library.GripDemand.Low, listOf("Front Delts")))
        val bench = db.exercises().all().first { it.id == "barbell_bench_press" }
        library.update(bench.toExercise().copy(note = "Feet planted, my cue"))
        library.setHidden("pec_deck", true)

        val gyms = Gyms(db)
        val gym = gyms.addGym("Central")
        com.hyperlog.app.week.WeekdayPlans(db).set(com.hyperlog.app.week.WeekdayPlan(mapOf(DayOfWeek.MONDAY to gym, DayOfWeek.THURSDAY to gym)))
        val cable = gyms.addItem(EquipmentItem(gymId = gym, name = "Cable station", ladder = LadderSpec.lbStack()))
        gyms.setDumbbells(gym, LadderSpec.Steps(2.0, 2.0, WeightUnit.Kg, 20))
        gyms.setBar(com.hyperlog.app.library.Equipment.EzBar, 8.0)
        gyms.remember(gym, "cable_lateral_raise", cable)
        gyms.markMissing(gym, "leg_extension")
        gyms.setPinPlates(gym, listOf(2.5, 1.25, 1.25))
        gyms.addItem(EquipmentItem(gymId = gym, name = "Chest press", ladder = LadderSpec.DefaultPinStack.copy(builtIn = listOf(2.5, 2.5))))
        // v18: a guessed cable station, a rack from an imported gym, and the gym's default stack.
        gyms.addItem(EquipmentItem(gymId = gym, name = "Crossover", ladder = LadderSpec.DefaultPinStack, state = com.hyperlog.app.gym.LadderState.Guess, cableStation = true))
        gyms.addItem(EquipmentItem(gymId = gym, name = "Squat rack", ladder = LadderSpec.Explicit(emptyList()), kind = EquipmentItem.RACK))
        db.openHelper.writableDatabase.execSQL("UPDATE gym SET default_stack = '${LadderSpec.LbPinStack.encode()}'")
        // v19: the gym's own plates, which its sled loads.
        gyms.setPlates(gym, List(10) { 20.0 } + listOf(10.0, 5.0, 2.5, 1.25, 1.25))
        gyms.addItem(EquipmentItem(gymId = gym, name = "Sled", ladder = LadderSpec.Plates(0.0, emptyList())))

        val programs = Programs(db, clock = { now })
        programs.import(program)
        programs.accept(ChangeJson.decode("""{"summary":"Lighter bench","scope":"rest_of_program","operations":[{"op":"adjust_load","slot":"bench","percent":-10}]}"""))
        programs.accept(ChangeJson.decode("""{"summary":"Skip","scope":"rest_of_program","operations":[{"op":"skip_day"}]}"""))
        programs.revert(db.planChanges().changes(programs.active.first()!!.id).last().id)
        // v25: a program saved for later, with the new exercise it brings.
        programs.save(program.replace("Backup test", "Saved for later").replace("\"cable_lateral_raise\"", "\"kneeling_cable_y_raise\""), listOf(yRaise))

        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        // A finished session.
        logger.startDay(programs.active.first()!!, gym)
        logger.log("80", 8, 2)
        logger.log("80", 8, 1)
        logger.finish()
        // v22: a workout from the coach trained, and another waiting on Today.
        val workouts = com.hyperlog.app.coach.Workouts(db, clock = { now })
        val hotel = com.hyperlog.app.coach.WorkoutFormat.example(db.exercises().all().map { it.toExercise() })
        now += 86_400_000L
        logger.startWorkout(workouts.add(hotel, gym), gym)
        logger.log("30", 10, 1)
        logger.finish()
        workouts.add(hotel.copy(name = "Legs at home"), null)
        // The running one: a set logged, rest running, a technique changed and a half-typed set on the pad.
        now += 3 * 86_400_000L
        logger.startDay(programs.active.first()!!, gym)
        logger.log("82.5", 8, 2)
        logger.setTechnique(1, Technique.Dropset())
        logger.focus(Field.Weight)
        logger.type("82.5")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO set_extension (logged_set_id, position, kind, weight_kg, reps) SELECT MAX(id), 0, 'Drop', 60.0, 6 FROM logged_set",
        )

        db.startingWeights().replaceAll(listOf(StartingWeightEntity("db_bicep_curl", 14.0, 10, 2, now - 400 * 86_400_000L)))
        Bodyweights(db).log(LocalDate.of(2026, 10, 1), 82.4)
        Bodyweights(db).log(LocalDate.of(2026, 10, 8), 81.9)
        LiftGoals(db).set("barbell_bench_press", 100.0, LocalDate.of(2026, 12, 31))
        UpdatePreferences(db).run { setCheckForUpdates(false); dismiss(AppVersion(0, 2, 0)) }
        return db
    }

    /** Every table's rows with their SQLite types, read straight from the database. */
    private fun dump(db: HyperlogDatabase): Map<String, List<List<Pair<Int, Any?>>>> {
        val sql = db.openHelper.readableDatabase
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return tables.sorted().associateWith { table ->
            sql.query("SELECT * FROM `$table` ORDER BY rowid").use { c ->
                buildList {
                    while (c.moveToNext()) add((0 until c.columnCount).map { i -> c.getType(i) to c.value(i) })
                }
            }
        }
    }

    private fun Cursor.value(i: Int): Any? = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
        else -> getString(i)
    }

    @Test
    fun theUsedPhoneFillsEveryBackedUpTable() = runTest {
        val rows = dump(usedPhone())
        BackupFormat.Tables.forEach { assertTrue("$it is empty, so the round trip doesn't cover it", rows.getValue(it).isNotEmpty()) }
    }

    @Test
    fun exportThenRestoreOnAFreshInstallGivesBackEverythingExactly() = runTest {
        val phone = usedPhone()
        val file = Backups(phone, clock = { now }).export()

        val newPhone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(newPhone, clock = { now }).restore(file))

        assertEquals(dump(phone), dump(newPhone))
        val before = SessionLogger(phone, clock = { now }, programs = Programs(phone, clock = { now })).also { it.load() }.state.value
        val after = SessionLogger(newPhone, clock = { now }, programs = Programs(newPhone, clock = { now })).also { it.load() }.state.value
        assertNotNull(after)
        // WeightLadder has no equality of its own: compare its rungs, and everything else as data.
        assertEquals(before!!.exercises.map { it.ladder.rungs }, after!!.exercises.map { it.ladder.rungs })
        val ladder = before.exercises.first().ladder
        assertEquals(before.copy(exercises = before.exercises.map { it.copy(ladder = ladder) }), after.copy(exercises = after.exercises.map { it.copy(ladder = ladder) }))
        assertNotNull(after.restEndsAt)
        assertEquals("82.5", after.entry?.weight)
    }

    @Test
    fun theLastExportIsKeptForSettingsToShow() = runTest {
        val backups = Backups(freshInstall(), clock = { now })
        assertEquals(null, backups.lastExport.first())
        backups.recordExport(now)
        assertEquals(now, backups.lastExport.first())
    }

    @Test
    fun aBackupHoldsTheExportBeforeItAndARestoreShowsThatOne() = runTest {
        val phone = Backups(usedPhone(), clock = { now })
        phone.recordExport(now - 86_400_000)
        // The second export's file is made before its time is recorded.
        val file = phone.export()
        phone.recordExport(now)
        val other = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(other, clock = { now }).restore(file))
        assertEquals(now - 86_400_000, Backups(other, clock = { now }).lastExport.first())
    }

    @Test
    fun theFileSaysWhatItIs() = runTest {
        val file = Json.parseToJsonElement(Backups(usedPhone(), clock = { now }).export()).jsonObject
        assertEquals(JsonPrimitive("hyperlog-backup"), file["format"])
        assertEquals(JsonPrimitive(1), file["version"])
        assertEquals(JsonPrimitive(25), file["database"])
        assertEquals(JsonPrimitive(now), file["exportedAt"])
        assertEquals(BackupFormat.Tables.toSet(), (file["tables"] as JsonObject).keys)
    }

    @Test
    fun restoringReplacesWhatWasOnThePhone() = runTest {
        val file = Backups(usedPhone(), clock = { now }).export()
        val other = freshInstall()
        Gyms(other).addGym("Somewhere else")
        Bodyweights(other).log(LocalDate.of(2026, 1, 1), 90.0)

        assertEquals(Backups.Result.Restored, Backups(other, clock = { now }).restore(file))

        assertEquals(listOf("Central"), Gyms(other).gyms.first().map { it.name })
        assertEquals(listOf(81.9, 82.4), Bodyweights(other).entries.first().map { it.kg })
    }

    private suspend fun assertRefused(reason: Backups.Reason, file: String) {
        val phone = usedPhone()
        val before = dump(phone)
        assertEquals(Backups.Result.Invalid(reason), Backups(phone, clock = { now }).restore(file))
        assertEquals(before, dump(phone))
    }

    @Test
    fun aFileThatIsNotABackupChangesNothing() = runTest {
        assertRefused(Backups.Reason.NotABackup, "not json")
        assertRefused(Backups.Reason.NotABackup, """{"format":1,"title":"A program"}""")
        assertRefused(Backups.Reason.NotABackup, """{"type":"full_backup","workoutLogs":[]}""")
    }

    @Test
    fun aBackupFromANewerHyperlogChangesNothing() = runTest {
        val file = Json.parseToJsonElement(Backups(usedPhone(), clock = { now }).export()).jsonObject
        assertRefused(Backups.Reason.Newer, JsonObject(file + ("database" to JsonPrimitive(BackupFormat.DATABASE + 1))).toString())
        assertRefused(Backups.Reason.Newer, JsonObject(file + ("version" to JsonPrimitive(2))).toString())
    }

    @Test
    fun aBackupWhoseRowsDontFitTogetherChangesNothing() = runTest {
        val file = Backups(usedPhone(), clock = { now }).export()
        // Drop every session but keep their exercises and sets: their session_id points nowhere.
        val tables = Json.parseToJsonElement(file).jsonObject["tables"]!!.jsonObject
        val broken = JsonObject(Json.parseToJsonElement(file).jsonObject +
            ("tables" to JsonObject(tables + ("session" to kotlinx.serialization.json.JsonArray(emptyList())))))
        assertRefused(Backups.Reason.Damaged, broken.toString())
    }

    @Test
    fun aRowWithAnUnknownColumnChangesNothing() = runTest {
        val file = Backups(usedPhone(), clock = { now }).export()
        val damaged = file.replaceFirst("\"archived\":", "\"colour\":\"red\",\"archived\":")
        assertRefused(Backups.Reason.Damaged, damaged)
    }

    @Test
    fun aFileMissingATableChangesNothing() = runTest {
        val file = Json.parseToJsonElement(Backups(usedPhone(), clock = { now }).export()).jsonObject
        val tables = file["tables"]!!.jsonObject
        val cut = JsonObject(file + ("tables" to JsonObject(tables - "lift_goal")))
        assertRefused(Backups.Reason.Damaged, cut.toString())
    }

    @Test
    fun aRowMissingAColumnChangesNothing() = runTest {
        val file = Json.parseToJsonElement(Backups(usedPhone(), clock = { now }).export()).jsonObject
        val tables = file["tables"]!!.jsonObject
        val gyms = kotlinx.serialization.json.JsonArray(tables["gym"]!!.jsonArray.map { JsonObject(it.jsonObject - "archived") })
        assertRefused(Backups.Reason.Damaged, JsonObject(file + ("tables" to JsonObject(tables + ("gym" to gyms)))).toString())
    }

    @Test
    fun aBackupKeptFromThisVersionStillRestores() = runTest {
        // A file exported by DB v14 Hyperlog, kept so a later schema change must still read it.
        val file = javaClass.getResource("/backup/hyperlog-backup-db14.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        assertEquals(listOf("Central"), Gyms(phone).gyms.first().map { it.name })
        assertEquals(100.0, LiftGoals(phone).of("barbell_bench_press")!!.kg, 0.0)
        assertTrue(SessionLogger(phone, clock = { now }, programs = Programs(phone, clock = { now })).also { it.load() }.state.value != null)
    }

    @Test
    fun aBackupKeptFromDatabase16StillRestores() = runTest {
        // DB v16: an archived machine with its history, an EZ bar's weight and the two-dumbbell marks.
        val file = javaClass.getResource("/backup/hyperlog-backup-db16.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val gyms = Gyms(phone)
        assertEquals(listOf("Cable station"), gyms.gyms.first().single().items.map { it.name })
        assertTrue("Old cable · Central" in gyms.machineNames().values)
        assertEquals(8.0, gyms.bars.first().getValue(com.hyperlog.app.library.Equipment.EzBar), 0.0)
        assertTrue(phone.exercises().all().first { it.id == "db_bench_press" }.twoDumbbells)
    }

    @Test
    fun aBackupKeptFromDatabase17StillRestores() = runTest {
        // DB v17: "Check for updates" turned off and 0.2.0's banner dismissed.
        val file = javaClass.getResource("/backup/hyperlog-backup-db17.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val preferences = UpdatePreferences(phone)
        assertEquals(false, preferences.checkForUpdates())
        assertEquals("0.2.0", preferences.dismissed())
        // Its shared dumbbells, edited by hand, became the gym's rack, as the v18 migration does.
        assertEquals(LadderSpec.Steps(2.0, 2.0, WeightUnit.Kg, 20), Gyms(phone).gyms.first().single().dumbbells)
    }

    @Test
    fun aBackupFromBeforeSettingsKeepsUpdatesOn() = runTest {
        val file = javaClass.getResource("/backup/hyperlog-backup-db16.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        assertEquals(true, UpdatePreferences(phone).checkForUpdates())
    }

    @Test
    fun aRunningAppPicksUpTheRestoredSession() = runTest {
        val file = Backups(usedPhone(), clock = { now }).export()
        val phone = freshInstall()
        val logger = SessionLogger(phone, clock = { now }, programs = Programs(phone, clock = { now })).also { it.load() }
        logger.start()
        logger.focus(Field.Weight)
        logger.type("20")

        Backups(phone, clock = { now }).restore(file)
        logger.restored()

        val session = logger.state.value!!
        assertEquals("Barbell Bench Press", session.exercises.first { it.id == session.currentId }.exercise.name)
        assertEquals("82.5", session.entry?.weight)
    }

    @Test
    fun readingAFileFirstSaysWhenItWasMadeAndChangesNothing() = runTest {
        val file = Backups(usedPhone(), clock = { now }).export()
        val phone = freshInstall()
        val before = dump(phone)
        assertEquals(Backups.Contents.Readable(exportedAt = now), Backups(phone).read(file))
        assertEquals(Backups.Contents.Unreadable(Backups.Reason.NotABackup), Backups(phone).read("{}"))
        assertEquals(before, dump(phone))
    }

    @Test
    fun aBackupKeptFromDatabase18StillRestores() = runTest {
        // DB v18: pin plates, a gym's own dumbbells and default stack, a guessed cable station and a rack.
        val file = javaClass.getResource("/backup/hyperlog-backup-db18.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val gym = Gyms(phone).gyms.first().single()
        assertEquals(listOf(2.5, 1.25, 1.25), gym.pinPlates)
        assertEquals(LadderSpec.LbPinStack, gym.defaultStack)
        assertEquals(listOf("Squat rack"), gym.kit.map { it.name })
        val station = gym.items.single { it.cableStation }
        assertEquals(com.hyperlog.app.gym.LadderState.Guess, station.state)
        // From before each gym had its own plates: the gym gets the typical ones.
        assertEquals(LadderSpec.DefaultGymPlates, gym.plates)
    }

    @Test
    fun aBackupKeptFromDatabase19StillRestores() = runTest {
        // DB v19: the gym's own plates, which its sled loads.
        val file = javaClass.getResource("/backup/hyperlog-backup-db19.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val gym = Gyms(phone).gyms.first().single()
        assertEquals(List(10) { 20.0 } + listOf(10.0, 5.0, 2.5, 1.25, 1.25), gym.plates)
        assertEquals(LadderSpec.Plates(0.0, emptyList()), gym.items.single { it.name == "Sled" }.ladder)
    }

    @Test
    fun aBackupFromBeforeGymPlatesGivesEveryGymTheSharedPlatesEditedByHand() = runTest {
        val file = javaClass.getResource("/backup/hyperlog-backup-db18.json")!!.readText()
            .replace("""{"key":"bar_ez_bar","ladder":"list;8.0"}""", """{"key":"bar_ez_bar","ladder":"list;8.0"},{"key":"plates","ladder":"plates;20.0;20.0,20.0,20.0,20.0,10.0"}""")
        assertTrue("plates;20.0;20.0,20.0,20.0,20.0,10.0" in file)
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        assertEquals(listOf(20.0, 20.0, 20.0, 20.0, 10.0), Gyms(phone).gyms.first().single().plates)
        assertEquals(20.0, Gyms(phone).barbell.first(), 0.0)
    }

    @Test
    fun aBackupKeptFromDatabase20StillRestores() = runTest {
        // DB v20: a warm-up typed by hand in the running session.
        val file = javaClass.getResource("/backup/hyperlog-backup-db20.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val bench = phone.sessions().sessionExercises(2).single { it.id == 3L }
        assertEquals("40.0:8,60.0:5", bench.warmups)
        assertEquals(1, bench.warmupsDone)
    }

    @Test
    fun aBackupKeptFromDatabase21StillRestores() = runTest {
        // DB v21, before workouts: its sessions train none, and none waits.
        val file = javaClass.getResource("/backup/hyperlog-backup-db21.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        assertTrue(phone.sessions().finishedSessions().all { it.workoutId == null })
        assertEquals(emptyList<com.hyperlog.app.coach.Workouts.Waiting>(), com.hyperlog.app.coach.Workouts(phone).waiting.first())
    }

    @Test
    fun aWorkoutAndItsSessionComeBackFromABackup() = runTest {
        val file = Backups(usedPhone(), clock = { now }).export()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        assertEquals(listOf("Legs at home"), com.hyperlog.app.coach.Workouts(phone).waiting.first().map { it.workout.name })
        val titles = com.hyperlog.app.session.PastSessionEditor(phone, Programs(phone, clock = { now })).sessions().map { it.title }
        assertTrue(titles.toString(), "Upper body, 45 min" in titles)
    }

    @Test
    fun aBackupKeptFromDatabase22RestoresItsGymsDaysAsYourTrainingDays() = runTest {
        // DB v22, when each gym had its weekdays: Central on Mondays and Thursdays.
        val file = javaClass.getResource("/backup/hyperlog-backup-db22.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        assertEquals(
            com.hyperlog.app.week.WeekdayPlan(mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.THURSDAY to 1L)),
            com.hyperlog.app.week.WeekdayPlans(phone).plan.first(),
        )
        assertEquals(listOf("Central"), Gyms(phone).gyms.first().map { it.name })
    }

    @Test
    fun aBackupKeptFromDatabase23RestoresItsChangesWithNoCheckIn() = runTest {
        val file = javaClass.getResource("/backup/hyperlog-backup-db23.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val history = Programs(phone, clock = { now }).active.first()!!.history
        assertEquals(listOf("Lighter bench", "Skip"), history.map { it.change.summary })
        assertEquals(listOf(null, null), history.map { it.checkIn })
    }

    @Test
    fun aSavedProgramComesBackFromABackupAndStartsWithItsNewExercise() = runTest {
        val file = Backups(usedPhone(), clock = { now }).export()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val programs = Programs(phone, clock = { now })
        val saved = programs.kept.first().single { it.status is com.hyperlog.app.program.KeptProgram.Status.Saved }
        assertEquals("Saved for later", saved.title)
        assertTrue(programs.start(saved.id) is com.hyperlog.app.program.ProgramParser.Result.Valid)
        assertEquals("Saved for later", programs.active.first()!!.program.title)
        assertEquals("Kneeling Cable Y-Raise", phone.exercises().all().single { it.id == yRaise.id }.name)
    }

    @Test
    fun aBackupKeptFromDatabase24RestoresItsProgramAsRunningSinceItsImport() = runTest {
        val file = javaClass.getResource("/backup/hyperlog-backup-db24.json")!!.readText()
        val phone = freshInstall()
        assertEquals(Backups.Result.Restored, Backups(phone, clock = { now }).restore(file))
        val kept = Programs(phone, clock = { now }).kept.first()
        assertEquals(listOf("Backup test"), kept.map { it.title })
        assertEquals(com.hyperlog.app.program.KeptProgram.Status.Running(com.hyperlog.app.program.Position(2, 0), finished = false), kept.single().status)
        assertEquals(1_760_000_000_000L, phone.programs().program(kept.single().id)!!.startedAt)
    }
}
