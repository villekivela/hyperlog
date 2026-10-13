package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.domain.StackReading
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The stack learned from typed weights is offered as a question, never applied by itself. */
@RunWith(RobolectricTestRunner::class)
class LadderOfferTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theOfferAsksAndAnswersEitherWay() {
        val offer = (StackReading.read(listOf(4.5, 9.1, 13.6), WeightUnit.Kg) as StackReading.Result.Read).spec
        var answer = ""
        compose.setContent {
            HyperlogTheme { LadderOffer(offer, "Leg extension", emptyList(), onTake = { answer = "use" }, onDecline = { answer = "keep" }) }
        }
        compose.onNodeWithText("Looks like 10 lb steps: use that?").assertIsDisplayed()
        compose.onNodeWithText("The weights typed on Leg extension fit 4.5–90.7 kg by 10 lb.").assertIsDisplayed()
        compose.onNodeWithText("Keep as typed").performClick()
        assertEquals("keep", answer)
        compose.onNodeWithText("Use").performClick()
        assertEquals("use", answer)
    }
}
