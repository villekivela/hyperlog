package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The pad's pin-stack strip says how to load the weight typed. */
@RunWith(RobolectricTestRunner::class)
class StackLoadViewTest {
    @get:Rule
    val compose = createComposeRule()

    private val stack = LadderSpec.PinStack.steps(WeightUnit.Kg, start = 5.0, step = 5.0, top = 100.0, builtIn = listOf(2.5, 2.5))

    @Test
    fun aPlateOnThePinIsPartOfTheLoading() {
        compose.setContent { HyperlogTheme { StackLoad(stack.load(41.25, listOf(1.25, 2.5)), 41.25) } }
        compose.onNodeWithContentDescription("Load pin 40 + 1.25 on the pin for 41.25 kg").assertIsDisplayed()
    }

    @Test
    fun builtInAddOnsAreCounted() {
        compose.setContent { HyperlogTheme { StackLoad(LadderSpec.PinStack.steps(WeightUnit.Kg, 10.0, 10.0, 100.0, builtIn = listOf(2.5, 2.5)).load(45.0, emptyList()), 45.0) } }
        compose.onNodeWithContentDescription("Load pin 40 + 2 add-ons for 45 kg").assertIsDisplayed()
    }

    @Test
    fun aWeightOffTheStackSaysSo() {
        compose.setContent { HyperlogTheme { StackLoad(stack.load(41.0, emptyList()), 41.0) } }
        compose.onNodeWithContentDescription("41 kg isn’t on this stack").assertIsDisplayed()
    }
}
