package com.hyperlog.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.session.RirText
import com.hyperlog.app.ui.session.setText
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.CardShape
import com.hyperlog.app.ui.theme.Inter
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.interFigures
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// Today's calendar of weeks and the cards for the day picked on it (SPEC, Today; design note,
// Today's calendar).

/** The Monday of [date]'s week: the calendar's weeks run Monday to Sunday. */
internal fun weekOf(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value - 1L)

/** The day a finished session was started on. */
internal fun PastSessionItem.date(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** `Sat 17 Oct`. */
internal fun shortDate(date: LocalDate): String =
    "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"

/** `October 2026`, or `Oct – Nov 2026` for a week across two months (`Dec 2026 – Jan 2027` across years). */
internal fun monthLine(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    fun short(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return when {
        monday.month == sunday.month -> "${monday.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${monday.year}"
        monday.year == sunday.year -> "${short(monday)} – ${short(sunday)} ${sunday.year}"
        else -> "${short(monday)} ${monday.year} – ${short(sunday)} ${sunday.year}"
    }
}

private val DayPrefix = Regex("""^Day\s+(\w{1,2})\b\s*(?:[—–-]\s*)?""")

/**
 * A program day's letter: `A` from `Day A — Heavy bench, squat, pulldown`, else the day's place in
 * its week (`A` for the first).
 */
internal fun dayLetter(name: String, index: Int): String = DayPrefix.find(name)?.groupValues?.get(1) ?: ('A' + index).toString()

/** A program day's name after its letter: `Heavy bench, squat, pulldown`, or the whole name (`Day A`) when nothing follows. */
internal fun dayTitle(name: String): String = name.replace(DayPrefix, "").ifBlank { name }

internal val PlannedDay.letter: String get() = dayLetter(day.name, position.day)

/** A finished session's letter: its program day's, or null for an ad-hoc session or a workout. */
internal val PastSessionItem.letter: String? get() = position?.let { p -> title?.let { dayLetter(it, p.day) } }

/** About how long a day takes: each set's rest and about 45 s of work, to the nearest 5 minutes. */
internal fun aboutMinutes(day: PlannedDay): Int =
    Math.round(day.slots.sumOf { s -> s.sets.sumOf { it.rest + 45 } } / 60.0 / 5).toInt() * 5

/**
 * The calendar's head: the month and year of the week shown, **Today** once you've moved off
 * today, and the pencil to the training days (`Set your training days` with none set).
 */
@Composable
internal fun CalendarHead(
    monday: LocalDate,
    showToday: Boolean,
    onToday: () -> Unit,
    noTrainingDays: Boolean,
    onTrainingDays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(interFigures(monthLine(monday)), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showToday) {
            TextButton(onClick = onToday, Modifier.testTag(TodayButtonTag)) { Text(stringResource(R.string.today_calendar_today)) }
        }
        if (noTrainingDays) {
            TextButton(onClick = onTrainingDays) {
                SymbolIcon(Symbol.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.weekday_plan_none))
            }
        } else {
            IconButton(onClick = onTrainingDays) { SymbolIcon(Symbol.Edit, stringResource(R.string.weekday_plan_title)) }
        }
    }
}

/**
 * Mon to Sun from [monday]: each day's weekday letter, its date in a 40 dp circle (filled when
 * selected, ringed when it's today), and its marker: the done session's letter filled (a tick
 * for an ad-hoc session or a workout), the planned day's letter outlined, or nothing.
 */
@Composable
internal fun WeekRow(
    monday: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    done: Map<LocalDate, List<PastSessionItem>>,
    planned: Map<LocalDate, PlannedDay>,
    onSelect: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).map { monday.plusDays(it.toLong()) }.forEach { date ->
            val isSelected = date == selected
            val isToday = date == today
            val sessions = done[date].orEmpty()
            val plan = planned[date]
            val doneLetter = sessions.firstNotNullOfOrNull { it.letter }
            val description = shortDate(date) + when {
                sessions.isNotEmpty() -> ", " + (doneLetter?.let { stringResource(R.string.today_cell_done_day, it) } ?: stringResource(R.string.today_cell_done))
                plan != null -> ", " + stringResource(R.string.today_cell_planned_day, plan.letter)
                else -> ""
            }
            Column(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .selectable(isSelected, role = Role.Tab, onClick = { onSelect(date) })
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Tab
                        this.selected = isSelected
                    }
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) cs.primary else cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).then(
                        when {
                            isSelected -> Modifier.background(cs.primaryContainer)
                            isToday -> Modifier.border(2.dp, cs.primary, CircleShape)
                            else -> Modifier
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${date.dayOfMonth}",
                        fontFamily = Inter,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            isSelected -> cs.onPrimaryContainer
                            // A past day without a session is muted.
                            date < today && sessions.isEmpty() -> cs.onSurfaceVariant.copy(alpha = 0.7f)
                            else -> cs.onSurface
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                val shape = RoundedCornerShape(6.dp)
                val marker = Modifier.height(20.dp).width(28.dp).clip(shape)
                when {
                    sessions.isNotEmpty() -> Box(marker.background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                        if (doneLetter != null) {
                            Text(doneLetter, fontFamily = Inter, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer)
                        } else {
                            SymbolIcon(Symbol.Check, null, Modifier.size(16.dp), tint = cs.onSecondaryContainer)
                        }
                    }
                    plan != null -> Box(marker.border(1.5.dp, cs.primary, shape), contentAlignment = Alignment.Center) {
                        Text(plan.letter, fontFamily = Inter, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    }
                    else -> Spacer(marker)
                }
            }
        }
    }
}

/** A day's letter in the 9-sided cookie: filled in [container], or outlined in [content] when [container] is null. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LetterBadge(letter: String, container: Color?, content: Color) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(
        Modifier.size(64.dp).clip(shape).then(if (container != null) Modifier.background(container) else Modifier.border(2.dp, content, shape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = MaterialTheme.typography.headlineMedium, color = content)
    }
}

/** A number in Inter, what it counts small under it. */
@Composable
private fun Stat(number: String, label: String, color: Color) {
    Column {
        Text(number, fontFamily = Inter, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.78f))
    }
}

/** Exercises, sets and minutes (`~45` for an estimate). */
@Composable
private fun Stats(exercises: Int, sets: Int, minutes: Int, estimate: Boolean, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Stat("$exercises", pluralStringResource(R.plurals.today_stat_exercises, exercises), color)
        Stat("$sets", pluralStringResource(R.plurals.today_stat_sets, sets), color)
        Stat(if (estimate) "~$minutes" else "$minutes", pluralStringResource(R.plurals.today_stat_minutes, minutes), color)
    }
}

/** The letter, the head over the day's title, side by side at the top of a day's card. */
@Composable
private fun CardHead(letter: String?, head: String, title: String, letterContainer: Color?, letterContent: Color, headColor: Color, titleColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (letter != null) {
            LetterBadge(letter, letterContainer, letterContent)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(interFigures(head), style = MaterialTheme.typography.labelLarge, color = headColor)
            Spacer(Modifier.height(2.dp))
            Text(interFigures(title), style = MaterialTheme.typography.titleLarge, color = titleColor)
        }
    }
}

internal const val HeroCardTag = "today-hero"

/**
 * The next day to train, in primary container: its letter in the cookie, `Week 7 · Intensify`
 * (with `Next · Mon 19 Oct ·` in front on another date) over its title, its numbers, and Start
 * session across the card.
 */
@Composable
internal fun HeroCard(day: PlannedDay, date: LocalDate?, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val week = stringResource(R.string.today_week_block, day.position.week, day.block.name)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(cs.primaryContainer).padding(20.dp).testTag(HeroCardTag),
    ) {
        CardHead(
            day.letter,
            head = date?.let { stringResource(R.string.today_next_head, shortDate(it), week) } ?: week,
            title = dayTitle(day.day.name),
            letterContainer = cs.onPrimaryContainer, letterContent = cs.primaryContainer,
            headColor = cs.onPrimaryContainer.copy(alpha = 0.8f), titleColor = cs.onPrimaryContainer,
        )
        Spacer(Modifier.height(20.dp))
        Stats(day.slots.size, day.slots.sumOf { it.sets.size }, aboutMinutes(day), estimate = true, color = cs.onPrimaryContainer)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            colors = ButtonDefaults.buttonColors(containerColor = cs.onPrimaryContainer, contentColor = cs.primaryContainer),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            SymbolIcon(Symbol.PlayArrow, null, Modifier.size(ButtonDefaults.MediumIconSize))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.today_start_session), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A card on the card tone. */
@Composable
private fun DayCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(Tones.card).padding(20.dp)) { content() }
}

/**
 * A finished session: `Done · Sat 17 Oct · Kalliokoski`, its letter filled, its name, its
 * numbers (minutes taken) and Open session.
 */
@Composable
internal fun DoneCard(session: PastSessionItem, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val date = shortDate(session.date())
    DayCard {
        CardHead(
            session.letter,
            head = session.gym?.let { stringResource(R.string.today_done_head_gym, date, it) } ?: stringResource(R.string.today_done_head, date),
            title = session.title?.let(::dayTitle) ?: stringResource(R.string.today_ad_hoc),
            letterContainer = cs.secondaryContainer, letterContent = cs.onSecondaryContainer,
            headColor = cs.primary, titleColor = cs.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        val minutes = ((session.finishedAt - session.startedAt) / 60_000).toInt()
        Stats(session.exercises.size, session.exercises.sumOf { it.sets.size }, minutes, estimate = false, color = cs.onSurface)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onOpen, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.today_open_session)) }
    }
}

/** A later planned day: `Planned · Wed 21 Oct · Week 7`, its letter outlined, its name and numbers. No Start. */
@Composable
internal fun PlannedCard(day: PlannedDay, date: LocalDate) {
    val cs = MaterialTheme.colorScheme
    DayCard {
        CardHead(
            day.letter,
            head = stringResource(R.string.today_planned_head, shortDate(date), day.position.week),
            title = dayTitle(day.day.name),
            letterContainer = null, letterContent = cs.primary,
            headColor = cs.primary, titleColor = cs.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        Stats(day.slots.size, day.slots.sumOf { it.sets.size }, aboutMinutes(day), estimate = true, color = cs.onSurface)
    }
}

/** A day with nothing on it: `Rest day`, and the next session to select. */
@Composable
internal fun RestCard(date: LocalDate, next: Pair<LocalDate, PlannedDay>?, onNext: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    DayCard {
        Text(interFigures(shortDate(date)), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.today_rest_day), style = MaterialTheme.typography.titleLarge)
        next?.let { (d, day) ->
            Spacer(Modifier.height(8.dp))
            NextLink(d, day, onNext, Modifier.padding(start = 0.dp), PaddingValues(0.dp))
        }
    }
}

/** `Next: Day B on Wed 21 Oct`, selecting that day. */
@Composable
internal fun NextLink(date: LocalDate, day: PlannedDay, onNext: (LocalDate) -> Unit, modifier: Modifier = Modifier, padding: PaddingValues = ButtonDefaults.TextButtonContentPadding) {
    TextButton(onClick = { onNext(date) }, modifier, contentPadding = padding) {
        Text(stringResource(R.string.today_next_link, day.letter, shortDate(date)))
        Spacer(Modifier.width(4.dp))
        SymbolIcon(Symbol.ChevronRight, null, Modifier.size(ButtonDefaults.IconSize))
    }
}

/** A finished session's exercise: its number centred on the row, the name, the sets as logged with their RIR chips filled. */
@Composable
internal fun DoneRow(number: Int, exercise: ExerciseSummary) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", Modifier.width(40.dp), fontFamily = Inter, style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RirText(exercise.sets.joinToString("   ") { it.setText() }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

/** Ad-hoc session as a text button after the day. */
@Composable
internal fun AdHocButton(onAdHoc: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onAdHoc, Modifier.heightIn(min = 48.dp)) {
            SymbolIcon(Symbol.FitnessCenter, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.today_ad_hoc))
        }
    }
}

/** A planned day's slots, one group per segment. */
internal fun groupsOf(day: PlannedDay): List<List<PlannedSlot>> = day.slots.groupBy { it.group }.values.toList()
