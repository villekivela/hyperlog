package com.hyperlog.app.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Reading starting weights out of a legacy Hyperlog backup (format 7.0). Synthetic data only. */
class LegacyBackupTest {
    private val library = setOf("barbell_bench_press", "db_bench_press", "db_incline_press", "db_lateral_raise", "leg_extension", "hack_squat", "pullup_wide_grip")
    private val merged = mapOf("incline_db_press" to "db_incline_press", "hack_squat_back_off" to "hack_squat")

    private fun backup(logs: String, settings: String = "[]") = """
        { "type": "full_backup", "version": "7.0", "exportedAt": "2026-10-12T10:00:00.000Z",
          "programs": [], "exercises": [], "strengthEvents": [], "foodEntries": [{"kcal": 1}],
          "settings": $settings, "workoutLogs": $logs }
    """

    private fun read(json: String) = (LegacyBackup.startingWeights(json, library, merged) as LegacyBackup.Result.Read).weights.associateBy { it.exerciseId }

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun theLatestSessionsTopWorkSetWithRpeAsRir() {
        val weights = read(
            backup(
                """[
                { "id": 1, "date": "2026-09-01T15:00:00.000Z", "exercises": [
                  { "slotId": "s", "exerciseId": "barbell_bench_press", "exerciseName": "Bench", "sets": [
                    { "setType": "work", "weight": 70, "reps": 6, "completed": true, "rpe": 9 } ] } ] },
                { "id": 2, "date": "2026-09-12T15:00:00.000Z", "exercises": [
                  { "slotId": "s", "exerciseId": "barbell_bench_press", "exerciseName": "Bench", "sets": [
                    { "setType": "warmup", "weight": 80, "reps": 3, "completed": true },
                    { "setType": "work", "weight": 60, "reps": 8, "completed": true, "rpe": 8 },
                    { "setType": "work", "weight": 60, "reps": 7, "completed": true, "rpe": 9 },
                    { "setType": "work", "weight": 50, "reps": 10, "completed": true, "rpe": 8.5 } ] } ] }
                ]""",
            ),
        )
        assertEquals(StartingWeight("barbell_bench_press", 60.0, 8, 2, at("2026-09-12T15:00:00.000Z")), weights["barbell_bench_press"])
    }

    @Test
    fun aHighRepSetIsPassedOverForOneTheEstimateCanUse() {
        val weights = read(
            backup(
                """[
                { "id": 1, "date": "2026-09-12T15:00:00.000Z", "exercises": [
                  { "slotId": "s", "exerciseId": "barbell_bench_press", "exerciseName": "Bench", "sets": [
                    { "setType": "work", "weight": 50, "reps": 9, "completed": true },
                    { "setType": "amrap", "weight": 50, "reps": 20, "completed": true } ] },
                  { "slotId": "l", "exerciseId": "db_lateral_raise", "exerciseName": "Raise", "sets": [
                    { "setType": "work", "weight": 8, "reps": 20, "completed": true },
                    { "setType": "work", "weight": 8, "reps": 15, "completed": true } ] } ] }
                ]""",
            ),
        )
        assertEquals(9, weights.getValue("barbell_bench_press").reps)
        // With only high-rep sets, the best of them.
        assertEquals(20, weights.getValue("db_lateral_raise").reps)
    }

    @Test
    fun theFiveDumbbellTotalsAreHalvedAndOldIdsMapToKeptOnes() {
        val weights = read(
            backup(
                """[
                { "id": 1, "date": "2026-09-12T15:00:00.000Z", "exercises": [
                  { "slotId": "a", "exerciseId": "db_bench_press", "exerciseName": "x", "sets": [
                    { "setType": "work", "weight": 50, "reps": 10, "completed": true, "rpe": 8 } ] },
                  { "slotId": "b", "exerciseId": "incline_db_press", "exerciseName": "x", "sets": [
                    { "setType": "amrap", "weight": 45, "reps": 9, "completed": true } ] },
                  { "slotId": "c", "exerciseId": "db_lateral_raise", "exerciseName": "x", "sets": [
                    { "setType": "work", "weight": 10, "reps": 15, "completed": true, "rpe": 10 } ] } ] }
                ]""",
            ),
        )
        assertEquals(25.0, weights.getValue("db_bench_press").kg, 0.0)
        assertEquals(22.5, weights.getValue("db_incline_press").kg, 0.0)
        assertEquals(null, weights.getValue("db_incline_press").rir)
        // Logged per dumbbell already.
        assertEquals(10.0, weights.getValue("db_lateral_raise").kg, 0.0)
        assertEquals(0, weights.getValue("db_lateral_raise").rir)
    }

    @Test
    fun machineWeightsAddedOverABaseIncludeTheBase() {
        val weights = read(
            backup(
                """[
                { "id": 1, "date": "2026-09-12T15:00:00.000Z", "exercises": [
                  { "slotId": "a", "exerciseId": "hack_squat_back_off", "exerciseName": "x", "sets": [
                    { "setType": "work", "weight": 40, "reps": 10, "completed": true, "rpe": 8, "machineWeightMode": "added" } ] },
                  { "slotId": "b", "exerciseId": "leg_extension", "exerciseName": "x", "sets": [
                    { "setType": "work", "weight": 45, "reps": 12, "completed": true, "rpe": 9, "machineWeightMode": "added" } ] } ] }
                ]""",
                settings = """[ { "key": "machine_base_weight_hack_squat_back_off", "value": 37.5 }, { "key": "current_week" } ]""",
            ),
        )
        assertEquals(77.5, weights.getValue("hack_squat").kg, 0.0)
        assertEquals(45.0, weights.getValue("leg_extension").kg, 0.0)
    }

    @Test
    fun exercisesNotInTheLibraryAndSetsWithoutRepsAreSkipped() {
        val weights = read(
            backup(
                """[
                { "id": 1, "date": "2026-09-12T15:00:00.000Z", "exercises": [
                  { "slotId": "a", "exerciseId": "gone_exercise", "exerciseName": "x", "sets": [
                    { "setType": "work", "weight": 40, "reps": 10, "completed": true } ] },
                  { "slotId": "b", "exerciseId": "pullup_wide_grip", "exerciseName": "x", "sets": [
                    { "setType": "work", "weight": 0, "reps": 0, "completed": true },
                    { "setType": "work", "weight": 0, "reps": 9, "completed": true, "rpe": 9 } ] } ] }
                ]""",
            ),
        )
        assertEquals(setOf("pullup_wide_grip"), weights.keys)
        assertEquals(StartingWeight("pullup_wide_grip", 0.0, 9, 1, at("2026-09-12T15:00:00.000Z")), weights["pullup_wide_grip"])
    }

    @Test
    fun anythingButAFullBackupIsRejected() {
        assertTrue(LegacyBackup.startingWeights("""{ "format": 1, "title": "A program" }""", library, merged) is LegacyBackup.Result.Invalid)
        assertTrue(LegacyBackup.startingWeights("not json", library, merged) is LegacyBackup.Result.Invalid)
    }
}
