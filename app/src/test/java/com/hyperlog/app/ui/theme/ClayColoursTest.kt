package com.hyperlog.app.ui.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The mannequin and its kits take their colours from the theme, by material (SPEC, Technique videos → The mannequin). */
class ClayColoursTest {
    private val clips = File("src/main/assets/mannequins").listFiles { f -> f.extension == "glb" }!!.toList()

    @Test
    fun everyMaterialAShippedClipIsDrawnInHasAColourInBothThemes() {
        val materials = clips.flatMap(::materialsOf).toSet() - "shadow"
        // The body and its joints, and the kits' five tones.
        assertEquals(setOf("body", "joint", "frame", "vinyl", "plate", "metal", "pulley"), materials)
        for (name in materials) {
            assertNotNull("$name in light", ClayColours.Light.colourOf(name))
            assertNotNull("$name in dark", ClayColours.Dark.colourOf(name))
        }
    }

    /** The names of a `.glb`'s materials, from its JSON chunk. */
    private fun materialsOf(glb: File): List<String> {
        val bytes = glb.readBytes()
        val length = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        val json = Json.parseToJsonElement(String(bytes, 20, length))
        return json.jsonObject["materials"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
    }
}
