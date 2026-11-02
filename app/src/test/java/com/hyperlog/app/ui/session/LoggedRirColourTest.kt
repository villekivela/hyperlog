package com.hyperlog.app.ui.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
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
 * A logged RIR on the logging screen is the badge History shows: a soft tint of its colour with the
 * number in that colour, a step darker in light and lighter in dark (#46).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoggedRirColourTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    /** The fill just inside the edge of the one logged chip in the set table, with set 1 logged at [rir], and its number's colour. */
    private fun loggedChip(rir: Int, dark: Boolean): Pair<Color, Color> {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val top = SetPrescription(RepRange(4, 6), 1, 180)
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = listOf(LoggedSet(100.0, 6, 0, rir)), lastTime = emptyList(), ladder = DefaultPlates.ladder(),
            ladderKnown = true, prescription = listOf(top, top), plates = DefaultPlates,
        )
        val entry = SetEntry("100", "", "", null, Field.Weight, partialMode = false, fresh = true)
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry,
            padVisible = true, restEndsAt = null, restTotalMs = 0, latestSet = 1L to 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), Noop, onLeave = {}) } }
        val chip = compose.onNode(hasTestTag(RirChipLoggedTag) and !onRirPad, useUnmergedTree = true).captureToImage().toPixelMap()
        // The edge is 1 dp (3 px); the number is the pixel furthest in luminance from the fill.
        val fill = chip[6, chip.height / 2]
        val number = (0 until chip.width).flatMap { x -> (0 until chip.height).map { y -> chip[x, y] } }
            .maxBy { kotlin.math.abs(it.luminance() - fill.luminance()) }
        return fill to number
    }

    private fun assertColour(what: String, want: Color, was: Color, tolerance: Float) {
        assertEquals("$what red", want.red, was.red, tolerance)
        assertEquals("$what green", want.green, was.green, tolerance)
        assertEquals("$what blue", want.blue, was.blue, tolerance)
    }

    private fun assertBadge(rir: Int, light: Pair<Long, Long>, dark: Pair<Long, Long>) {
        for ((isDark, colours) in listOf(false to light, true to dark)) {
            val (fill, number) = loggedChip(rir, isDark)
            assertColour("RIR $rir (dark=$isDark) fill", Color(colours.first), fill, 0.02f)
            // Antialiasing softens the number a little.
            assertColour("RIR $rir (dark=$isDark) number", Color(colours.second), number, 0.05f)
        }
    }

    @Test
    fun aLoggedZeroIsTheHotBadge() = assertBadge(0, light = 0xFFFAE9E8 to 0xFFA52F26, dark = 0xFF952A22 to 0xFFF2C8C5)

    @Test
    fun aLoggedTwoIsTheWarmBadge() = assertBadge(2, light = 0xFFFBF3E6 to 0xFF7D5516, dark = 0xFF825917 to 0xFFF4E1C2)

    @Test
    fun aLoggedThreeIsTheCalmBadge() = assertBadge(3, light = 0xFFEAF4F3 to 0xFF366460, dark = 0xFF36635F to 0xFFD1E6E4)
}
