package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The plate calculator reads the whole load: the plates per side and what they go on. */
@RunWith(RobolectricTestRunner::class)
class PlateLoadViewTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aBarsPlatesGoOnTheBar() {
        compose.setContent { HyperlogTheme { PlateLoad(LadderSpec.DefaultPlates, 90.0) } }
        compose.onNodeWithContentDescription("Plates per side: 1 × 20, 1 × 15, on a 20 kg bar").assertIsDisplayed()
    }

    @Test
    fun aPlateLoadedMachinesStartingWeightCountsTowardTheTotal() {
        // A leg press sled of 30 kg with pairs of 20 and 10: 90 kg is 30 on each side.
        compose.setContent { HyperlogTheme { PlateLoad(LadderSpec.Plates(30.0, listOf(20.0, 10.0)), 90.0, machine = true) } }
        compose.onNodeWithContentDescription("Plates per side: 1 × 20, 1 × 10, on a 30 kg machine").assertIsDisplayed()
    }

    @Test
    fun anEmptyMachineIsTheMachineAlone() {
        compose.setContent { HyperlogTheme { PlateLoad(LadderSpec.Plates(30.0, listOf(20.0)), 30.0, machine = true) } }
        compose.onNodeWithContentDescription("The machine alone, 30 kg").assertIsDisplayed()
    }

    @Test
    fun theWholeBarShowsBothSidesAndTheTotal() {
        compose.setContent { HyperlogTheme { WholeBar(LadderSpec.DefaultPlates, 100.0) } }
        compose.onNodeWithContentDescription("100 kg: a 20 kg bar with 2 × 20 on each side").assertIsDisplayed()
    }

    @Test
    fun theWholeMachineCountsItsStartingWeight() {
        compose.setContent { HyperlogTheme { WholeBar(LadderSpec.Plates(30.0, listOf(20.0, 10.0)), 90.0, machine = true) } }
        compose.onNodeWithContentDescription("90 kg: a 30 kg machine with 1 × 20, 1 × 10 on each side").assertIsDisplayed()
    }
}
