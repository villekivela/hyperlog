package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.program.SetSummary
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

/** The five technique colours on the set table, the drop and myo pills, the plan text and the sheet, for judging them. Run with RENDERS=<dir>. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TechniqueRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val all = listOf(
        Technique.Dropset(), Technique.MyoReps(), Technique.LengthenedPartials(),
        Technique.IsoHold(seconds = 20, position = HoldPosition.Stretch), Technique.Failure,
    )

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out!!).mkdirs()
        File(out, "technique-colours-$name-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun session(sets: List<LoggedSet>, plan: List<SetPrescription>, followUp: FollowUp? = null, entry: SetEntry? = null) = ActiveSession(
        id = 1, startedAt = 0, currentId = 1, entry = entry, padVisible = entry != null, restEndsAt = null, restTotalMs = 0, followUp = followUp,
        exercises = listOf(
            SessionExercise(1, bench, sets, emptyList(), DefaultPlates.ladder(), true, prescription = plan, plates = DefaultPlates),
        ),
    )

    /** One plain set, then one of each technique. */
    private fun table(dark: Boolean) = shoot("sets", dark) {
        val plan = listOf(SetPrescription(RepRange(4, 6), 1, 180)) + all.map { SetPrescription(RepRange(8, 10), 1, 120, technique = it) }
        SessionScreen(session(emptyList(), plan), listOf(bench), Noop, onLeave = {})
    }

    /** A drop set with its first drop logged and the second under way, in the pad. */
    private fun dropPill(dark: Boolean) = shoot("drop-pill", dark) {
        val drops = Technique.Dropset(2, 25)
        val set = LoggedSet(25.0, 9, 0, 1, technique = drops, extensions = listOf(Extension(Extension.Kind.Drop, 17.5, 6)))
        val entry = SetEntry("12.5", "6", "", null, Field.Reps, partialMode = false, fresh = true)
        SessionScreen(session(listOf(set), listOf(SetPrescription(RepRange(8, 10), 1, 120, technique = drops)), FollowUp.Drop(1, 2), entry), listOf(bench), Noop, onLeave = {})
    }

    /** A finished myo-reps set with three mini-sets. */
    private fun myoPill(dark: Boolean) = shoot("myo-pill", dark) {
        val myo = Technique.MyoReps(4)
        val set = LoggedSet(60.0, 12, 0, 0, technique = myo, extensions = List(3) { Extension(Extension.Kind.MiniSet, 60.0, 4) })
        SessionScreen(session(listOf(set), listOf(SetPrescription(RepRange(10, 12), 0, 90, technique = myo))), listOf(bench), Noop, onLeave = {})
    }

    private fun planText(dark: Boolean) = shoot("plan-text", dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                all.forEach { SetSummary(listOf(SetPrescription(RepRange(8, 10), 2, 120, technique = it))) }
            }
        }
    }

    private fun sheet(dark: Boolean) = shoot("sheet", dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { SetSheet(3, Technique.Dropset(), null, {}, {}) }
    }

    @Test fun setsLight() { assumeTrue(out != null); table(false) }
    @Test fun setsDark() { assumeTrue(out != null); table(true) }
    @Test fun dropPillLight() { assumeTrue(out != null); dropPill(false) }
    @Test fun dropPillDark() { assumeTrue(out != null); dropPill(true) }
    @Test fun myoPillLight() { assumeTrue(out != null); myoPill(false) }
    @Test fun myoPillDark() { assumeTrue(out != null); myoPill(true) }
    @Test fun planTextLight() { assumeTrue(out != null); planText(false) }
    @Test fun planTextDark() { assumeTrue(out != null); planText(true) }
    @Test fun sheetLight() { assumeTrue(out != null); sheet(false) }
    @Test fun sheetDark() { assumeTrue(out != null); sheet(true) }
}
