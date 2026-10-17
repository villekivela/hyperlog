package com.hyperlog.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.Group
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.interFigures
import com.hyperlog.app.week.WeekdayPlan
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Program's line for your training days: `3 days a week · Mon Wed Fri`, or `Set your training days`. */
@Composable
internal fun trainingDaysLine(plan: WeekdayPlan): String =
    if (plan.days.isEmpty()) stringResource(R.string.training_days_none)
    else pluralStringResource(
        R.plurals.training_days_line, plan.days.size, plan.days.size,
        plan.days.sorted().joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) },
    )

/**
 * Your training days (SPEC, Training days): the seven weekdays as toggles and, with two or more
 * gyms, each day that's on with the gym it's at. Every change applies at once.
 */
@Composable
internal fun TrainingDaysSheetContent(plan: WeekdayPlan, gyms: List<Gym>, onChange: (WeekdayPlan) -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val several = gyms.size >= 2
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(stringResource(R.string.training_days_title), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(if (several) R.string.training_days_body_gyms else R.string.training_days_body),
            Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        WeekdayToggles(plan.days, Modifier.padding(horizontal = 24.dp)) { day, on ->
            onChange(WeekdayPlan(if (on) plan.gyms + (day to null) else plan.gyms - day))
        }
        if (several && plan.days.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Group(color = Tones.sheetCard) {
                plan.days.sorted().forEach { day ->
                    DayGym(day, plan.gyms[day]?.let { id -> gyms.firstOrNull { it.id == id } }, gyms) { gym ->
                        onChange(WeekdayPlan(plan.gyms + (day to gym)))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDone, Modifier.align(Alignment.End).padding(end = 16.dp)) { Text(stringResource(R.string.done_close)) }
    }
}

/** One training day and its gym: a menu of the gyms, and Any gym for none named. */
@Composable
private fun DayGym(day: DayOfWeek, gym: Gym?, gyms: List<Gym>, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val gymName = gym?.name ?: stringResource(R.string.training_days_any_gym)
    val described = stringResource(R.string.training_days_gym_on, dayName, gymName)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box {
            TextButton(onClick = { open = true }, Modifier.semantics { contentDescription = described }) {
                Text(interFigures(gymName))
                Spacer(Modifier.size(4.dp))
                SymbolIcon(Symbol.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                gyms.forEach { g ->
                    DropdownMenuItem(text = { Text(interFigures(g.name)) }, onClick = { open = false; onPick(g.id) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.training_days_any_gym)) }, onClick = { open = false; onPick(null) })
            }
        }
    }
}

/** The seven weekdays as round toggles, Monday first, filled when on, spread to the edges (a 360 dp phone fits all seven). */
@Composable
internal fun WeekdayToggles(days: Set<DayOfWeek>, modifier: Modifier = Modifier, onToggle: (DayOfWeek, Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = day in days
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (on) cs.primary else Color.Transparent)
                    .border(1.dp, if (on) cs.primary else cs.outlineVariant, CircleShape)
                    .toggleable(on, role = Role.Checkbox) { onToggle(day, it) }
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
        }
    }
}
