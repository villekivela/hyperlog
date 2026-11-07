package com.hyperlog.app.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/design/colours.md holds only while no screen writes a colour of its own (#271). */
class ColourInventoryTest {
    private val scales = setOf("GraphitePalette.kt", "TechniqueColours.kt", "RirChip.kt", "Plates.kt", "Tokens.kt", "FitLines.kt")

    @Test
    fun onlyTheInventorysScalesWriteAColour() {
        val written = File("src/main/java/com/hyperlog/app/ui").walkTopDown()
            .filter { it.extension == "kt" && "Color(0x" in it.readText() }.map { it.name }.toSet()
        assertEquals(emptySet<String>(), written - scales)
    }

    /** Amber is a falling weight and nothing else: no screen borrows the trend's colour for another meaning (owner, #271). */
    @Test
    fun amberMeansOnlyAFallingWeight() {
        val borrowed = File("src/main/java/com/hyperlog/app/ui").walkTopDown()
            .filter { it.extension == "kt" && "Direction.Down.color" in it.readText() }.map { it.name }.toSet()
        assertEquals(emptySet<String>(), borrowed)
    }
}
