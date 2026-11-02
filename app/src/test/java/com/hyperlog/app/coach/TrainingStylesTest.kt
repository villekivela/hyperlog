package com.hyperlog.app.coach

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.backup.Backups
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.toExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The training style as kept on the phone: in `setting`, so it survives a restart and a backup (SPEC, Training style → Kept). */
@RunWith(RobolectricTestRunner::class)
class TrainingStylesTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val open = mutableListOf<HyperlogDatabase>()

    private fun install(): HyperlogDatabase =
        Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build().also { open += it }

    @After
    fun close() = open.forEach { it.close() }

    private val mine = TrainingTargets(
        goal = TrainingGoal.Both, volume = NumberRange(14, 20), acrossBlock = AcrossBlock.Rising, frequency = Frequency.Twice,
        failure = FailureRule.Never, calibration = false, compound = NumberRange(4, 8), isolation = NumberRange(10, 20),
    )
    private val me = AboutMe(
        age = AgeBand.Forties, progress = Progress.Months, comingBack = true,
        aches = mapOf(BodyArea.Knees to "clicks on deep squats", BodyArea.WristsAndHands to "sore when gripping"),
        avoid = listOf("barbell_back_squat"), favour = listOf("hack_squat"), anythingElse = "I train fasted",
    )

    @Test
    fun withNothingSetTheStyleIsTheDefaultsAndTheShippedHowITrain() = runTest {
        val style = TrainingStyles(install(), context).current()
        assertEquals(TrainingTargets(volume = NumberRange(8, 14)), style.targets)
        assertEquals(AboutMe(), style.aboutMe)
        assertTrue(style.howITrain, style.howITrain.startsWith("- Form comes before load."))
    }

    @Test
    fun theStyleIsKeptAndComesBackOnTheNextStart() = runTest {
        val db = install()
        TrainingStyles(db, context).apply {
            setTargets(mine)
            setAboutMe(me)
            setHowITrain("Heavy and simple.")
        }
        // The next start reads the same table.
        val again = TrainingStyles(db, context)
        val style = again.current()
        assertEquals(mine, style.targets)
        // Aches come back in body order.
        assertEquals(me.copy(aches = mapOf(BodyArea.WristsAndHands to "sore when gripping", BodyArea.Knees to "clicks on deep squats")), style.aboutMe)
        assertEquals("Heavy and simple.", style.howITrain)
        assertTrue(again.edited.first())
    }

    @Test
    fun backToDefaultKeepsNoTextSoANewDefaultReachesYou() = runTest {
        val db = install()
        var shipped = "Old default."
        val styles = TrainingStyles(db) { shipped }
        styles.setHowITrain("Mine.")
        styles.setHowITrain(shipped)
        assertFalse(styles.edited.first())
        // The page shows the default with its wrapped lines joined: saved like that, it is still the default.
        shipped = "- Form comes before load. A\n  heavier weight never buys a shorter rep."
        styles.setHowITrain("- Form comes before load. A heavier weight never buys a shorter rep.")
        assertFalse(styles.edited.first())
        shipped = "New default."
        assertEquals("New default.", styles.current().howITrain)
    }

    @Test
    fun theStyleGoesIntoABackupAndARestoreBringsItBack() = runTest {
        val phone = install()
        TrainingStyles(phone, context).apply {
            setTargets(mine)
            setAboutMe(me)
            setHowITrain("Heavy and simple.")
        }
        val file = Backups(phone).export()
        val other = install()
        assertEquals(Backups.Result.Restored, Backups(other).restore(file))
        val style = TrainingStyles(other, context).current()
        assertEquals(mine, style.targets)
        assertEquals(me.avoid, style.aboutMe.avoid)
        assertEquals(me.aches.keys, style.aboutMe.aches.keys)
        assertEquals("Heavy and simple.", style.howITrain)
    }

    @Test
    fun aProposedProgramsFocusIsStoredWithItAndLaterCheckInsSeeIt() = runTest {
        val db = install()
        val seed = com.hyperlog.app.library.LibrarySeed.fromAssets(context)
        com.hyperlog.app.library.ExerciseLibrary(db).sync(seed)
        val library = db.exercises().all().map { it.toExercise() }
        val file = ProgramFormat.example(library).toString().replaceFirst("{", """{"focus": ["Back"], "targets": {"volume": "10-16"},""")
        val reply = kotlinx.serialization.json.Json.parseToJsonElement("""{"program": $file, "swaps": [], "newExercises": []}""") as kotlinx.serialization.json.JsonObject
        val proposal = (ProgramProposal.read(reply, library.map { it.id }.toSet(), library.associate { it.id to it.name }) as ProgramProposal.Result.Valid).proposal
        val programs = com.hyperlog.app.program.Programs(db)
        programs.import(proposal.source)
        // Read back from the database, as the next check-in reads it.
        val active = com.hyperlog.app.program.Programs(db).active.first()!!
        assertEquals(listOf(com.hyperlog.app.library.MuscleGroup.Back), active.program.focus)
        val text = CheckInContext.frame(active, emptyList(), library, style = TrainingStyles(db, context).current()).context
        assertTrue(text, text.contains("Program focus: Back (their muscles 16–24 sets a week, the rest 5–10)"))
        assertTrue(text, text.contains("Volume for this program: 10–16"))
    }
}
