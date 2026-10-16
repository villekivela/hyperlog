package com.hyperlog.app.coach

import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.library.Muscles

/**
 * Build me a workout's options on Coach (SPEC, Coaching → A workout's options): the effort, the
 * time for the whole session, and the muscles to focus on (none: a balanced full-body day).
 * Together with the gym they are a whole request; words are optional.
 */
data class WorkoutOptions(
    val effort: Effort = Effort.Normal,
    /** The whole session, warm-ups and rest included: one of [Times]. */
    val minutes: Int = 60,
    /** Muscles as the library names them, in the order picked. */
    val focus: List<String> = emptyList(),
) {
    enum class Effort { Easy, Normal, Hard }

    companion object {
        /** The times offered, in minutes. */
        val Times = listOf(45, 60, 75, 90)

        /** How many muscles the focus marks with their sets. */
        const val MARKED = 3

        /** The weeks before this one that count as trained lately: with this one, the last 4 weeks and today's. */
        private const val LATELY = 4L

        /** `45 min`, `1 h`, `1 h 15`, `1 h 30`. */
        fun timeText(minutes: Int): String = when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60}"
        }

        /**
         * The [MARKED] muscles with the fewest sets this week among those trained lately (this week
         * or the 4 before), fewest first, ties in body order, each with this week's sets as History
         * counts them. [weeks] is History's weekly sets, newest first or not.
         */
        fun lowest(weeks: List<WeekSets>): List<Pair<String, Double>> {
            val thisWeek = weeks.maxByOrNull { it.start } ?: return emptyList()
            val lately = weeks.filter { it.start >= thisWeek.start.minusWeeks(LATELY) }
                .flatMap { w -> w.sets.filterValues { it > 0 }.keys }.toSet()
            fun order(m: String) = Muscles.indexOf(m).let { if (it < 0) Int.MAX_VALUE else it }
            return lately.map { it to (thisWeek.sets[it] ?: 0.0) }
                .sortedWith(compareBy<Pair<String, Double>> { it.second }.thenBy { order(it.first) })
                .take(MARKED)
        }
    }
}
