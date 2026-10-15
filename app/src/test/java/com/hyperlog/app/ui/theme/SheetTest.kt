package com.hyperlog.app.ui.theme

import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The reference phone: 1280 × 2856 px at 3.0 density.
@Config(qualifiers = "w410dp-h914dp-xxhdpi")
class SheetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun content(): SemanticsNode? =
        runCatching { compose.onNodeWithTag("content").fetchSemanticsNode() }.getOrNull()

    /** The sheet's window, which takes insets and keys. */
    private fun window(): View = (content()!!.root as View).rootView

    /**
     * Opens a sheet holding more than the screen does, frame by frame, with the phone's 204 px
     * status bar and 72 px navigation bar (Robolectric gives a window none). Calls [onFrame]
     * with the content's node on each frame it's shown.
     */
    private fun openTallSheet(
        frames: Int = 60,
        onDismiss: () -> Unit = {},
        list: @Composable () -> Unit = {
            Column(Modifier.testTag("content").verticalScroll(rememberScrollState())) {
                Text("Tall", Modifier.fillMaxWidth().height(3000.dp))
            }
        },
        onFrame: (SemanticsNode) -> Unit = {},
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent { HyperlogTheme { Sheet(onDismiss = onDismiss) { list() } } }
        while (content() == null) compose.mainClock.advanceTimeByFrame()
        val bars = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 204, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 72))
            .build()
        ViewCompat.dispatchApplyWindowInsets(window(), bars)
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            content()?.let(onFrame)
        }
    }

    @Test
    fun aSheetKeepsItsHeightWhileItOpens() {
        val heights = mutableListOf<Int>()
        openTallSheet { heights += it.size.height }

        assertEquals("content heights while opening", listOf(heights.last()), heights.drop(1).distinct())
    }

    @Test
    fun aSheetKeepsItsHeightWhileDraggedDownOverItsContent() {
        openTallSheet()
        val heights = mutableListOf<Int>()
        val list = compose.onNodeWithTag("content")
        list.performTouchInput { down(center) }
        repeat(30) {
            list.performTouchInput { moveBy(Offset(0f, 15f)) }
            compose.mainClock.advanceTimeByFrame()
            heights += content()!!.size.height
        }
        list.performTouchInput { up() }

        assertEquals("content heights while dragged", listOf(heights.first()), heights.distinct())
    }

    @Test
    fun aSheetIsDismissedByDraggingDownOverItsScrollingContent() {
        var dismissed = false
        openTallSheet(onDismiss = { dismissed = true })

        compose.onNodeWithTag("content").performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 200) }
        repeat(60) { compose.mainClock.advanceTimeByFrame() }

        assertTrue("dismissed", dismissed)
    }

    @Test
    fun aSheetHasTheMaterialDragHandle() {
        openTallSheet()

        compose.onNodeWithContentDescription("Drag handle").assertExists()
    }

    @Test
    fun backDismissesASheet() {
        var dismissed = false
        openTallSheet(onDismiss = { dismissed = true })

        window().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        window().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        repeat(60) { compose.mainClock.advanceTimeByFrame() }

        assertTrue("dismissed", dismissed)
    }

    @Test
    fun everySheetInTheAppIsThisOne() {
        val offenders = File("src/main/java").walk().filter { it.extension == "kt" }
            .filter { Regex("""\bModalBottomSheet\s*\(""").containsMatchIn(it.readText()) }
            .map { it.invariantSeparatorsPath }
            .filterNot { it.endsWith("/ui/theme/Sheet.kt") }.toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun aSheetIsDismissedByDraggingDownOverALazyList() {
        var dismissed = false
        openTallSheet(onDismiss = { dismissed = true }, list = {
            LazyColumn(Modifier.testTag("content")) { items(60) { Text("Row $it", Modifier.fillMaxWidth().height(56.dp)) } }
        })

        compose.onNodeWithTag("content").performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 200) }
        repeat(60) { compose.mainClock.advanceTimeByFrame() }

        assertTrue("dismissed", dismissed)
    }
}
