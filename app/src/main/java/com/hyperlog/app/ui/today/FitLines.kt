package com.hyperlog.app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.ui.session.Direction
import com.hyperlog.app.ui.session.color
import com.hyperlog.app.week.DayFit
import com.hyperlog.app.week.TimeOver
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal const val FitTimeTag = "fit-time"
internal const val FitSwapsTag = "fit-swaps"

/** The words a weekday goes by in the fit lines: `Saturday`. */
internal fun fullWeekday(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

/** The first answer of the check-in a time line starts: `Day C runs ~58 minutes but Saturday has 50.` */
@Composable
internal fun timeOverAnswer(day: String, over: TimeOver): String =
    stringResource(R.string.fit_time_answer, day, over.about, fullWeekday(over.weekday), over.has)

/**
 * How a planned day fits its training day (SPEC, The fit), under its numbers: the time in amber
 * (the progression's weight-down amber), tappable when [onTime] is given, and the swaps muted
 * under it. Each is left out when nothing is off. [day] is the day's own name (`Day C`).
 */
@Composable
internal fun FitLines(fit: DayFit, day: String, mutedColor: Color, onTime: ((String) -> Unit)? = null) {
    val time = fit.time
    val swaps = fit.swaps
    if (time == null && swaps == null) return
    Column {
        if (time != null) {
            val words = stringResource(R.string.fit_time, time.about, fullWeekday(time.weekday), time.has)
            val answer = timeOverAnswer(day, time)
            val amber = Direction.Down.color
            val modifier = if (onTime != null) Modifier.clickable(role = Role.Button) { onTime(answer) } else Modifier
            Box(modifier.testTag(FitTimeTag).heightIn(min = if (onTime != null) 48.dp else 24.dp).defaultMinSize(minWidth = 48.dp), contentAlignment = Alignment.CenterStart) {
                Text(words, style = MaterialTheme.typography.bodyMedium, color = amber)
            }
        }
        if (swaps != null) {
            Box(Modifier.testTag(FitSwapsTag).heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
                Text(pluralStringResource(R.plurals.fit_swaps, swaps.count, swaps.count, swaps.gym), style = MaterialTheme.typography.bodyMedium, color = mutedColor)
            }
        }
    }
}
