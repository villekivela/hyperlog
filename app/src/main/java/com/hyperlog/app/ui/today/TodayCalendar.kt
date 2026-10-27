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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.layout.SubcomposeLayout
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.library.Exercise
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
import com.hyperlog.app.ui.theme.figureHeadline
import com.hyperlog.app.ui.theme.figureMedium
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
internal fun weekOf(date: LocalDate): LocalDate = com.hyperlog.app.week.Rotations.mondayOf(date)

/** The day a finished session was started on. */
internal fun PastSessionItem.date(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** `Sat 17 Oct`. */
internal fun shortDate(date: LocalDate): String =
    "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"

/** `October 2026`, or `Oct – Nov 2026` for a week across two months (`Dec 2026 – Jan 2027` across years). */
internal fun monthLine(monday: LocalDate, abbreviated: Boolean = false): String {
    val sunday = monday.plusDays(6)
    fun short(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return when {
        monday.month == sunday.month -> "${monday.month.getDisplayName(if (abbreviated) TextStyle.SHORT else TextStyle.FULL, Locale.ENGLISH)} ${monday.year}"
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

/**
 * A done day's fill (the strip's marker, the done card's cookie) and its letter: solid primary,
 * the planned day's outline filled in. secondaryContainer was a lilac on lilac in light (1.1:1).
 */
internal fun doneFill(scheme: ColorScheme): DoneColors = DoneColors(scheme.primary, scheme.onPrimary)

/** A done day's [fill] and the [letter] on it. */
internal data class DoneColors(val fill: Color, val letter: Color)

/** A finished session's letter: its program day's, or null for an ad-hoc session or a workout. */
internal val PastSessionItem.letter: String? get() = position?.let { p -> title?.let { dayLetter(it, p.day) } }

/**
 * A day's marker from its finished sessions, in order: one session's letter, two program days'
 * letters (`CA`), else the first letter and a plus (`A+`); null, drawn as a tick, when none has a
 * letter (ad-hoc sessions and workouts).
 */
internal fun doneMarker(sessions: List<PastSessionItem>): String? {
    val letters = sessions.map { it.letter }
    val first = letters.firstNotNullOfOrNull { it } ?: return null
    return when {
        letters.size == 1 -> first
        letters.size == 2 && letters.all { it != null } -> letters.joinToString("")
        else -> "$first+"
    }
}

/** How TalkBack names one of a day's finished sessions. */
internal sealed interface DoneName {
    data class Day(val letter: String) : DoneName
    data object Workout : DoneName
    data object AdHoc : DoneName
}

/**
 * Every session of a day as TalkBack names it: a program day by its letter, a workout from
 * Claude as a workout (its own name, no program day), else an ad-hoc session (GLOSSARY).
 */
internal fun doneNames(sessions: List<PastSessionItem>): List<DoneName> = sessions.map { s ->
    s.letter?.let { DoneName.Day(it) } ?: if (s.position == null && s.title != null) DoneName.Workout else DoneName.AdHoc
}

/** [name] read out: `Day C`, `workout`, `ad-hoc session`. */
@Composable
private fun spoken(name: DoneName): String = when (name) {
    is DoneName.Day -> stringResource(R.string.today_cell_day, name.letter)
    DoneName.Workout -> stringResource(R.string.today_cell_workout)
    DoneName.AdHoc -> stringResource(R.string.today_cell_ad_hoc)
}

/**
 * The program week a calendar week from [monday] stands for: the week of its first planned day,
 * else of its first program session done; null when it has neither (outside the program, or no
 * program).
 */
internal fun programWeekOf(monday: LocalDate, planned: Map<LocalDate, PlannedDay>, done: Map<LocalDate, List<PastSessionItem>>): Int? {
    val days = (0..6L).map { monday.plusDays(it) }
    return days.firstNotNullOfOrNull { planned[it]?.position?.week }
        ?: days.firstNotNullOfOrNull { d -> done[d]?.firstNotNullOfOrNull { it.position?.week } }
}

/**
 * The calendar's head: the month and year of the week shown, **Today** once you've moved off
 * today, and the pencil to the weekday plan's sheet (`Set your training days` with none set).
 * The month is never cut short: when the words don't fit beside it (larger text, a long month),
 * the prompt is the pencil alone, still read as `Set your training days`.
 */
@Composable
internal fun CalendarHead(
    monday: LocalDate,
    showToday: Boolean,
    onToday: () -> Unit,
    noWeekdayPlan: Boolean,
    onWeekdayPlan: () -> Unit,
    modifier: Modifier = Modifier,
    /** The program week of the week shown (programWeekOf); null outside the program. */
    week: Int? = null,
    /** The week shown is an easy week, named in the week's place. */
    easy: Boolean = false,
) {
    // What the head can say, longest first: the month and the week, the month abbreviated and the
    // week, then the month alone, cut only as a last resort.
    val weekWords = if (easy) stringResource(R.string.today_easy_week) else week?.let { stringResource(R.string.today_head_week, it) }
    val lines = listOfNotNull(
        weekWords?.let { "${monthLine(monday)} · $it" },
        weekWords?.let { "${monthLine(monday, abbreviated = true)} · $it" },
        monthLine(monday),
    ).distinct()
    val month: @Composable (String) -> Unit = { line ->
        Text(interFigures(line), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val actions: @Composable (words: Boolean) -> Unit = { words ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showToday) {
                TextButton(onClick = onToday, Modifier.testTag(TodayButtonTag)) { Text(stringResource(R.string.today_calendar_today)) }
            }
            when {
                noWeekdayPlan && words -> TextButton(onClick = onWeekdayPlan) {
                    SymbolIcon(Symbol.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.weekday_plan_none))
                }
                noWeekdayPlan -> IconButton(onClick = onWeekdayPlan) { SymbolIcon(Symbol.Edit, stringResource(R.string.weekday_plan_none)) }
                else -> IconButton(onClick = onWeekdayPlan) { SymbolIcon(Symbol.Edit, stringResource(R.string.weekday_plan_title)) }
            }
        }
    }
    SubcomposeLayout(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 24.dp, end = 8.dp)) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // Each line's width, measured unseen.
        val widths = lines.mapIndexed { i, line ->
            subcompose("line-$i") { Box(Modifier.clearAndSetSemantics {}) { month(line) } }.single().maxIntrinsicWidth(loose.maxHeight)
        }
        val longest = widths.first()
        // The actions with the prompt's words, measured unseen: the text buttons' own padding and
        // least width around their contents, with no semantics, so nothing to tap or read is
        // composed twice.
        val withWords = noWeekdayPlan &&
            subcompose("probe") {
                Row(Modifier.clearAndSetSemantics {}) {
                    val button = Modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth).padding(ButtonDefaults.TextButtonContentPadding)
                    val label = MaterialTheme.typography.labelLarge
                    if (showToday) Box(button) { Text(stringResource(R.string.today_calendar_today), style = label) }
                    Row(button) {
                        Spacer(Modifier.width(ButtonDefaults.IconSize + ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.weekday_plan_none), style = label)
                    }
                }
            }.single().maxIntrinsicWidth(loose.maxHeight) + longest <= constraints.maxWidth
        val end = subcompose("actions") { actions(withWords) }.single().measure(loose)
        // The pencil's words went first; then the month abbreviates; then the week goes.
        val room = (constraints.maxWidth - end.width).coerceAtLeast(0)
        val line = lines[widths.indexOfFirst { it <= room }.takeIf { it >= 0 } ?: lines.lastIndex]
        val start = subcompose("month") { month(line) }.single().measure(loose.copy(maxWidth = room))
        val height = maxOf(start.height, end.height, constraints.minHeight)
        layout(constraints.maxWidth, height) {
            // Relative, so a right-to-left layout mirrors the head.
            start.placeRelative(0, (height - start.height) / 2)
            end.placeRelative(constraints.maxWidth - end.width, (height - end.height) / 2)
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
    /** The training days missed, by date, each with the day it used up. */
    missed: Map<LocalDate, PlannedDay> = emptyMap(),
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).map { monday.plusDays(it.toLong()) }.forEach { date ->
            val isSelected = date == selected
            val isToday = date == today
            val sessions = done[date].orEmpty()
            val plan = planned[date]
            val miss = missed[date]?.takeIf { sessions.isEmpty() }
            val doneLetter = doneMarker(sessions)
            val names = doneNames(sessions).map { spoken(it) }
            val description = shortDate(date) + when {
                sessions.isNotEmpty() -> ", " + (
                    if (doneLetter == null) stringResource(R.string.today_cell_done)
                    else stringResource(R.string.today_cell_done_list, names.joinToString(", "))
                )
                plan != null -> ", " + stringResource(R.string.today_cell_planned_day, plan.letter)
                miss != null -> ", " + stringResource(R.string.today_cell_missed_day, miss.letter)
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
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) cs.primary else cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
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
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else null,
                        style = MaterialTheme.typography.figureMedium,
                        color = when {
                            isSelected -> cs.onPrimaryContainer
                            // A past day without a session is muted.
                            date < today && sessions.isEmpty() -> cs.onSurfaceVariant.copy(alpha = 0.7f)
                            else -> cs.onSurface
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                val shape = RoundedCornerShape(8.dp)
                // Two letters (`CA`, `A+`) widen it; one keeps 28 dp.
                val marker = Modifier.height(20.dp).widthIn(min = 28.dp).clip(shape)
                when {
                    sessions.isNotEmpty() -> {
                        val (fill, letter) = doneFill(cs)
                        Box(marker.background(fill), contentAlignment = Alignment.Center) {
                            if (doneLetter != null) {
                                Text(doneLetter, Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = letter)
                            } else {
                                SymbolIcon(Symbol.Check, null, Modifier.size(16.dp), tint = letter)
                            }
                        }
                    }
                    plan != null -> Box(marker.border(1.5.dp, cs.primary, shape), contentAlignment = Alignment.Center) {
                        Text(plan.letter, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    }
                    // A missed day keeps its letter in a muted outline, struck through.
                    // Outline, not outlineVariant: the variant all but vanishes on the light screen.
                    miss != null -> Box(marker.border(1.5.dp, cs.outline, shape), contentAlignment = Alignment.Center) {
                        Text(
                            miss.letter, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant, textDecoration = TextDecoration.LineThrough,
                        )
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
        Text(number, style = MaterialTheme.typography.figureHeadline, color = color)
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

/** A planned day's exercises, sets and about how many minutes. */
@Composable
private fun PlannedStats(day: PlannedDay, color: Color, library: List<Exercise>, finished: List<PastSessionItem>) =
    Stats(day.slots.size, day.slots.sumOf { it.sets.size }, aboutMinutes(day, library, finished), estimate = true, color = color)

/** The letter, the head over the day's title, side by side at the top of a day's card. */
@Composable
private fun CardHead(
    letter: String?,
    head: String,
    title: String,
    letterContainer: Color?,
    letterContent: Color,
    headColor: Color,
    titleColor: Color,
    deload: Boolean = false,
    easy: Boolean = false,
    /** A line under the tag saying what an easy week changes; the hero's alone. */
    easyNote: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (letter != null) {
            LetterBadge(letter, letterContainer, letterContent)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(interFigures(head), style = MaterialTheme.typography.labelLarge, color = headColor)
            Text(interFigures(title), style = MaterialTheme.typography.titleLarge, color = titleColor)
            if (deload) {
                Spacer(Modifier.height(8.dp))
                DeloadTag(if (easy) R.string.today_easy_week else R.string.today_deload)
                if (easy && easyNote) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.today_easy_changes), style = MaterialTheme.typography.bodyMedium, color = headColor)
                }
            }
        }
    }
}

/**
 * `Deload` on a day of a deload week: a small tonal tag like the technique pills, the program's
 * spa symbol and the word in onTertiaryContainer, 24 dp high on the 8 dp grid.
 */
@Composable
private fun DeloadTag(words: Int = R.string.today_deload) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).background(cs.tertiaryContainer).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(Symbol.Spa, null, Modifier.size(16.dp), tint = cs.onTertiaryContainer)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(words), Modifier.testTag(DeloadTagTag), style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer)
    }
}

internal const val HeroCardTag = "today-hero"
internal const val DeloadTagTag = "today-deload"

/**
 * The next day to train, in primary container: its letter in the cookie, `Week 7 · Intensify`
 * (`Next · Mon 19 Oct · Week 7` on another date; `Back after 16 days` in an easy week,
 * [easyDays] off) over its title, its numbers, and Start session across the card. With no
 * [onStart] (a later date, with training days) there's no Start.
 */
@Composable
internal fun HeroCard(
    day: PlannedDay,
    date: LocalDate?,
    onStart: (() -> Unit)?,
    library: List<Exercise>,
    finished: List<PastSessionItem>,
    easyDays: Int? = null,
) {
    val cs = MaterialTheme.colorScheme
    val week = easyDays?.let { stringResource(R.string.today_easy_head, it) } ?: stringResource(R.string.today_week_block, day.position.week, day.block.name)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(cs.primaryContainer)
            .padding(horizontal = CardPadding, vertical = 20.dp).testTag(HeroCardTag),
    ) {
        CardHead(
            day.letter,
            // On another date the block's name goes, as on a planned day, so the head stays one line.
            head = date?.let { stringResource(R.string.today_next_head, shortDate(it), day.position.week) } ?: week,
            title = dayTitle(day.day.name),
            letterContainer = cs.onPrimaryContainer, letterContent = cs.primaryContainer,
            headColor = cs.onPrimaryContainer.copy(alpha = 0.8f), titleColor = cs.onPrimaryContainer,
            deload = day.deload, easy = day.easy, easyNote = true,
        )
        Spacer(Modifier.height(20.dp))
        PlannedStats(day, cs.onPrimaryContainer, library, finished)
        if (onStart == null) return@Column
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

/** A card on the card tone, its text on the 24 dp edge. */
@Composable
private fun DayCard(bottom: Dp = 20.dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(Tones.card).padding(start = CardPadding, end = CardPadding, top = 20.dp, bottom = bottom)) { content() }
}

/**
 * A finished session: `Done · Sat 17 Oct · Kalliokoski`, its letter filled, its name, its
 * numbers (minutes taken) and Open session.
 */
@Composable
internal fun DoneCard(session: PastSessionItem, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val doneColors = doneFill(cs)
    val date = shortDate(session.date())
    DayCard {
        CardHead(
            session.letter,
            head = session.gym?.let { stringResource(R.string.today_done_head_gym, date, it) } ?: stringResource(R.string.today_done_head, date),
            title = session.title?.let(::dayTitle) ?: stringResource(R.string.today_ad_hoc),
            letterContainer = doneColors.fill, letterContent = doneColors.letter,
            headColor = cs.primary, titleColor = cs.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        val minutes = ((session.finishedAt - session.startedAt) / 60_000).toInt()
        Stats(session.exercises.size, session.exercises.sumOf { it.sets.size }, minutes, estimate = false, color = cs.onSurface)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onOpen, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.today_open_session)) }
    }
}

/**
 * A later planned day: `Planned · Wed 21 Oct · Week 7` (`Last day · …` on the program's [last]),
 * its letter outlined, its name and numbers. No Start. A [missed] day reads `Missed · Mon 26 Oct ·
 * Week 7`, dimmed.
 */
@Composable
internal fun PlannedCard(
    day: PlannedDay,
    date: LocalDate,
    last: Boolean = false,
    library: List<Exercise>,
    finished: List<PastSessionItem>,
    missed: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val head = when {
        missed -> R.string.today_missed_head
        last -> R.string.today_last_day
        else -> R.string.today_planned_head
    }
    DayCard {
        Column(Modifier.alpha(if (missed) MissedAlpha else 1f)) {
            // The program's last day says so in Planned's place, so the head stays one line.
            CardHead(
                day.letter,
                head = stringResource(head, shortDate(date), day.position.week),
                title = dayTitle(day.day.name),
                // A missed day is nothing to act on, so it gives up the accent for the muted tone.
                letterContainer = null, letterContent = if (missed) cs.onSurfaceVariant else cs.primary,
                headColor = if (missed) cs.onSurfaceVariant else cs.primary, titleColor = cs.onSurface,
                deload = day.deload, easy = day.easy,
            )
            Spacer(Modifier.height(20.dp))
            PlannedStats(day, cs.onSurface, library, finished)
        }
    }
}

/** What a day with nothing on it says beyond its date. */
internal sealed interface RestMode {
    /** `Rest day`, and the next session to select ([next] null past it). */
    data class Rest(val next: Pair<LocalDate, PlannedDay>?) : RestMode

    /** Past the program's last planned day: `Nothing planned`, pointing back to [last]. */
    data class AfterProgram(val last: Pair<LocalDate, PlannedDay>) : RestMode

    /** The program goes on but only its [next] day has a date (no training days): `Not planned yet`. */
    data class Undated(val next: Pair<LocalDate, PlannedDay>, val onWeekdayPlan: () -> Unit) : RestMode
}

/** A day with nothing on it: its date, then what [mode] says, with a link to the day it names. */
@Composable
internal fun RestCard(date: LocalDate, mode: RestMode, onNext: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (title, link) = when (mode) {
        is RestMode.Rest -> R.string.today_rest_day to mode.next
        is RestMode.AfterProgram -> R.string.today_nothing_planned to mode.last
        is RestMode.Undated -> R.string.today_not_planned_yet to mode.next
    }
    // The link's 48 dp row already holds 14 dp under its words: 8 dp more matches the 20 dp on top.
    DayCard(bottom = if (link != null) 8.dp else 20.dp) {
        Text(interFigures(shortDate(date)), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        link?.let { (d, day) ->
            Spacer(Modifier.height(8.dp))
            NextLink(d, day, onNext, padding = PaddingValues(0.dp), last = mode is RestMode.AfterProgram)
        }
        if (mode is RestMode.Undated) {
            TextButton(onClick = mode.onWeekdayPlan, contentPadding = PaddingValues(0.dp)) {
                SymbolIcon(Symbol.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.weekday_plan_none))
            }
        }
    }
}

/** `Next: Day B on Wed 21 Oct` (`Last planned: …` when [last]), selecting that day. */
@Composable
internal fun NextLink(date: LocalDate, day: PlannedDay, onNext: (LocalDate) -> Unit, modifier: Modifier = Modifier, padding: PaddingValues = ButtonDefaults.TextButtonContentPadding, last: Boolean = false) {
    TextButton(onClick = { onNext(date) }, modifier, contentPadding = padding) {
        Text(stringResource(if (last) R.string.today_last_link else R.string.today_next_link, day.letter, shortDate(date)))
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
        Text("$number", Modifier.width(40.dp), style = MaterialTheme.typography.titleSmall, color = cs.primary)
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
