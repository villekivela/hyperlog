package com.hyperlog.app.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.theme.techniqueColor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Program plan text and the technique sheet tint a technique's symbol in its colour (SPEC, Palette → Technique colours). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TechniqueTextColourTest {
    @get:Rule
    val compose = createComposeRule()

    private val techniques = listOf(
        Technique.Dropset(), Technique.MyoReps(), Technique.LengthenedPartials(),
        Technique.IsoHold(seconds = 20, position = HoldPosition.Stretch), Technique.Failure,
    )

    private fun near(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue) < 0.06f

    private fun pixels(dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit): List<Color> {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { Box(Modifier.testTag("shown")) { content() } } }
        val image = compose.onNodeWithTag("shown").captureToImage().toPixelMap()
        return (0 until image.width).flatMap { x -> (0 until image.height).map { y -> image[x, y] } }
    }

    private fun assertEveryTechniqueShows(dark: Boolean, shown: List<Color>) {
        for (t in techniques) {
            val n = shown.count { near(it, techniqueColor(t.kind, dark)) }
            assertTrue("${t.kind} (dark=$dark) shows in $n pixels", n > 30)
        }
    }

    private fun plan(dark: Boolean) {
        val plan = techniques.map { SetPrescription(RepRange(8, 10), 2, 120, technique = it) }
        assertEveryTechniqueShows(dark, pixels(dark) { SetSummary(plan) })
    }

    private fun sheet(dark: Boolean) = assertEveryTechniqueShows(dark, pixels(dark) { SetSheet(1, null, null, {}, {}) })

    @Test
    fun thePlanTextTintsEachTechniqueSymbolInItsColourInLight() = plan(dark = false)

    @Test
    fun thePlanTextTintsEachTechniqueSymbolInItsColourInDark() = plan(dark = true)

    @Test
    fun theTechniqueSheetTintsEachTechniqueSymbolInItsColourInLight() = sheet(dark = false)

    @Test
    fun theTechniqueSheetTintsEachTechniqueSymbolInItsColourInDark() = sheet(dark = true)
}
