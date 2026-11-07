package com.hyperlog.app.ui.theme

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/** One size for a group of labels: the largest every one fits, and never under the floor (#273). */
class TextFitTest {
    @Test
    fun aGroupThatFitsKeepsItsFullSize() = assertEquals(14.sp, fitSize(14.sp, 12.sp) { true })

    @Test
    fun itStepsDownToTheLargestSizeThatFits() = assertEquals(12.5.sp, fitSize(14.sp, 10.sp) { it.value <= 12.7f })

    @Test
    fun itStopsAtTheFloor() = assertEquals(12.sp, fitSize(14.sp, 12.sp) { false })
}
