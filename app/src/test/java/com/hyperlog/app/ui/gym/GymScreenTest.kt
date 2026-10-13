package com.hyperlog.app.ui.gym

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class GymScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theSpecialtyBarsShowTheirWeightUnderTheSharedPlates() {
        // Their weight dialog holds a text field, which Robolectric can't idle in a dialog window: checked on the phone.
        compose.setContent {
            HyperlogTheme {
                GymsScreen(
                    emptyList(), LadderSpec.DefaultPlates,
                    bars = mapOf(Equipment.EzBar to 10.0, Equipment.TrapBar to 25.0, Equipment.SafetyBar to 30.0),
                    onAddGym = {}, onOpenGym = {}, onPlates = {},
                    onBar = { _, _ -> }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("EZ bar").assertHasClickAction()
        compose.onNodeWithText("Trap bar").assertExists()
        compose.onNodeWithText("25 kg bar, the shared plates").assertExists()
        compose.onNodeWithText("30 kg bar, the shared plates").assertExists()
    }

    @Test
    fun theUsualDaysToggleOneWeekdayAtATime() {
        var days: Set<DayOfWeek>? = null
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", emptyList(), setOf(DayOfWeek.MONDAY)),
                    onRename = {}, onDelete = {}, onWeekdays = { days = it }, onOpenItem = {}, onAddItem = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Starting a session on these days picks Central.").assertExists()
        compose.onNodeWithContentDescription("Monday").assertIsOn()
        compose.onNodeWithContentDescription("Wednesday").assertIsOff().performClick()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), days)
        compose.onNodeWithContentDescription("Monday").performClick()
        assertEquals(emptySet<DayOfWeek>(), days)
    }

    @Test
    fun addEquipmentIsReachableWithoutScrollingOnAGymWithManyItems() {
        var added = 0
        val items = (1..30).map { com.hyperlog.app.gym.EquipmentItem(it.toLong(), 1, "Machine $it", LadderSpec.DefaultPinStack) }
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", items),
                    onRename = {}, onDelete = {}, onWeekdays = {}, onOpenItem = {}, onAddItem = { added++ }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Add equipment").assertIsDisplayed().performClick()
        assertEquals(1, added)
    }

    @Test
    fun theGymsListKeepsItsAddImportAndSharedPlatesActions() {
        var plates = 0
        compose.setContent {
            HyperlogTheme {
                GymsScreen(
                    listOf(Gym(1, "Central", emptyList())), LadderSpec.DefaultPlates, bars = emptyMap(),
                    onAddGym = {}, onOpenGym = {}, onPlates = { plates++ }, onBar = { _, _ -> }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Add gym").assertHasClickAction()
        compose.onNodeWithText("Import gym").assertHasClickAction()
        compose.onNodeWithText("Barbell plates").performClick()
        assertEquals(1, plates)
    }

    @Test
    fun aGymKeepsItsDumbbellRow() {
        var dumbbells = 0
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", emptyList()),
                    onRename = {}, onDelete = {}, onWeekdays = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
                    onDumbbells = { dumbbells++ },
                )
            }
        }
        compose.onNodeWithText("Dumbbells").performClick()
        assertEquals(1, dumbbells)
    }

    private val legExtension = com.hyperlog.app.library.Exercise(
        "leg_extension", "Leg Extension", Equipment.Machine, com.hyperlog.app.library.MovementPattern.KneeExtension,
        com.hyperlog.app.library.GripDemand.None, listOf("Quads"),
    )
    private val lateralRaise = legExtension.copy(id = "cable_lateral_raise", name = "Cable Lateral Raise", equipment = Equipment.Cable)

    @Test
    fun aGymOffersTheProgramsEquipmentAndAsksOnceWhichStack() {
        var added: LadderSpec? = null
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", emptyList()),
                    onRename = {}, onDelete = {}, onWeekdays = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
                    programSetup = com.hyperlog.app.gym.ProgramSetup(listOf(legExtension), listOf(lateralRaise), station = null),
                    programTitle = "Full Body",
                    onAddProgramEquipment = { added = it },
                )
            }
        }
        compose.onNodeWithText("Add the equipment your program uses").assertExists()
        compose.onNodeWithText("1 machine and a cable station for Full Body").assertExists()
        compose.onNodeWithText("lb stacks").performClick()
        compose.onNodeWithText("Add").performClick()
        assertEquals(LadderSpec.LbPinStack, added)
    }

    @Test
    fun aGymWithItsStackChosenAddsOnItWithoutAsking() {
        var added: LadderSpec? = null
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", emptyList(), defaultStack = LadderSpec.DefaultPinStack),
                    onRename = {}, onDelete = {}, onWeekdays = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
                    programSetup = com.hyperlog.app.gym.ProgramSetup(listOf(legExtension, legExtension.copy(id = "pec_deck", name = "Pec Deck")), emptyList(), station = null),
                    programTitle = "Full Body",
                    onAddProgramEquipment = { added = it },
                )
            }
        }
        compose.onNodeWithText("2 machines for Full Body").assertExists()
        compose.onNodeWithText("lb stacks").assertDoesNotExist()
        compose.onNodeWithText("Add").performClick()
        assertEquals(LadderSpec.DefaultPinStack, added)
    }

    @Test
    fun nothingToAddOffersNothing() {
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", emptyList()),
                    onRename = {}, onDelete = {}, onWeekdays = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
                    programSetup = com.hyperlog.app.gym.ProgramSetup(emptyList(), emptyList(), station = null),
                    programTitle = "Full Body",
                )
            }
        }
        compose.onNodeWithText("Add the equipment your program uses").assertDoesNotExist()
    }

    @Test
    fun importGymPicksAFileAndShowsItsProblems() {
        var picked = 0
        var errors by androidx.compose.runtime.mutableStateOf<List<String>?>(null)
        compose.setContent {
            HyperlogTheme {
                GymsScreen(
                    emptyList(), LadderSpec.DefaultPlates, bars = emptyMap(),
                    onAddGym = {}, onOpenGym = {}, onPlates = {}, onBar = { _, _ -> }, onBack = {},
                    onImport = { picked++ }, importErrors = errors, onCloseImportErrors = { errors = null },
                )
            }
        }
        compose.onNodeWithText("Import gym").performClick()
        assertEquals(1, picked)
        errors = listOf("equipment[0].serves[0]: unknown exercise \"nope\"")
        compose.onNodeWithText("Not imported").assertExists()
        compose.onNodeWithText("Choose file").performClick()
        assertEquals(2, picked)
    }
}
