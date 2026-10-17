package com.hyperlog.app.ui.theme

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/** The owner's rule (#50): words in titles are Space Grotesk, numbers are always Inter. */
class InterFiguresInTitlesTest {
    private fun interRuns(text: AnnotatedString) =
        text.spanStyles.filter { it.item.fontFamily == Inter }.map { text.text.substring(it.start, it.end) }

    @Test
    fun aTitlesNumbersAreInterAndItsWordsAreNot() {
        val title = interFigures("Build · weeks 3–6")
        assertEquals("Build · weeks 3–6", title.text)
        assertEquals(listOf("3–6"), interRuns(title))
    }

    @Test
    fun decimalsTimesAndOpenEndsStayOneRun() {
        assertEquals(listOf("122.5", "1:30", "4+"), interRuns(interFigures("122.5 kg in 1:30, RIR 4+")))
    }

    @Test
    fun aTitleWithoutNumbersIsUntouched() {
        assertEquals(emptyList<String>(), interRuns(interFigures("Day A — Heavy bench, squat, pulldown")))
    }
}
