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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A logged RIR on the logging screen is the badge History shows: a white number on #D14237,
 * #9D6B1C or #447E79, in light and dark (#46).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoggedRirColourTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    /** The fill at the left edge of the one logged chip in the set table, with set 1 logged at [rir], and its number's colour. */
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
        // The number is the chip's lightest pixel.
        val number = (0 until chip.width).flatMap { x -> (0 until chip.height).map { y -> chip[x, y] } }.maxBy { it.luminance() }
        return chip[3, chip.height / 2] to number
    }

    private fun assertBadge(expected: Long, rir: Int) {
        for (dark in listOf(false, true)) {
            val (fill, number) = loggedChip(rir, dark)
            val want = Color(expected)
            assertEquals("RIR $rir (dark=$dark) red", want.red, fill.red, 0.02f)
            assertEquals("RIR $rir (dark=$dark) green", want.green, fill.green, 0.02f)
            assertEquals("RIR $rir (dark=$dark) blue", want.blue, fill.blue, 0.02f)
            assertTrue("RIR $rir (dark=$dark) number is white, was $number", number.red > 0.95f && number.green > 0.95f && number.blue > 0.95f)
        }
    }

    @Test
    fun aLoggedZeroIsTheHotBadge() = assertBadge(0xFFD14237, 0)

    @Test
    fun aLoggedTwoIsTheWarmBadge() = assertBadge(0xFF9D6B1C, 2)

    @Test
    fun aLoggedThreeIsTheCalmBadge() = assertBadge(0xFF447E79, 3)
}
