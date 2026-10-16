package com.hyperlog.app.ui.history

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.history.TrendPoint
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.ui.session.RirChipLoggedTag
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An exercise's progress page lists its sessions with their top sets' chips in one column (#46). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrendRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    @Test
    fun theTopSetsChipsLineUpInOneColumn() {
        val day = LocalDate.of(2026, 10, 1)
        val points = listOf(
            TrendPoint(1, day, LoggedSet(80.0, 5, 0, 0), 92.0),
            TrendPoint(2, day.plusDays(3), LoggedSet(82.5, 8, 0, 1), 100.0),
            TrendPoint(3, day.plusDays(7), LoggedSet(100.0, 10, 3, 3), 130.0),
        )
        compose.setContent {
            HyperlogTheme { ExerciseTrendScreen(ExerciseTrend(bench, listOf(TrendLine(null, null, points))), hasBodyweight = false, onOpenSession = {}, onBack = {}) }
        }
        val chips = compose.onAllNodes(hasTestTag(RirChipLoggedTag) and hasAnyAncestor(hasTestTag(TrendRowTag)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(3, chips.size)
        val rights = chips.map { with(compose.density) { it.boundsInRoot.right.toDp() } }
        assertTrue("chips are laid out: $rights", rights.all { it > 0.dp })
        rights.forEach { assertEquals("chip right edges $rights", rights.first().value, it.value, 0.5f) }
    }
}
