package com.hyperlog.app.ui.coach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Past two Changes in force, the rest fold under "N more in force", so the words stay in view. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ChangesInForceFoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val today = LocalDate.of(2026, 10, 15)

    private fun record(id: Long) = ChangeRecord(
        AcceptedChange(id, Change("Change $id", Scope.RestOfProgram, listOf(Operation.AdjustRir(null, 1))), today, Position(1, 0), Position(1, 0), null),
        acceptedAt = id, revertedAt = null,
    )

    @Test
    fun aThirdChangeFoldsUntilOpened() {
        val active = ActiveProgram(1, program, Position(1, 0), (1L..3L).map(::record), today = today)
        var opened: Long? = null
        compose.setContent {
            HyperlogTheme { CoachScreen(active, TextFieldValue(), onWords = {}, onBack = {}, onOpenChange = { opened = it }) }
        }
        // Newest first.
        compose.onNodeWithText("Change 3").assertIsDisplayed()
        compose.onNodeWithText("Change 2").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Change 1")).fetchSemanticsNodes().size)
        compose.onNodeWithText("In your words").assertIsDisplayed()

        compose.onNodeWithText("1 more in force").performClick()
        compose.onNodeWithText("Change 1").assertExists()

        compose.onNodeWithText("Change 3").performClick()
        assertEquals(3L, opened)
    }
}
