package com.hyperlog.app.ui.program

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.assertButtonOrder
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ProgramScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aRejectedFileListsEveryProblemAsPathThenMessage() {
        var retried = 0
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active = null,
                    errors = listOf(
                        """blocks[0].days[0].groups[1].slots[0].exercise: unknown exercise "bb_row"""",
                        """blocks[1].weekStart: must be 5, the week after the previous block""",
                    ),
                    onImport = { retried++ },
                    onCloseErrors = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Not imported").assertIsDisplayed()
        compose.onNodeWithText("2 problems. Fix them and import again; nothing changed.").assertIsDisplayed()
        compose.onNodeWithText("blocks[0].days[0].groups[1].slots[0].exercise").assertIsDisplayed()
        compose.onNodeWithText("""unknown exercise "bb_row"""").assertIsDisplayed()
        compose.onNodeWithText("Choose file").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun withNoProgramItOffersTheImport() {
        var imported = 0
        compose.setContent {
            HyperlogTheme { ProgramScreen(active = null, errors = null, onImport = { imported++ }, onCloseErrors = {}, onBack = {}) }
        }
        compose.onNodeWithText("Import program").performClick()
        assertEquals(1, imported)
    }

    @Test
    fun withAProgramRunningTheStarterIsInTheMenuAndAsksBeforeReplacing() {
        val program = (ProgramParser.parse(TestPrograms.twoDays("Mine"), AnyId) as ProgramParser.Result.Valid).program
        val starter = program.copy(title = "Hyperlog Full Body")
        var ran = 0
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active = ActiveProgram(1, program, Position(1, 0)),
                    errors = null,
                    onImport = {},
                    onCloseErrors = {},
                    onBack = {},
                    starter = starter,
                    onRunStarter = { ran++ },
                )
            }
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Run Hyperlog Full Body").performClick()
        compose.onNodeWithText("Replace Mine?").assertIsDisplayed()
        compose.assertButtonOrder("Cancel", "Replace")
        assertEquals(0, ran)
        compose.onNodeWithText("Replace").performClick()
        assertEquals(1, ran)
    }

    @Test
    fun withNoProgramTheStarterIsOfferedFirst() {
        val starter = (ProgramParser.parse(TestPrograms.twoDays("Hyperlog Full Body"), AnyId) as ProgramParser.Result.Valid).program
        var ran = 0
        var imported = 0
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(null, null, onImport = { imported++ }, onCloseErrors = {}, onBack = {}, starter = starter, onRunStarter = { ran++ })
            }
        }
        compose.onNodeWithText("Starter program").assertIsDisplayed()
        compose.onNodeWithText("Run this program").performClick()
        compose.onNodeWithText("Import program").performClick()
        assertEquals(1, ran)
        assertEquals(1, imported)
    }
}

private object AnyId : Set<String> by emptySet() {
    override fun contains(element: String) = true
}
