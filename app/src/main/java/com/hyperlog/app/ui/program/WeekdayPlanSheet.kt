package com.hyperlog.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.Group
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.week.WeekdayPlan
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Program's line for the weekday plan: `3 days a week · Mon Wed Fri`, or `Set your training days`. */
@Composable
internal fun weekdayPlanLine(plan: WeekdayPlan): String =
    if (plan.days.isEmpty()) stringResource(R.string.weekday_plan_none)
    else pluralStringResource(
        R.plurals.weekday_plan_line, plan.days.size, plan.days.size,
        plan.days.sorted().joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) },
    )

/**
 * The line under it, `4 days take turns`, with fewer or more training days than the program's week
 * has [days]; null when they match or none are set.
 */
@Composable
internal fun weekdayPlanTurns(plan: WeekdayPlan, days: Int?): String? =
    if (plan.days.isEmpty() || days == null || days == plan.days.size) null
    else stringResource(R.string.weekday_plan_take_turns, days)

/**
 * The weekday plan's sheet, "Training days" on screen (SPEC, Training days): the seven weekdays as
 * toggles, then each day that's on with, given two or more gyms, the gym it's at, and the minutes
 * you have. A day turned on with one gym is at it, so it keeps its gym when a second gym is added.
 * Every change applies at once.
 */
@Composable
internal fun WeekdayPlanSheetContent(plan: WeekdayPlan, gyms: List<Gym>, onChange: (WeekdayPlan) -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val several = gyms.size >= 2
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(stringResource(R.string.weekday_plan_title), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(if (several) R.string.weekday_plan_body_gyms else R.string.weekday_plan_body),
            Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        WeekdayToggles(plan.days, Modifier.padding(horizontal = 24.dp)) { day, on ->
            onChange(if (on) plan.copy(gyms = plan.gyms + (day to gyms.singleOrNull()?.id)) else WeekdayPlan(plan.gyms - day, plan.minutes - day))
        }
        if (plan.days.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Group(color = Tones.sheetCard) {
                plan.days.sorted().forEach { day ->
                    DayRow(day, onMinutes = { onChange(plan.copy(minutes = if (it == null) plan.minutes - day else plan.minutes + (day to it))) }, minutes = plan.minutes[day]) {
                        if (several) {
                            DayGym(day, plan.gyms[day]?.let { id -> gyms.firstOrNull { it.id == id } }, gyms) { gym ->
                                onChange(plan.copy(gyms = plan.gyms + (day to gym)))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDone, Modifier.align(Alignment.End).padding(end = 16.dp)) { Text(stringResource(R.string.done_close)) }
    }
}

/** One training day: its name, [gym] (the gym menu, with several gyms), and its minutes menu at the end. */
@Composable
private fun DayRow(day: DayOfWeek, minutes: Int?, onMinutes: (Int?) -> Unit, gym: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // As wide as the widest day name, so every row's gym gets the same room.
        Box(Modifier.padding(end = 8.dp)) {
            Text(DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), Modifier.alpha(0f).clearAndSetSemantics {}, style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false)
            Text(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { gym() }
        DayMinutes(day, minutes, onMinutes)
    }
}

/** A training day's minutes: `—` for no limit, then 30 to 120 in fives, read as `60 min`. */
@Composable
private fun DayMinutes(day: DayOfWeek, minutes: Int?, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val label: @Composable (Int?) -> String = { m -> m?.let { stringResource(R.string.weekday_plan_minutes, it) } ?: stringResource(R.string.weekday_plan_no_minutes) }
    val described = stringResource(R.string.weekday_plan_minutes_on, dayName, minutes?.let { stringResource(R.string.weekday_plan_minutes, it) } ?: stringResource(R.string.weekday_plan_no_limit))
    Box {
        TextButton(onClick = { open = true }, Modifier.semantics { contentDescription = described }) {
            // As wide as the widest choice, so every row's minutes, and the gym menus before them, line up.
            Box(contentAlignment = Alignment.CenterEnd) {
                Text(label(WeekdayPlan.Minutes.max()), Modifier.alpha(0f).clearAndSetSemantics {}, maxLines = 1)
                Text(label(minutes), maxLines = 1)
            }
            Spacer(Modifier.size(4.dp))
            SymbolIcon(Symbol.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<Int?>(null) + WeekdayPlan.Minutes).forEach { m ->
                // The minutes set are ticked, as a picked choice shows itself everywhere else.
                DropdownMenuItem(
                    text = { Text(label(m)) }, onClick = { open = false; onPick(m) },
                    trailingIcon = if (m == minutes) ({ SymbolIcon(Symbol.Check, null) }) else null,
                )
            }
        }
    }
}

/** A training day's gym: a menu of the gyms, and Any gym for none named. */
@Composable
private fun RowScope.DayGym(day: DayOfWeek, gym: Gym?, gyms: List<Gym>, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val gymName = gym?.name ?: stringResource(R.string.weekday_plan_any_gym)
    val described = stringResource(R.string.weekday_plan_gym_on, dayName, gymName)
    // The gym takes what the day name leaves and ends in an ellipsis, so the arrow always shows.
    Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = { open = true }, Modifier.semantics { contentDescription = described }) {
            Text(gymName, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(4.dp))
            SymbolIcon(Symbol.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            gyms.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.name) }, onClick = { open = false; onPick(g.id) },
                    trailingIcon = if (g.id == gym?.id) ({ SymbolIcon(Symbol.Check, null) }) else null,
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.weekday_plan_any_gym)) }, onClick = { open = false; onPick(null) },
                trailingIcon = if (gym == null) ({ SymbolIcon(Symbol.Check, null) }) else null,
            )
        }
    }
}

/** The seven weekdays as round toggles (`Mo` … `Su`), Monday first, filled when on, spread to the edges (a 360 dp phone fits all seven). */
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
                // Two letters, so Tuesday and Thursday (and the weekend) never read the same.
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
        }
    }
}
