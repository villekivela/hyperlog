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


    @Test
    fun withNothingSetTheStyleIsTheDefaultsAndTheShippedPrinciples() = runTest {
        val style = TrainingStyles(install(), context).current()
        assertEquals(TrainingTargets(volume = NumberRange(8, 14)), style.targets)
        assertEquals(AboutMe(), style.aboutMe)
        // Every principle on, with its sentence from the asset.
        assertEquals(HowITrain(), style.howITrain)
        assertTrue(Principle.entries.all { style.howITrain.on(it) })
        assertTrue(style.sentences.getValue(Principle.FormBeforeLoad), style.sentences.getValue(Principle.FormBeforeLoad).startsWith("Form comes before load"))
    }

    private val mine = TrainingTargets(
        goal = TrainingGoal.Both, volume = NumberRange(14, 20), acrossBlock = AcrossBlock.Rising, frequency = Frequency.Twice,
        failure = FailureRule.Never, calibration = false, compound = NumberRange(4, 8), isolation = NumberRange(10, 20),
    )
    private val me = AboutMe(
        age = AgeBand.Forties, progress = Progress.Months, comingBack = true,
        aches = mapOf(BodyArea.Knees to "clicks on deep squats", BodyArea.WristsAndHands to "sore when gripping"),
        avoid = listOf("barbell_back_squat"), favour = listOf("hack_squat"), anythingElse = "I train fasted",
    )
    private val principles = HowITrain(off = setOf(Principle.Deload), anythingElse = "Supersets for arms")

    @Test
    fun theStyleIsKeptAndComesBackOnTheNextStart() = runTest {
        val db = install()
        TrainingStyles(db, context).apply {
            setTargets(mine)
            setAboutMe(me)
            setHowITrain(principles)
        }
        // The next start reads the same table.
        val style = TrainingStyles(db, context).current()
        assertEquals(mine, style.targets)
        // Aches come back in body order.
        assertEquals(me.copy(aches = mapOf(BodyArea.WristsAndHands to "sore when gripping", BodyArea.Knees to "clicks on deep squats")), style.aboutMe)
        assertEquals(principles, style.howITrain)
        // Only what's off is kept, so a principle added later starts on.
        val kept = db.settings().get(TrainingStyles.STYLE)!!
        assertTrue(kept, kept.contains("\"off\":[\"deload\"]"))
        assertEquals(HowITrain(off = setOf(Principle.Deload)), TrainingStyleJson.decode(kept.replace("\"deload\"", "\"deload\",\"gone_since\"")).howITrain.copy(anythingElse = null))
    }

    @Test
    fun theStyleGoesIntoABackupAndARestoreBringsItBack() = runTest {
        val phone = install()
        TrainingStyles(phone, context).apply {
            setTargets(mine)
            setAboutMe(me)
            setHowITrain(principles)
        }
        val file = Backups(phone).export()
        val other = install()
        assertEquals(Backups.Result.Restored, Backups(other).restore(file))
        val style = TrainingStyles(other, context).current()
        assertEquals(mine, style.targets)
        assertEquals(me.avoid, style.aboutMe.avoid)
        assertEquals(me.aches.keys, style.aboutMe.aches.keys)
        assertEquals(principles, style.howITrain)
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

    @Test
    fun theWizardsDoneCountsAsSetEvenWithEveryDefaultKeptAndKeepsTheStyle() = runTest {
        val styles = TrainingStyles(install(), context)
        assertFalse(styles.done.first())
        styles.finish(TrainingTargets(), AboutMe())
        assertTrue(styles.done.first())
        assertEquals(TrainingTargets(), styles.current().targets)
        styles.finish(mine, me)
        assertEquals(mine, styles.current().targets)
        assertEquals(me.avoid, styles.current().aboutMe.avoid)
    }

    @Test
    fun finishingLeavesHowITrainAsItWas() = runTest {
        val styles = TrainingStyles(install(), context)
        styles.setHowITrain(principles)
        styles.finish(mine, me)
        assertEquals(principles, styles.current().howITrain)
    }

    @Test
    fun dismissingTheCardHidesItButDoesNotCountAsSet() = runTest {
        val styles = TrainingStyles(install(), context)
        assertFalse(styles.cardDismissed.first())
        styles.dismissCard()
        assertTrue(styles.cardDismissed.first())
        assertFalse(styles.done.first())
    }

    @Test
    fun theWizardsFlagsGoIntoABackup() = runTest {
        val phone = install()
        val styles = TrainingStyles(phone, context)
        styles.finish(mine, me)
        styles.dismissCard()
        val other = install()
        assertEquals(Backups.Result.Restored, Backups(other).restore(Backups(phone).export()))
        assertTrue(TrainingStyles(other, context).done.first())
        assertTrue(TrainingStyles(other, context).cardDismissed.first())
    }
}
