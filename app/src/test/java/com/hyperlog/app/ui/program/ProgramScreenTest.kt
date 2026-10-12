package com.hyperlog.app.ui.program

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
