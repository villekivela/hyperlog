package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
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

/**
 * Renders of the bench angle's row and sheet (SPEC, Today → Bench angle), for the design note.
 * Skipped unless asked for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*BenchAngleRenders*'`,
 * with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BenchAngleRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private val incline = Exercise(
        "db_incline_press", "Incline DB Press", Equipment.Dumbbell, MovementPattern.HorizontalPush, GripDemand.High, listOf("Chest"),
        adjustableBench = true,
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-bench-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        capture(name)
    }

    /** The sheet as it sits on its modal sheet: surfaceContainerLow, rounded at the top. */
    @Composable
    private fun OnSheet(content: @Composable () -> Unit) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            // The drag handle's room above the content, as on the modal sheet.
            Box(Modifier.padding(top = 32.dp)) { content() }
        }
    }

    private fun sheet(name: String, target: Int?, start: Int? = null, reading: Int?, measuring: Boolean, typed: String = "", againstToday: Boolean = false) {
        render(name) { OnSheet { BenchAngleSheet("Incline DB Press", target, againstToday, start, reading, measuring, onSave = {}, onClose = {}) } }
        if (typed.isNotEmpty()) {
            typed.forEach { compose.onNode(hasText(it.toString()) and hasAnyAncestor(hasTestTag(BenchSheetTag))).performClick() }
            capture(name)
        }
    }

    @Test fun phoneOver() = sheet("sheet-phone-over", target = 30, reading = 41, measuring = true)
    @Test fun phoneUnder() = sheet("sheet-phone-under", target = 45, reading = 36, measuring = true)
    @Test fun phoneOnTarget() = sheet("sheet-phone-on-target", target = 30, reading = 32, measuring = true)
    @Test fun waiting() = sheet("sheet-waiting", target = 30, reading = null, measuring = true)
    @Test fun typedOnTarget() = sheet("sheet-typed-on-target", target = 30, reading = 41, measuring = true, typed = "30")
    @Test fun tooSteep() = sheet("sheet-too-steep", target = 30, reading = 41, measuring = true, typed = "95")
    @Test fun firstTime() = sheet("sheet-first-time", target = null, reading = 15, measuring = true)
    @Test fun typeOnly() = sheet("sheet-type-only", target = 60, start = null, reading = null, measuring = false)
    @Test fun flat() = sheet("sheet-flat", target = 0, reading = 0, measuring = true)
    @Test fun againstToday() = sheet("sheet-againstToday", target = 30, start = 30, reading = 26, measuring = true, againstToday = true)
    @Test fun todayWaiting() = sheet("sheet-againstToday-waiting", target = 30, start = 30, reading = null, measuring = true, againstToday = true)
    @Test fun upright() = sheet("sheet-upright", target = 85, reading = 90, measuring = true)

    private fun table(benchAngle: Int?, lastBenchAngle: Int?) = SessionExercise(
        id = 1, exercise = incline, sets = emptyList(),
        lastTime = listOf(LoggedSet(30.0, 8, 0, 1), LoggedSet(30.0, 7, 0, 1)),
        ladder = WeightLadder((1..30).map { it * 2.0 }), ladderKnown = true,
        prescription = listOf(SetPrescription(RepRange(6, 8), 1, 150), SetPrescription(RepRange(6, 8), 1, 150)),
        suggestion = Suggestion(32.0, 8, Suggestion.Kind.Up, "Last time 30 kg × 8 @ 1, top of 6–8 → 32 kg", "32 · hit the top of 6–8 last time"),
        benchAngle = benchAngle, lastBenchAngle = lastBenchAngle,
    )

    private fun session(e: SessionExercise) = ActiveSession(
        id = 1, startedAt = 0, exercises = listOf(e), currentId = 1,
        entry = SetEntry("32", "", "", 1, Field.Weight, partialMode = false, fresh = true), padVisible = true,
        restEndsAt = null, restTotalMs = 0, rampEntry = SetEntry("32", "", "", 1, Field.Weight, partialMode = false, fresh = true),
    )

    @Test fun rowLastTime() = render("row-last-time") { SessionScreen(session(table(null, 30)), listOf(incline), Noop, onLeave = {}) }
    @Test fun rowSet() = render("row-set") { SessionScreen(session(table(32, 30)), listOf(incline), Noop, onLeave = {}) }
    @Test fun rowEmpty() = render("row-empty") { SessionScreen(session(table(null, null)), listOf(incline), Noop, onLeave = {}) }
}
