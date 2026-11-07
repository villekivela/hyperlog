package com.hyperlog.app.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** A past session's preview ends on a whole name and a count, never a cut word (#273). */
class PreviewLineTest {
    private val parts = listOf("Kalliokoski", "Barbell RDL", "Leg Extension", "Bayesian Cable Curl")

    private fun line(room: Int) = previewLine(parts, { "+$it more" }) { it.length <= room }

    @Test
    fun everythingThatFitsShows() = assertEquals("Kalliokoski · Barbell RDL · Leg Extension · Bayesian Cable Curl", line(100))

    @Test
    fun itEndsOnAWholeNameWithACount() = assertEquals("Kalliokoski · Barbell RDL · +2 more", line(40))

    @Test
    fun theFirstPartStaysWhateverTheRoom() = assertEquals("Kalliokoski · +3 more", line(5))

    @Test
    fun oneNameNeedsNoCount() = assertEquals("Kalliokoski", previewLine(listOf("Kalliokoski"), { "+$it more" }) { false })
}
