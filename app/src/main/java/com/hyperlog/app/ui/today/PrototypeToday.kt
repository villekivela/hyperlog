package com.hyperlog.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hyperlog.app.coach.Changes
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.session.RirText
import com.hyperlog.app.ui.session.setText
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperlog.app.R
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.planChunks
import com.hyperlog.app.ui.session.RirChip
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.CardShape
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.Inter
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.Tones
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// PROTOTYPE, throwaway (branch prototype/today-settings): Today in looks B, C and D. See
// ui/theme/PrototypeLook.kt for what each tries.

/** Room under the floating look switcher at the top of the screen. */
private val SwitcherClearance = 48.dp

@Composable
internal fun PrototypeToday(
    key: String,
    day: PlannedDay,
    names: Map<String, String>,
    today: LocalDate,
    weekdays: Set<DayOfWeek>,
    lastTrained: LocalDate?,
    banners: @Composable ColumnScope.() -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    onAdHoc: (() -> Unit)?,
    onSettings: () -> Unit,
    program: ActiveProgram?,
    history: List<PastSessionItem>,
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = day.slots.groupBy { it.group }.values.toList()
    val stats = Stats(day)
    Box(modifier.fillMaxSize().background(Tones.screen)) {
        when (key) {
            "B" -> Ledger(day, groups, names, today, stats, banners, startLabel, onStart, onAdHoc, onSettings)
            "C" -> Week(day, groups, names, today, weekdays, stats, banners, startLabel, onStart, onAdHoc, program, history, onOpenSession)
            else -> UpNext(day, groups, names, today, stats, banners, startLabel, onStart, onAdHoc, onSettings)
        }
    }
}

private class Stats(day: PlannedDay) {
    val exercises = day.slots.size
    val sets = day.slots.sumOf { it.sets.size }
    /** Rest plus about 45 s a set, to the nearest 5 minutes. */
    val minutes = ((day.slots.sumOf { s -> s.sets.sumOf { it.rest + 45 } } / 60.0) / 5).let { Math.round(it) * 5 }.toInt()
}

private fun dateLine(today: LocalDate): String =
    "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"

/** `Day A — Heavy bench, squat, pulldown` → `A` and `Heavy bench, squat, pulldown`. */
private fun splitName(name: String): Pair<String?, String> {
    val parts = name.split(" — ", " - ", limit = 2)
    if (parts.size < 2) return null to name
    return parts[0].removePrefix("Day ").trim().take(2) to parts[1]
}

/** A slot's sets in Inter: `1 × 4–6` and its RIR chip, chunks apart, a back-off arrow before its chunk. */
@Composable
private fun Numbers(slot: PlannedSlot, modifier: Modifier = Modifier, big: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val size = if (big) 17.sp else 14.sp
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        planChunks(slot.sets).forEachIndexed { i, c ->
            if (i > 0) Spacer(Modifier.width(14.dp))
            if (c.backoff) {
                SymbolIcon(Symbol.SouthEast, null, Modifier.size(if (big) 18.dp else 16.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
            }
            Text(
                "${c.count} × ${c.reps}",
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = size,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            c.rir?.let {
                Spacer(Modifier.width(6.dp))
                RirChip(it, target = true, fontSize = size)
            }
        }
    }
}

/** A stat in Inter: the number big, what it counts small under it. */
@Composable
private fun Stat(number: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(number, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = color, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.72f))
    }
}

@Composable
private fun StatsRow(stats: Stats, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Stat("${stats.exercises}", "exercises", color)
        Stat("${stats.sets}", "sets", color)
        Stat("~${stats.minutes}", "minutes", color)
    }
}

/** One group's rows, compact: label, name, the numbers under it. */
@Composable
private fun CompactRows(group: List<PlannedSlot>, names: Map<String, String>, vertical: Int = 10) {
    val cs = MaterialTheme.colorScheme
    group.forEach { slot ->
        // The label is centred on the row, beside the name and the numbers.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = CardPadding, vertical = vertical.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slot.label, Modifier.width(36.dp), style = MaterialTheme.typography.titleSmall, color = cs.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    names[slot.exercise] ?: slot.exercise,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Numbers(slot)
            }
        }
    }
}

/** A plain row at the foot of a list: a symbol, a title, a chevron. */
@Composable
private fun FootRow(symbol: Symbol, title: String, index: Int, count: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Segment(index, count, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = CardPadding, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(symbol, null, Modifier.size(24.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            SymbolIcon(Symbol.ChevronRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// B · Ledger: the date leads, the day reads as one tight list, Start is a strip above the tabs,
// and Settings is the last row of Today.

@Composable
private fun Ledger(
    day: PlannedDay,
    groups: List<List<PlannedSlot>>,
    names: Map<String, String>,
    today: LocalDate,
    stats: Stats,
    banners: @Composable ColumnScope.() -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    onAdHoc: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = SwitcherClearance, start = 24.dp, end = 24.dp, bottom = 20.dp)) {
                    Text(today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} · Week ${day.position.week} · ${day.block.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { banners() }
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                    Text(day.day.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    StatsRow(stats, cs.onSurface)
                }
            }
            itemsIndexed(groups, key = { _, g -> g.first().id }) { i, group ->
                Segment(i, groups.size) { CompactRows(group, names) }
            }
            item {
                Spacer(Modifier.height(24.dp))
                val rows = listOfNotNull(
                    onAdHoc?.let { Triple(Symbol.FitnessCenter, stringResource(R.string.today_ad_hoc), it) },
                    Triple(Symbol.Settings, stringResource(R.string.settings), onSettings),
                )
                rows.forEachIndexed { i, (symbol, title, action) -> FootRow(symbol, title, i, rows.size, action) }
            }
        }
        BottomActions {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                SymbolIcon(Symbol.PlayArrow, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(startLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// C · Week: a calendar of weeks on top, swiped back into history and on into the program, each day
// marked with the session done (filled) or planned (outlined). The selected day fills the card and
// the list: today's hero with Start, a past day's session, a future day's plan, or a rest day.
// Settings is a fifth tab (MainActivity), so Today has no gear.

/** A day on the calendar: the sessions finished on it and the program day planned for it. */
private class CalDay(val done: List<PastSessionItem>, val planned: PlannedDay?)

private fun PastSessionItem.date(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * The program's coming days put on dates: the next day today (unless a session was finished today),
 * then on the gyms' usual weekdays, or spread evenly through the week with none set. An estimate:
 * the program runs as a sequence, whenever you train.
 */
private fun project(program: ActiveProgram?, weekdays: Set<DayOfWeek>, today: LocalDate, trainedToday: Boolean): Map<LocalDate, PlannedDay> {
    if (program == null || program.finished) return emptyMap()
    val out = mutableMapOf<LocalDate, PlannedDay>()
    var pos: Position? = program.position
    val perWeek = program.program.blockAt(program.position.week)?.days?.size ?: 3
    val gap = maxOf(1, 7 / maxOf(1, perWeek))
    fun after(d: LocalDate): LocalDate =
        if (weekdays.isEmpty()) d.plusDays(gap.toLong())
        else generateSequence(d.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek in weekdays }
    var date = if (trainedToday) after(today) else today
    val limit = today.plusDays(400)
    while (pos != null && date <= limit) {
        Changes.day(program.program, pos, date, program.changes)?.let { out[date] = it }
        pos = program.program.next(pos)
        date = after(date)
    }
    return out
}

private fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

private fun shortDate(d: LocalDate): String =
    "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Week(
    day: PlannedDay,
    groups: List<List<PlannedSlot>>,
    names: Map<String, String>,
    today: LocalDate,
    weekdays: Set<DayOfWeek>,
    stats: Stats,
    banners: @Composable ColumnScope.() -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    onAdHoc: (() -> Unit)?,
    program: ActiveProgram?,
    history: List<PastSessionItem>,
    onOpenSession: (Long) -> Unit,
) {
    val doneByDate = remember(history) { history.groupBy { it.date() } }
    val trainedToday = doneByDate[today].orEmpty().isNotEmpty()
    val planned = remember(program, weekdays, today, trainedToday) { project(program, weekdays, today, trainedToday) }
    val thisWeek = weekStart(today)
    val firstWeek = minOf(weekStart(history.minOfOrNull { it.date() } ?: today), thisWeek.minusWeeks(4))
    val lastWeek = maxOf(weekStart(planned.keys.maxOrNull() ?: today), thisWeek.plusWeeks(4))
    val weeks = (ChronoUnit.WEEKS.between(firstWeek, lastWeek) + 1).toInt()
    val todayPage = ChronoUnit.WEEKS.between(firstWeek, thisWeek).toInt()
    val pager = rememberPagerState(initialPage = todayPage) { weeks }
    val scope = rememberCoroutineScope()
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val calDay = CalDay(doneByDate[selected].orEmpty(), planned[selected])
    val isToday = selected == today
    val select: (LocalDate) -> Unit = { d ->
        selectedEpoch = d.toEpochDay()
        val page = ChronoUnit.WEEKS.between(firstWeek, weekStart(d)).toInt()
        if (page != pager.currentPage) scope.launch { pager.animateScrollToPage(page) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val shownWeek = firstWeek.plusWeeks(pager.currentPage.toLong())
            CalendarHead(
                shownWeek,
                showToday = pager.currentPage != todayPage || !isToday,
                onToday = { select(today) },
                modifier = Modifier.statusBarsPadding().padding(top = SwitcherClearance),
            )
            HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
                val monday = firstWeek.plusWeeks(page.toLong())
                WeekRow(monday, today, selected, doneByDate, planned, onSelect = select)
            }
            Spacer(Modifier.height(16.dp))
            if (isToday) Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { banners() }
        }
        when {
            // Today with the next day to train: the hero, with Start.
            isToday && !trainedToday -> {
                item { TodayHero(day, stats, startLabel, onStart); Spacer(Modifier.height(16.dp)) }
                itemsIndexed(groups, key = { _, g -> "t" + g.first().id }) { i, group ->
                    Segment(i, groups.size) { CompactRows(group, names, vertical = 8) }
                }
                onAdHoc?.let { adHoc -> item { AdHocButton(adHoc) } }
            }
            calDay.done.isNotEmpty() -> {
                calDay.done.forEach { session ->
                    item(key = "s${session.id}") {
                        DoneCard(session, selected, onOpen = { onOpenSession(session.id) })
                        Spacer(Modifier.height(16.dp))
                    }
                    itemsIndexed(session.exercises, key = { i, _ -> "s${session.id}-$i" }) { i, ex ->
                        Segment(i, session.exercises.size) { DoneRow(i + 1, ex) }
                    }
                    item(key = "gap${session.id}") { Spacer(Modifier.height(16.dp)) }
                }
                planned.keys.filter { it > selected }.minOrNull()?.let { next ->
                    item {
                        TextButton(onClick = { select(next) }, Modifier.padding(start = 12.dp)) {
                            Text("Next: ${splitName(planned.getValue(next).day.name).first?.let { "Day $it" } ?: "session"} on ${shortDate(next)}")
                        }
                    }
                }
            }
            // The next day to train, on whatever date it falls: the hero, with Start.
            calDay.planned != null && selected == planned.keys.minOrNull() -> {
                item { TodayHero(day, stats, startLabel, onStart, head = "Next · ${shortDate(selected)}"); Spacer(Modifier.height(16.dp)) }
                itemsIndexed(groups, key = { _, g -> "n" + g.first().id }) { i, group ->
                    Segment(i, groups.size) { CompactRows(group, names, vertical = 8) }
                }
                onAdHoc?.let { adHoc -> item { AdHocButton(adHoc) } }
            }
            calDay.planned != null -> {
                val p = calDay.planned
                val pGroups = p.slots.groupBy { it.group }.values.toList()
                item { PlannedCard(p, selected, Stats(p)); Spacer(Modifier.height(16.dp)) }
                itemsIndexed(pGroups, key = { _, g -> "p" + g.first().id }) { i, group ->
                    Segment(i, pGroups.size) { CompactRows(group, names, vertical = 8) }
                }
            }
            else -> item {
                val next = planned.keys.filter { it > selected }.minOrNull()
                RestCard(selected, next?.let { it to planned.getValue(it) }, onNext = { next?.let(select) })
            }
        }
    }
}

/** The month of the week shown, and a way back to today when you've moved off it. */
@Composable
private fun CalendarHead(monday: LocalDate, showToday: Boolean, onToday: () -> Unit, modifier: Modifier = Modifier) {
    val sunday = monday.plusDays(6)
    val month = if (monday.month == sunday.month) monday.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    else "${monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} – ${sunday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$month ${sunday.year}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (showToday) TextButton(onClick = onToday) { Text("Today") }
    }
}

/** Mon to Sun: the date in a circle (selected filled, today ringed) over the day's marker. */
@Composable
private fun WeekRow(
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
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(date) }.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) cs.primary else cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .then(
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isSelected -> cs.onPrimaryContainer
                            date < today && sessions.isEmpty() -> cs.onSurfaceVariant
                            else -> cs.onSurface
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Done: filled with the day's letter (a tick for ad hoc or a workout). Planned: outlined.
                val marker = Modifier.height(20.dp).width(28.dp).clip(RoundedCornerShape(6.dp))
                when {
                    sessions.isNotEmpty() -> Box(marker.background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                        val letter = sessions.first().title?.let { splitName(it).first }
                        if (letter != null) {
                            Text(letter, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelSmall)
                        } else {
                            SymbolIcon(Symbol.Check, null, Modifier.size(14.dp), tint = cs.onSecondaryContainer)
                        }
                    }
                    plan != null -> Box(marker.border(1.5.dp, cs.primary.copy(alpha = 0.7f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text(splitName(plan.day.name).first ?: "•", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = cs.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    else -> Spacer(marker)
                }
            }
        }
    }
}

/** The day letter in the scalloped badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LetterBadge(letter: String, container: Color, content: Color) {
    Box(Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.headlineMedium, color = content)
    }
}

/** Today's next day in the accent, with Start. */
@Composable
private fun TodayHero(day: PlannedDay, stats: Stats, startLabel: String, onStart: () -> Unit, head: String? = null) {
    val cs = MaterialTheme.colorScheme
    val (letter, title) = splitName(day.day.name)
    Column(Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(cs.primaryContainer).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (letter != null) {
                LetterBadge(letter, cs.onPrimaryContainer, cs.primaryContainer)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text((head?.let { "$it · " } ?: "") + "Week ${day.position.week} · ${day.block.name}", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                Text(title, style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(20.dp))
        StatsRow(stats, cs.onPrimaryContainer)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            colors = ButtonDefaults.buttonColors(containerColor = cs.onPrimaryContainer, contentColor = cs.primaryContainer),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            SymbolIcon(Symbol.PlayArrow, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(startLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A card on the screen's card tone: what the day is, its letter and name, and its numbers. */
@Composable
private fun DayCard(
    head: String,
    name: String,
    letterContainer: Color,
    letterContent: Color,
    stats: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (letter, title) = splitName(name)
    Column(Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(Tones.card).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (letter != null) {
                LetterBadge(letter, letterContainer, letterContent)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(head, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(20.dp))
        stats()
        action?.let { Spacer(Modifier.height(20.dp)); it() }
    }
}

@Composable
private fun DoneCard(session: PastSessionItem, date: LocalDate, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val minutes = ((session.finishedAt - session.startedAt) / 60_000).toInt()
    DayCard(
        head = "Done · ${shortDate(date)}" + (session.gym?.let { " · $it" } ?: ""),
        name = session.title ?: stringResource(R.string.today_ad_hoc),
        letterContainer = cs.secondaryContainer,
        letterContent = cs.onSecondaryContainer,
        stats = {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Stat("${session.exercises.size}", "exercises", cs.onSurface)
                Stat("${session.exercises.sumOf { it.sets.size }}", "sets", cs.onSurface)
                Stat("$minutes", "minutes", cs.onSurface)
            }
        },
        action = {
            FilledTonalButton(onClick = onOpen, Modifier.fillMaxWidth()) { Text("Open session") }
        },
    )
}

@Composable
private fun PlannedCard(day: PlannedDay, date: LocalDate, stats: Stats) {
    val cs = MaterialTheme.colorScheme
    DayCard(
        head = "Planned · ${shortDate(date)} · Week ${day.position.week}",
        name = day.day.name,
        letterContainer = Color.Transparent,
        letterContent = cs.primary,
        stats = { StatsRow(stats, cs.onSurface) },
    )
}

/** A day with nothing on it, pointing to the next session. */
@Composable
private fun RestCard(date: LocalDate, next: Pair<LocalDate, PlannedDay>?, onNext: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(Tones.card).padding(20.dp)) {
        Text(shortDate(date), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Text("Rest day", style = MaterialTheme.typography.titleLarge)
        next?.let { (d, p) ->
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onNext, contentPadding = PaddingValues(0.dp)) {
                Text("Next: ${splitName(p.day.name).first?.let { "Day $it" } ?: p.day.name} on ${shortDate(d)}")
            }
        }
    }
}

/** An exercise done: its number centred, the name, the sets as logged with their RIR chips. */
@Composable
private fun DoneRow(number: Int, exercise: ExerciseSummary) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = CardPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", Modifier.width(36.dp), style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            RirText(
                exercise.sets.joinToString("   ") { it.setText() },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AdHocButton(onAdHoc: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onAdHoc) {
            SymbolIcon(Symbol.FitnessCenter, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.today_ad_hoc))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// D · Up next: a full-bleed accent header (date, gear, the day, Start) that scrolls away with the
// gear in it; the first exercise up front with its sets large, then the rest.

@Composable
private fun UpNext(
    day: PlannedDay,
    groups: List<List<PlannedSlot>>,
    names: Map<String, String>,
    today: LocalDate,
    stats: Stats,
    banners: @Composable ColumnScope.() -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    onAdHoc: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val on = cs.onPrimaryContainer
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(cs.primaryContainer)
                    .statusBarsPadding()
                    .padding(top = SwitcherClearance - 8.dp, bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dateLine(today), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
                    IconButton(onClick = onSettings) { SymbolIcon(Symbol.Settings, stringResource(R.string.settings), tint = on) }
                }
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("Week ${day.position.week} · ${day.block.name}", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                    Text(day.day.name, style = MaterialTheme.typography.headlineMedium, color = on)
                    Spacer(Modifier.height(20.dp))
                    StatsRow(stats, on)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                        colors = ButtonDefaults.buttonColors(containerColor = on, contentColor = cs.primaryContainer),
                        contentPadding = ButtonDefaults.MediumContentPadding,
                    ) {
                        SymbolIcon(Symbol.PlayArrow, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(startLabel, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { banners() }
        }
        groups.firstOrNull()?.let { first ->
            item {
                GroupHead("Up first")
                Segment(0, 1) {
                    first.forEach { slot ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding + 4.dp, vertical = 16.dp)) {
                            Text(names[slot.exercise] ?: slot.exercise, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(12.dp))
                            BigSets(slot)
                        }
                    }
                }
            }
        }
        if (groups.size > 1) {
            item { GroupHead("Then") }
            val rest = groups.drop(1)
            itemsIndexed(rest, key = { _, g -> g.first().id }) { i, group ->
                Segment(i, rest.size) { CompactRows(group, names) }
            }
        }
        onAdHoc?.let { adHoc ->
            item {
                Spacer(Modifier.height(16.dp))
                FootRow(Symbol.FitnessCenter, stringResource(R.string.today_ad_hoc), 0, 1, adHoc)
            }
        }
    }
}

/** The first exercise's sets, one line a chunk: `2×` small, `6–8` large in Inter, `reps`, the RIR chip. */
@Composable
private fun BigSets(slot: PlannedSlot) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        planChunks(slot.sets).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${c.count}×", Modifier.width(36.dp), fontFamily = Inter, fontSize = 16.sp, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                Text("${c.reps}", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(6.dp))
                Text("reps", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (c.backoff) {
                    Text("back-off", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                }
                c.rir?.let { RirChip(it, target = true, fontSize = 18.sp) }
            }
        }
    }
}
