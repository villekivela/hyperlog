package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LadderPreviewTest {
    @Test
    fun aLongLadderShowsItsFirstRungsAndItsTop() {
        assertEquals("1.1 · 3.4 · 5.7 · 7.9 … 89.6 kg", LadderSpec.lbStack().ladder().preview())
    }

    @Test
    fun aShortLadderShowsEveryRung() {
        assertEquals("20 · 25 · 30 kg", WeightLadder(listOf(20.0, 25.0, 30.0)).preview())
    }
}
