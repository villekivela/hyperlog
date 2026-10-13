package com.hyperlog.app.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sets this week per muscle (SPEC, Weekly sets per muscle): every muscle's name reads in full. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
// Real text measuring: the legacy mode's text has no width to cut off.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekSetsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun ellipsized(text: String): Boolean {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        return (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
    }

    @Test
    fun longMuscleNamesAreNotCutOff() {
        // Found on the phone: "Triceps Long H…" and "Triceps Lateral …".
        // The JVM's font is narrower than the phone's, so the text is set larger to make up for it.
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                WeekSets(mapOf("Chest" to 6, "Triceps Long Head" to 4, "Triceps Lateral Head" to 3))
            }
        }
        assertFalse(ellipsized("Triceps Long Head"))
        assertFalse(ellipsized("Triceps Lateral Head"))
    }
}
