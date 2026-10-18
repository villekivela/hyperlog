package com.hyperlog.app.ui.today

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.WeekdayPlan
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** A deload day says so on Today's hero and planned cards (the owner's call on #58, issue 7). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class DeloadTagTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val sat17 = LocalDate.of(2026, 10, 17)

    private fun today(position: Position) = compose.setContent {
        HyperlogTheme {
            TodayScreen(
                sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                program = ActiveProgram(1, program, position, today = sat17),
                library = library.exercises, weekdayPlan = WeekdayPlan.None, sessions = emptyList(),
            )
        }
    }

    @Test
    fun theDeloadWeeksHeroIsTaggedDeload() {
        assertTrue(program.plannedDay(Position(12, 1))!!.deload)
        today(Position(12, 1))
        compose.onNode(hasTestTag(DeloadTagTag), useUnmergedTree = true).assertTextEquals("Deload")
    }

    @Test
    fun anOrdinaryWeeksHeroHasNoTag() {
        today(Position(7, 1))
        compose.onNode(hasTestTag(DeloadTagTag), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aPlannedDeloadDayIsTaggedToo() {
        compose.setContent { HyperlogTheme { PlannedCard(program.plannedDay(Position(12, 2))!!, sat17.plusDays(4), library = library.exercises, finished = emptyList()) } }
        compose.onNode(hasTestTag(DeloadTagTag), useUnmergedTree = true).assertTextEquals("Deload")
    }
}
