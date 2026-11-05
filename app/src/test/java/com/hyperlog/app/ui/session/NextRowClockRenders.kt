package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.FollowUp
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

/** Renders of the Next row as the session's clock (#249). Run with RENDERS=<dir>, and DARK=1 for dark. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NextRowClockRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val squat = Exercise("squat", "Back Squat", Equipment.Barbell, MovementPattern.Squat, GripDemand.Low, listOf("Quads"))

    private fun session(
        padVisible: Boolean = true, restLeftMs: Long? = null, totalMs: Long = 180_000, breathing: Boolean = false,
        heldMs: Long? = null,
    ): ActiveSession {
        val now = System.currentTimeMillis()
        val plan = listOf(SetPrescription(RepRange(4, 6), 1, 180), SetPrescription(RepRange(6, 8), 1, 180), SetPrescription(RepRange(6, 8), 1, 180))
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = listOf(LoggedSet(100.0, 6, 0, 1)), lastTime = listOf(LoggedSet(97.5, 6, 0, 1)),
            ladder = DefaultPlates.ladder(), ladderKnown = true, prescription = plan, plates = DefaultPlates,
        )
        val other = SessionExercise(2, squat, emptyList(), emptyList(), DefaultPlates.ladder(), true)
        val entry = SetEntry("100", "6", "", 1, Field.Reps, partialMode = false, fresh = true)
        return ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise, other), currentId = 1, entry = entry, padVisible = padVisible,
            restEndsAt = restLeftMs?.let { now + it }, restTotalMs = totalMs, breathing = breathing,
            holdStartedAt = heldMs?.let { now - it }, followUp = heldMs?.let { FollowUp.Hold(30, HoldPosition.Stretch) },
        )
    }

    private fun render(name: String, session: ActiveSession, fontScale: Float = 1f) {
        assumeTrue(out != null)
        val dark = System.getenv("DARK") == "1"
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HyperlogTheme { SessionScreen(session, listOf(bench, squat), Noop, onLeave = {}) }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out!!).mkdirs()
        File(out, "next-row-clock-${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun restPad() = render("rest-pad", session(restLeftMs = 130_000))
    @Test fun restPadHidden() = render("rest-pad-hidden", session(padVisible = false, restLeftMs = 130_000))
    @Test fun restLow() = render("rest-low", session(restLeftMs = 25_000))
    @Test fun afterRest() = render("after-rest", session(restLeftMs = -12_000))
    @Test fun breathing() = render("breathing", session(restLeftMs = 9_000, totalMs = 15_000, breathing = true))
    @Test fun holdFilling() = render("hold", session(heldMs = 12_000))
    @Test fun holdReached() = render("hold-reached", session(heldMs = 31_000))
    @Test fun restLargeText() = render("rest-large-text", session(restLeftMs = 130_000), fontScale = 2f)
    @Test fun afterRestLargeText() = render("after-rest-large-text", session(restLeftMs = -72_000), fontScale = 2f)
}
