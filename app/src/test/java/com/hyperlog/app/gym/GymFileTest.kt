package com.hyperlog.app.gym

import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.library.LibrarySeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek

/** A gym file is read through a strict validator, every problem with its path (docs/gym-format.md). */
class GymFileTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText()).exercises.associateBy { it.id }

    private fun parse(json: String) = GymFile.parse(json, library)

    private fun errors(json: String) = (parse(json) as GymFile.Result.Invalid).errors

    private fun valid(json: String) = (parse(json) as? GymFile.Result.Valid)?.gym ?: error("rejected: ${parse(json)}")

    private fun gym(equipment: String = "[]", extra: String = "") =
        """{ "format": "hyperlog-gym", "version": 1, "name": "Harbour"$extra, "equipment": $equipment }"""

    @Test
    fun theDocumentedExampleIsAValidGym() {
        val doc = File("../docs/gym-format.md").readText()
        val example = Regex("```json\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(doc).map { it.groupValues[1] }.first { "\"hyperlog-gym\"" in it }
        val gym = valid(example)
        assertEquals("Harbour Fitness", gym.name)
        assertTrue(gym.equipment.isNotEmpty())
    }

    @Test
    fun aMinimalGymIsJustItsName() {
        val gym = valid(gym())
        assertEquals("Harbour", gym.name)
        assertEquals(emptySet<DayOfWeek>(), gym.usualDays)
        assertEquals(null, gym.dumbbells)
    }

    @Test
    fun aGymsPlatesArePairsPerSizeAndDefaultToNone() {
        assertEquals(null, valid(gym()).plates)
        val gym = valid(gym(extra = """, "plates": [ { "kg": 20, "pairs": 3 }, { "kg": 10, "pairs": 1 }, { "kg": 5, "pairs": 0 } ]"""))
        assertEquals(listOf(20.0, 20.0, 20.0, 10.0), gym.plates)
    }

    @Test
    fun aPlateSizeIsListedOnceWithAWholeCount() {
        assertEquals(
            listOf("plates[1].kg: 20 kg is already listed", "plates[2].pairs: must be a whole number from 0 to 50", "plates[3]: unknown field \"count\"", "plates[3].pairs: is required"),
            errors(gym(extra = """, "plates": [ { "kg": 20, "pairs": 3 }, { "kg": 20, "pairs": 1 }, { "kg": 5, "pairs": 1.5 }, { "kg": 2.5, "count": 2 } ]""")),
        )
    }

    @Test
    fun aStackWithoutALadderIsAnUnconfirmedDefaultStack() {
        val item = valid(gym("""[ { "name": "Leg extension", "kind": "stack", "serves": ["leg_extension"] } ]""")).equipment.single()
        assertEquals(LadderSpec.DefaultPinStack, item.ladder)
        assertEquals(true, item.unconfirmed)
        assertEquals(listOf("leg_extension"), item.serves)
    }

    @Test
    fun aPinStackLadderReadsItsShorthandAddOnsAndLabels() {
        val item = valid(
            gym(
                """[ { "name": "Lat pulldown", "kind": "stack", "serves": ["lat_pulldown"],
                  "ladder": { "kind": "pin", "unit": "lb", "plates": "80 95 … 200", "labels": { "kg": 1, "cut": true }, "addOns": [2.5, 2.5], "pinPlates": false } } ]""",
            ),
        ).equipment.single()
        val stack = item.ladder as LadderSpec.PinStack
        assertEquals(WeightUnit.Lb, stack.unit)
        assertEquals(listOf(36.0, 43.0, 49.0), stack.pins.take(3))
        assertEquals(listOf(2.5, 2.5), stack.builtIn)
        assertEquals(false, item.unconfirmed)
    }

    @Test
    fun theRestOfTheGymComesThrough() {
        val gym = valid(
            gym(
                extra = """, "usualDays": ["monday", "thursday"], "pinPlates": [1.25, 2.5], "dumbbells": "1 2 … 10 12.5 15 … 45",
                  "bars": [ { "name": "Olympic bar", "kind": "barbell", "kg": 20 }, { "name": "Swiss bar", "kind": "swiss_bar", "kg": 15 } ]""",
            ),
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), gym.usualDays)
        assertEquals(listOf(1.25, 2.5), gym.pinPlates)
        assertEquals(24, gym.dumbbells!!.ladder().rungs.size)
        assertEquals(listOf("barbell", "swiss_bar"), gym.bars.map { it.kind })
    }

    @Test
    fun aPlateLoadedMachineDefaultsToTheGymsPlatesFromZero() {
        val item = valid(gym("""[ { "name": "Leg press", "kind": "plate_loaded", "serves": ["leg_press"] } ]""")).equipment.single()
        assertEquals(LadderSpec.Plates(0.0, emptyList()), item.ladder)
    }

    @Test
    fun aRackOrBenchHasNoLadder() {
        assertEquals(
            listOf("equipment[0].ladder: a rack or bench has no weights"),
            errors(gym("""[ { "name": "Rack", "kind": "rack_bench", "ladder": { "kind": "list", "kg": [5] } } ]""")),
        )
    }

    @Test
    fun everyProblemIsReportedWithItsPath() {
        val errors = errors(
            """{ "format": "hyperlog-gym", "version": 2, "nam": "x", "usualDays": ["funday"], "pinPlates": [-1],
               "equipment": [
                 { "name": "A", "kind": "stack", "serves": ["bb_row"] },
                 { "name": "B", "kind": "sled", "serves": ["leg_press"] },
                 { "name": "C", "kind": "stack", "serves": ["barbell_bench_press"] },
                 { "name": "D", "kind": "stack", "ladder": { "kind": "pin", "unit": "kg", "plates": "5 … 10" } },
                 { "name": "E", "kind": "stack", "cableStation": true, "serves": ["leg_extension"] },
                 { "name": "F", "kind": "stack", "cableStation": true, "serves": ["leg_extension"] }
               ] }""",
        )
        assertEquals(
            listOf(
                "unknown field \"nam\"",
                "version: must be 1",
                "name: is required",
                "usualDays[0]: \"funday\" is not a weekday",
                "pinPlates[0]: must be a weight above 0",
                "equipment[0].serves[0]: unknown exercise \"bb_row\"",
                "equipment[1].kind: must be stack, plate_loaded or rack_bench",
                "equipment[2].serves[0]: Barbell Bench Press is a free-weight exercise, not done on a machine",
                "equipment[3].ladder.plates: \"5 … 10\" isn't a stack: give two plates before …",
                "equipment[5].serves[0]: leg_extension is already served by equipment[4]",
                "equipment[5].cableStation: equipment[4] is already the cable station",
            ),
            errors,
        )
    }

    @Test
    fun aFileSetsOnlyWhatTheGymsSetupCanEdit() {
        // The pin plates dialog takes up to six, each up to 25 kg; a bar's weight field up to 100 kg.
        val errors = errors(
            gym(
                extra = """, "pinPlates": [0.5, 1, 1.25, 2.5, 2.5, 5, 30],
                    "bars": [{ "name": "Log", "kind": "axle_bar", "kg": 120 }]""",
            ),
        )
        assertEquals(
            listOf(
                "pinPlates[6]: must be a weight up to 25",
                "pinPlates: at most 6 plates",
                "bars[0].kg: must be a weight up to 100",
            ),
            errors,
        )
    }

    @Test
    fun aFileThatIsntAGymSaysSo() {
        assertEquals(listOf("not a Hyperlog gym file"), errors("""{ "format": "hyperlog-backup" }"""))
        assertTrue(errors("not json").single().startsWith("not valid JSON"))
    }
}
