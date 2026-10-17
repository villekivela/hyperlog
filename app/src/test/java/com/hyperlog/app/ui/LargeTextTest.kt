package com.hyperlog.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.session.PastSession
import com.hyperlog.app.ui.gym.GymScreen
import com.hyperlog.app.ui.session.PastSessionScreen
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screens at the owner's system text size (115 %) on a phone a little narrower than theirs: what
 * the phone pass (#58) found cut short stays whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
// Real text measuring, so cut-short text shows.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.15f)) { HyperlogTheme { content() } }
    }

    private fun SemanticsNodeInteraction.cutShort(): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.single()
        return layout.hasVisualOverflow || (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
    }

    @Test
    fun aPastSessionsTitleWrapsRatherThanBeingCut() {
        val title = "Day C — Bench volume, leg press, pulldown"
        show { PastSessionScreen(PastSession(1, 0, 3_600_000, title, "Kalliokoski", emptyList()), onOpen = {}, onBack = {}) }
        assertFalse("The title is cut short", compose.onNodeWithText(title, useUnmergedTree = true).cutShort())
    }

    @Test
    fun aGymsPlatesRowShowsEveryPlate() {
        show { GymScreen(Gym(1, "Kalliokoski", emptyList()), onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {}) }
        assertFalse("The plates are cut short", compose.onNodeWithText("8×20 2×15 2×10 2×5 2×2.5 2×1.25", useUnmergedTree = true).cutShort())
    }
}
