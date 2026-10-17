package com.hyperlog.app.ui.today

import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier,
) {
    val groups = day.slots.groupBy { it.group }.values.toList()
    val stats = Stats(day)
    Box(modifier.fillMaxSize().background(Tones.screen)) {
        when (key) {
            "B" -> Ledger(day, groups, names, today, stats, banners, startLabel, onStart, onAdHoc, onSettings)
            "C" -> Week(day, groups, names, today, weekdays, lastTrained, stats, banners, startLabel, onStart, onAdHoc)
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
        Column(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = CardPadding, vertical = vertical.dp)) {
            Row {
                Text(slot.label, Modifier.width(36.dp).alignByBaseline(), style = MaterialTheme.typography.titleSmall, color = cs.primary)
                Text(
                    names[slot.exercise] ?: slot.exercise,
                    Modifier.weight(1f).alignByBaseline(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Numbers(slot, Modifier.padding(start = 36.dp))
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
// C · Week: a week strip on top, a hero card in the accent with Start inside it, then the plan.
// Settings is a fifth tab (MainActivity), so Today has no gear.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Week(
    day: PlannedDay,
    groups: List<List<PlannedSlot>>,
    names: Map<String, String>,
    today: LocalDate,
    weekdays: Set<DayOfWeek>,
    lastTrained: LocalDate?,
    stats: Stats,
    banners: @Composable ColumnScope.() -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    onAdHoc: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val (letter, title) = splitName(day.day.name)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            WeekStrip(today, weekdays, lastTrained, Modifier.statusBarsPadding().padding(top = SwitcherClearance + 4.dp, bottom = 16.dp))
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { banners() }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(cs.primaryContainer).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (letter != null) {
                        Box(
                            Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.onPrimaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(letter, style = MaterialTheme.typography.headlineMedium, color = cs.primaryContainer)
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Week ${day.position.week} · ${day.block.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                        )
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
            Spacer(Modifier.height(16.dp))
        }
        itemsIndexed(groups, key = { _, g -> g.first().id }) { i, group ->
            Segment(i, groups.size) { CompactRows(group, names, vertical = 8) }
        }
        onAdHoc?.let { adHoc ->
            item {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = adHoc) {
                        SymbolIcon(Symbol.FitnessCenter, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.today_ad_hoc))
                    }
                }
            }
        }
    }
}

/** Mon to Sun: today filled, planned days ringed, the last session ticked. */
@Composable
private fun WeekStrip(today: LocalDate, weekdays: Set<DayOfWeek>, lastTrained: LocalDate?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).map { monday.plusDays(it.toLong()) }.forEach { date ->
            val isToday = date == today
            val trained = date == lastTrained
            val planned = date.dayOfWeek in weekdays
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) cs.primary else cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val shape = CircleShape
                Box(
                    Modifier.size(40.dp).clip(shape)
                        .then(
                            when {
                                isToday -> Modifier.background(cs.primaryContainer)
                                trained -> Modifier.background(cs.secondaryContainer)
                                planned -> Modifier.border(2.dp, cs.primary.copy(alpha = 0.6f), shape)
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (trained && !isToday) {
                        SymbolIcon(Symbol.Check, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
                    } else {
                        Text(
                            "${date.dayOfMonth}",
                            fontFamily = Inter,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isToday) cs.onPrimaryContainer else cs.onSurface,
                        )
                    }
                }
            }
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
