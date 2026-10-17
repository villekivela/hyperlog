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
    fun theBarbellAndSpecialtyBarsShowTheirWeightOnTheGymsPlates() {
        // Their weight dialog holds a text field, which Robolectric can't idle in a dialog window: checked on the phone.
        compose.setContent {
            HyperlogTheme {
                GymsScreen(
                    emptyList(),
                    bars = mapOf(Equipment.Barbell to 20.0, Equipment.EzBar to 10.0, Equipment.TrapBar to 25.0, Equipment.SafetyBar to 30.0),
                    onAddGym = {}, onOpenGym = {},
                    onBar = { _, _ -> }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("EZ bar").assertHasClickAction()
        compose.onNodeWithText("Trap bar").assertExists()
        compose.onNodeWithText("Barbell").assertHasClickAction()
        compose.onNodeWithText("20 kg bar, the gym’s plates").assertExists()
        compose.onNodeWithText("25 kg bar, the gym’s plates").assertExists()
        compose.onNodeWithText("30 kg bar, the gym’s plates").assertExists()
    }

    @Test
    fun theGymHasNoUsualDaysTrainingDaysAreYours() {
        compose.setContent {
            HyperlogTheme {
                GymScreen(Gym(1, "Central", emptyList()), onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Dumbbells").assertExists()
        compose.onNodeWithText("Usual days").assertDoesNotExist()
        compose.onNodeWithContentDescription("Monday").assertDoesNotExist()
    }

    @Test
    fun addEquipmentIsReachableWithoutScrollingOnAGymWithManyItems() {
        var added = 0
        val items = (1..30).map { com.hyperlog.app.gym.EquipmentItem(it.toLong(), 1, "Machine $it", LadderSpec.DefaultPinStack) }
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", items),
                    onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = { added++ }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Add equipment").assertIsDisplayed().performClick()
        assertEquals(1, added)
    }

    @Test
    fun theGymsListKeepsItsAddAndImportActions() {
        compose.setContent {
            HyperlogTheme {
                GymsScreen(
                    listOf(Gym(1, "Central", emptyList())), bars = emptyMap(),
                    onAddGym = {}, onOpenGym = {}, onBar = { _, _ -> }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Add gym").assertHasClickAction()
        compose.onNodeWithText("Import gym").assertHasClickAction()
    }

    @Test
    fun theGymsListSaysWhatEachGymHolds() {
        // Every gym has its dumbbells, plates and pin plates; its items are machines and cable stations.
        val press = com.hyperlog.app.gym.EquipmentItem(1, 1, "Shoulder Press", LadderSpec.DefaultPinStack)
        compose.setContent {
            HyperlogTheme {
                GymsScreen(
                    listOf(Gym(1, "Kalliokoski", listOf(press)), Gym(2, "Hotel", emptyList())), bars = emptyMap(),
                    onAddGym = {}, onOpenGym = {}, onBar = { _, _ -> }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("1 machine · dumbbells and plates").assertExists()
        compose.onNodeWithText("Dumbbells and plates").assertExists()
    }

    @Test
    fun aGymKeepsItsDumbbellRow() {
        var dumbbells = 0
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", emptyList()),
                    onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
                    onDumbbells = { dumbbells++ },
                )
            }
        }
        compose.onNodeWithText("Dumbbells").performClick()
        assertEquals(1, dumbbells)
    }

    @Test
    fun aGymListsItsEquipmentUnderAHeaderForEachKind() {
        val gym = Gym(
            1, "Central",
            items = listOf(
                com.hyperlog.app.gym.EquipmentItem(1, 1, "Pec deck", LadderSpec.DefaultPinStack),
                com.hyperlog.app.gym.EquipmentItem(2, 1, "Leg press", LadderSpec.Plates(0.0, listOf(20.0))),
            ),
            kit = listOf(
                com.hyperlog.app.gym.EquipmentItem(3, 1, "EZ bar", LadderSpec.Explicit(listOf(10.0)), kind = "bar:ez_bar"),
                com.hyperlog.app.gym.EquipmentItem(4, 1, "Flat bench", LadderSpec.Explicit(emptyList()), kind = "rack"),
            ),
        )
        compose.setContent {
            HyperlogTheme {
                GymScreen(gym, onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {})
            }
        }
        val order = listOf("Stack machines", "Pec deck", "Plate-loaded machines", "Leg press", "Bars", "EZ bar", "Racks and benches", "Flat bench", "Dumbbells and plates", "Dumbbells")
        val tops = order.map { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun aGymsBarsOpenTheirSetupButItsRacksDoNot() {
        var opened: Long? = null
        val gym = Gym(
            1, "Central", emptyList(),
            kit = listOf(
                com.hyperlog.app.gym.EquipmentItem(3, 1, "EZ bar", LadderSpec.Explicit(listOf(10.0)), kind = "bar:ez_bar"),
                com.hyperlog.app.gym.EquipmentItem(4, 1, "Flat bench", LadderSpec.Explicit(emptyList()), kind = "rack"),
            ),
        )
        compose.setContent {
            HyperlogTheme {
                GymScreen(gym, onRename = {}, onDelete = {}, onOpenItem = { opened = it }, onAddItem = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Flat bench").performClick()
        assertEquals(null, opened)
        compose.onNodeWithText("EZ bar").performClick()
        assertEquals(3L, opened)
    }

    @Test
    fun aGymsPlatesShowAsPairsPerSizeAndChangeACountAtATime() {
        var saved: List<Double>? = null
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Central", emptyList()),
                    onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
                    onPlates = { saved = it },
                )
            }
        }
        compose.onNodeWithText("8×20 2×15 2×10 2×5 2×2.5 2×1.25").performClick()
        compose.onNodeWithContentDescription("One more pair of 25 kg").performClick()
        compose.onNodeWithContentDescription("One more pair of 25 kg").performClick()
        compose.onNodeWithContentDescription("One pair fewer of 1.25 kg").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(
            mapOf(25.0 to 2, 20.0 to 8, 15.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 1),
            saved!!.groupingBy { it }.eachCount(),
        )
    }

    @Test
    fun everyItemOffersRenameAndDeleteAndDeleteAsksFirst() {
        var deleted: com.hyperlog.app.gym.EquipmentItem? = null
        val bench = com.hyperlog.app.gym.EquipmentItem(4, 1, "Flat bench", LadderSpec.Explicit(emptyList()), kind = "rack")
        val gym = Gym(1, "Central", items = listOf(com.hyperlog.app.gym.EquipmentItem(1, 1, "Pec deck", LadderSpec.DefaultPinStack)), kit = listOf(bench))
        compose.setContent {
            HyperlogTheme {
                GymScreen(gym, onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {}, onDeleteItem = { deleted = it })
            }
        }
        compose.onNodeWithContentDescription("More for Pec deck").assertHasClickAction()
        compose.onNodeWithContentDescription("More for Flat bench").performClick()
        compose.onNodeWithText("Rename").assertHasClickAction()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete Flat bench?").assertExists()
        compose.onNodeWithText("Sessions done on it keep it in their history.").assertExists()
        assertEquals(null, deleted)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(bench, deleted)
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
                    onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
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
                    onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
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
                    onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {},
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
                    emptyList(), bars = emptyMap(),
                    onAddGym = {}, onOpenGym = {}, onBar = { _, _ -> }, onBack = {},
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
