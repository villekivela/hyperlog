package com.hyperlog.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ink and violet's larger, rounder groups (#50): 32 dp outside, 8 dp joins, 4 dp apart. */
class SurfacesTest {
    @Test
    fun aLoneCardRoundsThirtyTwo() = assertEquals(RoundedCornerShape(32.dp), CardShape)

    @Test
    fun aGroupsOuterCornersRoundThirtyTwoAndItsJoinsEight() {
        assertEquals(RoundedCornerShape(32.dp, 32.dp, 8.dp, 8.dp), segmentShape(0, 3))
        assertEquals(RoundedCornerShape(8.dp), segmentShape(1, 3))
        assertEquals(RoundedCornerShape(8.dp, 8.dp, 32.dp, 32.dp), segmentShape(2, 3))
        assertEquals(RoundedCornerShape(32.dp), segmentShape(0, 1))
    }

    @Test
    fun aGroupsRowsSitFourApart() = assertEquals(4.dp, SegmentGap)
}
