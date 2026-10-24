package com.hyperlog.app.ui.session

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.progression.Record
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A record set's badge in the set table is a disc of full ink, so it reads in dark mode as well as
 * light (SPEC, Records).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordBadgeColourTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    /** The badge's commonest colour besides the row's surface (its corners, ring and trophy), and the theme's full ink. */
    private fun disc(dark: Boolean): Pair<Color, Color> {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val top = SetPrescription(RepRange(4, 6), 1, 180)
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = listOf(LoggedSet(100.0, 6, 0, 1)), lastTime = emptyList(), ladder = DefaultPlates.ladder(),
            ladderKnown = true, prescription = listOf(top, top), plates = DefaultPlates, records = listOf(Record.Estimate),
        )
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = null,
            padVisible = true, restEndsAt = null, restTotalMs = 0,
        )
        var ink = Color.Unspecified
        var surface = Color.Unspecified
        compose.setContent { HyperlogTheme { ink = MaterialTheme.colorScheme.onSurface; surface = MaterialTheme.colorScheme.surface; SessionScreen(session, listOf(bench), Noop, onLeave = {}) } }
        val badge = compose.onNode(hasTestTag(RecordBadgeTag), useUnmergedTree = true).captureToImage().toPixelMap()
        val pixels = (0 until badge.width).flatMap { x -> (0 until badge.height).map { y -> badge[x, y] } }
        fun near(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue) < 0.06f
        return pixels.filterNot { near(it, surface) }.groupingBy { it }.eachCount().maxBy { it.value }.key to ink
    }

    private fun assertInk(dark: Boolean) {
        val (fill, ink) = disc(dark)
        assertEquals("dark=$dark red", ink.red, fill.red, 0.02f)
        assertEquals("dark=$dark green", ink.green, fill.green, 0.02f)
        assertEquals("dark=$dark blue", ink.blue, fill.blue, 0.02f)
    }

    @Test
    fun theBadgeIsFullInkInLight() = assertInk(dark = false)

    @Test
    fun theBadgeIsFullInkInDark() = assertInk(dark = true)
}
