package com.hyperlog.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Focus picks muscle groups (SPEC, Coaching → A workout's options): every library muscle has one. */
class MuscleGroupTest {
    @Test
    fun everyMuscleTheLibraryNamesHasAGroup() {
        val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
        val named = library.exercises.flatMap { it.muscles + it.secondary }.toSet() + Muscles
        named.forEach { assertTrue("$it has no group", MuscleGroup.of(it) != null) }
    }

    @Test
    fun theSevenGroupsInOrderEachCoverTheirMuscles() {
        assertEquals(listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Glutes", "Core"), MuscleGroup.entries.map { it.label })
        assertEquals(MuscleGroup.Legs, MuscleGroup.of("Hamstrings"))
        assertEquals(MuscleGroup.Back, MuscleGroup.of("Lats"))
        assertEquals(MuscleGroup.Shoulders, MuscleGroup.of("Rear Delts"))
        // No muscle is in two groups.
        val all = MuscleGroup.entries.flatMap { it.muscles }
        assertEquals(all.size, all.toSet().size)
    }
}
