package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.session.WarmupEdit
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders of the logging screen's states, for the design note. Run with -Drenders=<dir>. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val fly = Exercise("cable_fly", "Cable Fly", Equipment.Cable, MovementPattern.ChestFly, GripDemand.Low, listOf("Chest"))
    private val curl = Exercise("leg_curl", "Seated Leg Curl", Equipment.Machine, MovementPattern.KneeFlexion, GripDemand.Low, listOf("Hamstrings"))
    private val plates = DefaultPlates

    private fun plan(vararg sets: SetPrescription) = sets.toList()
    private fun set(min: Int, max: Int, rir: Int, technique: Technique? = null, backoff: Boolean = false) =
        SetPrescription(RepRange(min, max), rir, 180, technique = technique, backoff = if (backoff) com.hyperlog.app.program.Backoff(0, 10) else null)

    private fun render(name: String, session: ActiveSession, dark: Boolean) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench, fly, curl), Noop, onLeave = {}) } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun both(name: String, session: () -> ActiveSession) {
        assumeTrue(out != null)
        render(name, session(), dark = System.getenv("DARK") == "1")
    }

    private fun benchExercise(sets: List<LoggedSet> = emptyList(), warmupsDone: Int = 0, techniques: Plan = plan(set(4, 6, 1), set(6, 8, 1, backoff = true), set(6, 8, 1, backoff = true)), slot: PlannedSlot? = null) = SessionExercise(
        id = 1, exercise = bench, sets = sets,
        lastTime = listOf(LoggedSet(97.5, 6, 0, 1), LoggedSet(87.5, 8, 0, 1), LoggedSet(87.5, 7, 0, 1)),
        ladder = plates.ladder(), ladderKnown = true, prescription = techniques,
        suggestion = Suggestion(100.0, 6, Suggestion.Kind.Up, "Last time 97.5 kg × 6 @ 1, top of 4–6 → 100 kg", "100 · hit the top of 4–6 last time"),
        plates = plates, warmupsDone = warmupsDone, slot = slot,
    )

    private fun session(vararg e: SessionExercise, entry: SetEntry?, rest: Boolean = false, pad: Boolean = true, warmupEdit: WarmupEdit? = null, latest: Pair<Long, Int>? = null) = ActiveSession(
        id = 1, startedAt = 0, exercises = e.toList(), currentId = e.first().id, entry = entry, padVisible = pad,
        restEndsAt = if (rest) System.currentTimeMillis() + 95_000 else null, restTotalMs = 180_000,
        warmupEdit = warmupEdit, rampEntry = entry, latestSet = latest,
    )

    /** Reps are never prefilled: the cell shows the target range as a placeholder. */
    private val reps100 = SetEntry("100", "", "", 1, Field.Reps, partialMode = false, fresh = true)

    @Test fun warmups() = both("session-warmups") {
        session(benchExercise(warmupsDone = 2), SessionExercise(2, curl, emptyList(), emptyList(), plates.ladder(), true), entry = reps100)
    }

    @Test fun warmupOnPad() = both("session-warmup-on-pad") {
        session(benchExercise(warmupsDone = 2), entry = reps100,
            warmupEdit = WarmupEdit(1, 2, SetEntry("75", "3", "", null, Field.Weight, partialMode = false, fresh = true)))
    }

    @Test fun setLogged() = both("session-set-logged") {
        val slot = PlannedSlot("s", "1a", 0, "barbell_bench_press", emptyList(), null, emptyList())
        session(
            benchExercise(sets = listOf(LoggedSet(100.0, 6, 0, 1)), slot = slot).copy(suggestion = Suggestion(90.0, 8, Suggestion.Kind.Backoff, null)),
            SessionExercise(2, curl, emptyList(), emptyList(), plates.ladder(), true, slot = slot.copy(id = "c", label = "1b", exercise = "leg_curl")),
            entry = SetEntry("90", "", "", 1, Field.Reps, partialMode = false, fresh = true), rest = true, latest = 1L to 0,
        )
    }

    @Test fun techniques() = both("session-techniques") {
        val e = SessionExercise(
            id = 3, exercise = fly, sets = listOf(LoggedSet(25.0, 10, 0, 0, technique = Technique.Failure)),
            lastTime = listOf(LoggedSet(25.0, 10, 0, 1), LoggedSet(25.0, 9, 0, 0)), ladder = com.hyperlog.app.domain.WeightLadder((1..40).map { it * 2.5 }), ladderKnown = true,
            prescription = plan(set(8, 10, 0, Technique.Failure), set(8, 10, 1, Technique.Dropset(2, 25)), set(10, 12, 0, Technique.MyoReps(4))),
            slot = PlannedSlot("f", "3", 2, "barbell_bench_press", emptyList(), null, emptyList()),
            suggestion = Suggestion(25.0, 10, Suggestion.Kind.Follow, null),
        )
        session(e, entry = SetEntry("25", "10", "", 1, Field.Reps, partialMode = false, fresh = true))
    }

    /** A drop set under way: set 3 logged, its first drop done and the second on the pad. */
    @Test fun activeDrop() = both("session-active-drop") {
        val drops = Technique.Dropset(2, 25)
        val e = SessionExercise(
            id = 3, exercise = fly,
            sets = listOf(
                LoggedSet(25.0, 10, 0, 0, technique = Technique.Failure), LoggedSet(25.0, 9, 0, 1),
                LoggedSet(25.0, 10, 0, 1, technique = drops, extensions = listOf(com.hyperlog.app.domain.Extension(com.hyperlog.app.domain.Extension.Kind.Drop, 17.5, 7))),
            ),
            lastTime = listOf(LoggedSet(25.0, 10, 0, 1), LoggedSet(25.0, 9, 0, 0)), ladder = com.hyperlog.app.domain.WeightLadder((1..40).map { it * 2.5 }), ladderKnown = true,
            prescription = plan(set(8, 10, 0, Technique.Failure), set(8, 10, 1), set(8, 10, 1, drops), set(10, 12, 1)),
            slot = PlannedSlot("f", "3", 2, "cable_fly", emptyList(), null, emptyList()),
        )
        session(e, entry = SetEntry("12.5", "6", "", null, Field.Reps, partialMode = false, fresh = true), latest = 3L to 2)
            .copy(followUp = com.hyperlog.app.session.FollowUp.Drop(1, 2), openSetId = 9)
    }

    /** Myo-reps under way: the activation set and two mini-sets logged, the third on the pad. */
    @Test fun activeMiniSet() = both("session-active-mini-set") {
        val myo = Technique.MyoReps(4)
        val mini = com.hyperlog.app.domain.Extension(com.hyperlog.app.domain.Extension.Kind.MiniSet, 60.0, 4)
        val e = SessionExercise(
            id = 5, exercise = curl, sets = listOf(LoggedSet(60.0, 12, 0, 0, technique = myo, extensions = listOf(mini, mini))),
            lastTime = listOf(LoggedSet(57.5, 12, 0, 0)), ladder = com.hyperlog.app.domain.WeightLadder((1..40).map { it * 2.5 }), ladderKnown = true,
            prescription = plan(set(10, 12, 0, myo), set(10, 12, 1)),
        )
        session(e, entry = SetEntry("60", "4", "", null, Field.Reps, partialMode = false, fresh = true), latest = 5L to 0)
            .copy(followUp = com.hyperlog.app.session.FollowUp.MiniSet(2, 4), openSetId = 9)
    }

    /** Myo-reps about to start: one muted planned mini-set row, no `+`. */
    @Test fun miniSetsPlanned() = both("session-mini-sets-planned") {
        val e = SessionExercise(
            id = 5, exercise = curl, sets = emptyList(),
            lastTime = listOf(LoggedSet(57.5, 12, 0, 0)), ladder = com.hyperlog.app.domain.WeightLadder((1..40).map { it * 2.5 }), ladderKnown = true,
            prescription = plan(set(10, 12, 0, Technique.MyoReps(4)), set(10, 12, 1)),
        )
        session(e, entry = SetEntry("60", "", "", 0, Field.Reps, partialMode = false, fresh = true))
    }

    @Test fun adHoc() = both("adhoc-session") {
        val e = SessionExercise(
            id = 4, exercise = curl, sets = listOf(LoggedSet(40.0, 12, 0, 2)),
            lastTime = listOf(LoggedSet(37.5, 12, 0, 2), LoggedSet(37.5, 11, 0, 1), LoggedSet(37.5, 10, 0, 0)),
            ladder = com.hyperlog.app.domain.WeightLadder((1..30).map { it * 2.5 }), ladderKnown = true,
        )
        session(e, entry = SetEntry("40", "12", "", 1, Field.Reps, partialMode = false, fresh = true), rest = true, latest = 4L to 0)
    }

    @Test fun padHidden() = both("session-pad-hidden") {
        session(benchExercise(warmupsDone = 4), SessionExercise(2, curl, emptyList(), emptyList(), plates.ladder(), true), entry = reps100, pad = false)
    }

    @Test fun done() {
        assumeTrue(out != null)
        val dark = System.getenv("DARK") == "1"
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val start = java.time.LocalDateTime.of(2026, 10, 13, 10, 22).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val summary = com.hyperlog.app.session.SessionSummary(
            1, start, start + 52 * 60_000,
            listOf(
                com.hyperlog.app.session.ExerciseSummary(bench.name, listOf(LoggedSet(100.0, 6, 0, 1), LoggedSet(90.0, 8, 0, 1), LoggedSet(90.0, 7, 0, 0, extensions = listOf(Extension(Extension.Kind.Drop, 70.0, 6))))),
                // Myo-reps and partials: the chip ends the whole set.
                com.hyperlog.app.session.ExerciseSummary(curl.name, listOf(LoggedSet(60.0, 12, 0, 2), LoggedSet(60.0, 10, 4, 0), LoggedSet(45.0, 10, 0, 2, extensions = List(3) { Extension(Extension.Kind.MiniSet, 45.0, 3) }))),
            ),
        )
        compose.setContent { HyperlogTheme { SessionDoneScreen(summary, onResume = {}, onClose = {}, onOpen = {}) } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-session-done-sets.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The exercise sheet's prescription: each set's target RIR as an outlined chip (#46). */
    @Test fun exerciseSheet() {
        assumeTrue(out != null)
        val dark = System.getenv("DARK") == "1"
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val plan = plan(set(4, 6, 1), set(6, 8, 2, backoff = true), set(8, 10, 0, Technique.Dropset(2, 25)), set(10, 12, 3))
        compose.setContent {
            HyperlogTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 24.dp)) {
                        ExerciseSheet(bench, machine = null, onChangeMachine = null, plan = plan, why = "Last time 97.5 kg × 6 @ 1, top of 4–6 → 100 kg" to Direction.Up)
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-exercise-sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private typealias Plan = List<SetPrescription>

internal object Noop : SessionActions {
    override fun press(key: PadKey) = Unit
    override fun done() = Unit
    override fun selectRir(value: Int) = Unit
    override fun focus(field: Field) = Unit
    override fun partialMode(on: Boolean) = Unit
    override fun pickWeight(kg: Double) = Unit
    override fun setPadVisible(visible: Boolean) = Unit
    override fun addExercise(exerciseId: String) = Unit
    override fun switchTo(sessionExerciseId: Long) = Unit
    override fun adjustRest(deltaMs: Long) = Unit
    override fun endRest() = Unit
    override fun finish() = Unit
    override fun chooseMachine(itemId: Long) = Unit
    override fun addMachine(name: String, ladder: LadderSpec) = Unit
    override fun changeMachine() = Unit
    override fun saveMachine(item: com.hyperlog.app.gym.EquipmentItem) = Unit
    override fun setUpLadder(spec: LadderSpec) = Unit
    override fun skipLadderSetup() = Unit
    override fun takeLadderOffer() = Unit
    override fun declineLadderOffer() = Unit
    override fun notAtGym() = Unit
    override fun finishSet() = Unit
    override fun startHold() = Unit
    override fun stopHold() = Unit
    override fun setTechnique(setIndex: Int, technique: Technique?) = Unit
    override fun split(on: Boolean) = Unit
    override fun swap(sessionExerciseId: Long, exerciseId: String) = Unit
    override fun addSet(sessionExerciseId: Long) = Unit
    override fun removeSet(sessionExerciseId: Long, setIndex: Int) = Unit
    override fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: RepRange, rir: Int?) = Unit
    override fun removeExercise(sessionExerciseId: Long) = Unit
    override fun move(sessionExerciseId: Long, by: Int) = Unit
    override fun tickWarmup(index: Int) = Unit
    override fun open(sessionExerciseId: Long) = Unit
    override fun closeView() = Unit
    override fun correct(sessionExerciseId: Long, set: Int, extension: Int?) = Unit
    override fun cancelCorrection() = Unit
}
