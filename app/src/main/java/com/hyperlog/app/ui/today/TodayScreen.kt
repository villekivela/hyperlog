package com.hyperlog.app.ui.today

import com.hyperlog.app.ui.theme.Group
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import com.hyperlog.app.ui.theme.Banner
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.EmptyState
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.Sheet
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.Advice
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Constraints
import com.hyperlog.app.coach.RuleCoach
import com.hyperlog.app.coach.SessionSwap
import com.hyperlog.app.coach.Situation
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.UsualGym
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.program.StarterOffer
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.update.UpdateBanner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today: the next day of the program, with Start session, or with no program (or a finished one)
 * an ad-hoc session. While a session runs it offers the way back in. Starting asks for the gym,
 * with today's usual gym picked; with no gyms set up it starts straight away
 * (docs/design/program-and-today.md).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    sessionRunning: Boolean,
    onResume: () -> Unit,
    /**
     * Starts a session at the gym (null with no gyms): the program's next day, or ad hoc when
     * [adHoc]. The swaps (slot id -> exercise) are for equipment the gym doesn't have, this session only.
     */
    onStart: (gymId: Long?, adHoc: Boolean, swaps: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    gyms: List<Gym> = emptyList(),
    usualGym: UsualGym? = null,
    program: ActiveProgram? = null,
    exerciseNames: Map<String, String> = emptyMap(),
    onProgram: () -> Unit = {},
    /** The starter program, offered while no program runs; null if unreadable. */
    starter: Program? = null,
    onRunStarter: () -> Unit = {},
    /** The exercise library, for the swaps the coach proposes. */
    library: List<Exercise> = emptyList(),
    /** The day of the last session finished; null with none yet. */
    lastTrained: LocalDate? = null,
    /** Shows a coach's Change for review. */
    onPropose: (Change) -> Unit = {},
    /** The weekday plan, every gym's usual days: time off counts from the first planned day missed. */
    weekdays: Set<DayOfWeek> = emptySet(),
    /** A newer release to offer, above the day; null for none. */
    update: UpdateBanner? = null,
    onUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    /** The program day of the session running, which Resume opens; it is the day Today shows. */
    runningDay: PlannedDay? = null,
) {
    /** The kind of session the gym sheet is open for: null when closed, true for ad hoc. */
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val start: (Boolean) -> Unit = { adHoc ->
        when {
            sessionRunning -> onResume()
            gyms.isEmpty() -> onStart(null, adHoc, emptyMap())
            else -> picking = adHoc
        }
    }
    val kind = picking
    // A running session keeps its day on Today, even resumed after finishing moved the program on.
    val next = runningDay?.takeIf { sessionRunning } ?: program?.next
    if (kind != null && !sessionRunning) {
        Sheet(onDismiss = { picking = null }) {
            StartSheetContent(
                gyms, usualGym,
                onStart = { gym, swaps -> picking = null; onStart(gym, kind, swaps) },
                day = next.takeIf { !kind },
                library = library,
            )
        }
    }
    // Time off: more than a week off (by the weekday plan if there is one), with no Change made for today yet.
    val today = program?.today ?: LocalDate.now()
    val daysOff = RuleCoach.daysSince(lastTrained, today)
    val easeBack = if (program != null && next != null && !sessionRunning && RuleCoach.timeOff(RuleCoach.daysOff(lastTrained, today, weekdays)) &&
        program.changes.none { it.acceptedOn == program.today }
    ) {
        (RuleCoach.advise(Situation.TimeOff, Constraints(), program, library, lastTrained, weekdays) as? Advice.Propose)?.change
    } else null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val startLabel = stringResource(if (sessionRunning) R.string.today_resume_session else R.string.today_start_session)
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Tones.screen,
        topBar = {
            LargeFlexibleTopAppBar(
                // The bar sets titles 16 dp in; the screen's text edge is 24 dp, as on the logging screen.
                title = { Text(stringResource(R.string.today_title), Modifier.padding(start = 8.dp)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        SymbolIcon(Symbol.Settings, stringResource(R.string.settings))
                    }
                },
                colors = hyperlogTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        // The day's one action floats over the list in primary container, clear of the background.
        floatingActionButton = {
            if (next != null) {
                // The expressive medium size: the day's one action, unmistakable.
                MediumExtendedFloatingActionButton(onClick = { start(false) }) {
                    SymbolIcon(Symbol.PlayArrow, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(startLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        if (next != null) {
            NextDay(
                next, exerciseNames, Modifier.weight(1f),
                banners = {
                    update?.let { UpdateBanner(it, onUpdate, onDismissUpdate) }
                    easeBack?.let { change -> TimeOffBanner(daysOff!!, onEaseBack = { onPropose(change) }) }
                },
                adHoc = if (!sessionRunning) { { start(true) } } else null,
            )
        } else {
            update?.let { UpdateBanner(it, onUpdate, onDismissUpdate, Modifier.padding(bottom = 8.dp)) }
            if (program == null && starter != null && !sessionRunning) {
                StarterOffer(starter, onRunStarter, Modifier.weight(1f)) {
                    TextButton(onClick = { start(true) }) { Text(stringResource(R.string.today_start_session)) }
                    TextButton(onClick = onProgram) { Text(stringResource(R.string.program_import_short)) }
                }
            } else {
                val finished = program?.finished == true
                EmptyState(
                    symbol = if (finished) Symbol.Flag else Symbol.FitnessCenter,
                    title = stringResource(if (finished) R.string.program_finished else R.string.today_empty_title),
                    body = stringResource(if (finished) R.string.program_finished_body else R.string.today_no_program_body),
                    modifier = Modifier.weight(1f),
                ) {
                    Button(onClick = { start(true) }, contentPadding = ButtonDefaults.MediumContentPadding, modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                        SymbolIcon(Symbol.PlayArrow, null, Modifier.size(ButtonDefaults.MediumIconSize))
                        Spacer(Modifier.width(8.dp))
                        Text(startLabel, style = MaterialTheme.typography.titleMedium)
                    }
                    if (program == null && !sessionRunning) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onProgram) { Text(stringResource(R.string.program_import_short)) }
                    }
                }
            }
        }
      }
    }
}

/**
 * `Hyperlog 0.2.0 is available` over `Tap to update`, a primary-container banner at the top of
 * Today: tapping anywhere updates, the cross dismisses that version.
 */
@Composable
private fun UpdateBanner(banner: UpdateBanner, onUpdate: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Banner(
        Symbol.SystemUpdate,
        stringResource(R.string.update_available, banner.release.version.name),
        modifier,
        body = stringResource(
            when (banner) {
                is UpdateBanner.Available -> R.string.update_tap
                is UpdateBanner.Downloading -> R.string.update_downloading
                is UpdateBanner.Failed -> R.string.update_failed
            },
        ),
        bodyColor = if (banner is UpdateBanner.Failed) cs.error else null,
        onClick = if (banner is UpdateBanner.Downloading) null else onUpdate,
    ) {
        IconButton(onClick = onDismiss) { SymbolIcon(Symbol.Close, stringResource(R.string.update_dismiss)) }
    }
}

/**
 * The day to train: the update and time-off banners, week and block over the day's name, then its
 * slots as one group of rows (label, exercise, sets), and an ad-hoc session after it.
 */
@Composable
private fun NextDay(
    day: PlannedDay,
    names: Map<String, String>,
    modifier: Modifier = Modifier,
    banners: @Composable ColumnScope.() -> Unit = {},
    adHoc: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    // The bottom padding keeps the last row clear of the floating Start session.
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        item {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { banners() }
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) {
                Text(
                    stringResource(R.string.today_week_block, day.position.week, day.block.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(day.day.name, style = MaterialTheme.typography.headlineSmall)
            }
        }
        // One row per slot, one segment per group: a superset's partners share theirs.
        val groups = day.slots.groupBy { it.group }.values.toList()
        itemsIndexed(groups, key = { _, g -> g.first().id }) { i, group ->
            Segment(i, groups.size) { group.forEach { slot ->
                Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = CardPadding, vertical = 12.dp)) {
                    // A Change's swap reads as the session will: "In place of …" over the name.
                    slot.inPlaceOf?.let { planned ->
                        Text(
                            stringResource(R.string.edit_swapped_from, names[planned] ?: planned),
                            Modifier.padding(start = 40.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // The label sits on the name's baseline.
                    Row {
                        Text(
                            slot.label,
                            Modifier.width(40.dp).alignByBaseline(),
                            style = MaterialTheme.typography.titleSmall.merge(Tnum),
                            color = cs.primary,
                        )
                        Text(
                            names[slot.exercise] ?: slot.exercise,
                            Modifier.weight(1f).alignByBaseline(),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SetSummary(slot.sets, Modifier.padding(start = 40.dp))
                }
            } }
        }
        adHoc?.let { onAdHoc ->
            item {
                Spacer(Modifier.height(16.dp))
                Segment(0, 1, onClick = onAdHoc) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = CardPadding, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SymbolIcon(Symbol.FitnessCenter, null, Modifier.size(24.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.today_ad_hoc), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        SymbolIcon(Symbol.ChevronRight, null, tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * `11 days since your last session` with **Ease back in**, a tertiary-container banner: it opens
 * the coach's re-entry Change (or Repeat week after more than two weeks) for review.
 */
@Composable
private fun TimeOffBanner(days: Int, onEaseBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Banner(
        Symbol.History,
        stringResource(R.string.coach_days_off, days),
        container = cs.tertiaryContainer,
        content = cs.onTertiaryContainer,
    ) {
        Button(
            onClick = onEaseBack,
            colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
        ) { Text(stringResource(R.string.coach_ease_back)) }
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * Where you train today: the gyms as a single choice, today's usual one picked and saying why,
 * and Start. Changing the gym is one tap on another row.
 */
@Composable
internal fun StartSheetContent(
    gyms: List<Gym>,
    usualGym: UsualGym?,
    onStart: (Long, Map<String, String>) -> Unit,
    /** The program day to start, checked against the chosen gym's equipment; null ad hoc. */
    day: PlannedDay? = null,
    library: List<Exercise> = emptyList(),
) {
    var chosen by rememberSaveable { mutableStateOf(usualGym?.id ?: gyms.first().id) }
    val gym = gyms.firstOrNull { it.id == chosen } ?: gyms.first()
    val missing = remember(day, gym.missing, library) { day?.let { RuleCoach.missingEquipment(it, library, gym.missing) }.orEmpty() }
    /** Proposed swaps turned down, by slot id; another gym starts over. */
    var declined by remember(chosen) { mutableStateOf(emptySet<String>()) }
    val names = remember(library) { library.associate { it.id to it.name } }
    val today = remember { LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s" }
    run {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).selectableGroup()) {
            Text(
                stringResource(R.string.start_title),
                Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Group(color = Tones.sheetCard) { gyms.forEach { gym ->
                val selected = gym.id == chosen
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .selectable(selected, role = Role.RadioButton, onClick = { chosen = gym.id })
                        .heightIn(min = 64.dp)
                        .padding(start = 0.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, Modifier.size(48.dp))
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(gym.name, style = MaterialTheme.typography.bodyLarge)
                        // Only a weekday plan or habit makes it usual; the gym trained at last says so.
                        val why = when (usualGym?.takeIf { it.id == gym.id }?.why) {
                            UsualGym.Why.Weekday -> stringResource(R.string.start_usual, today)
                            UsualGym.Why.Last -> stringResource(R.string.start_last)
                            UsualGym.Why.First, null -> null
                        }
                        if (why != null) {
                            Text(
                                why,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } }
            if (missing.isNotEmpty()) {
                MissingSwaps(gym.name, missing, names, declined, onToggle = { slot -> declined = if (slot in declined) declined - slot else declined + slot })
            }
            Spacer(Modifier.height(16.dp))
            val swaps = missing.filter { it.replacement != null && it.slot !in declined }.associate { it.slot to it.replacement!! }
            Button(onClick = { onStart(chosen, swaps) }, Modifier.align(Alignment.End).padding(end = 16.dp).heightIn(min = ButtonDefaults.MediumContainerHeight), contentPadding = ButtonDefaults.MediumContentPadding) {
                Text(stringResource(R.string.start_start))
            }
        }
    }
}

/**
 * What the gym hasn't got, under the gyms: each proposed swap a ticked checkbox row reading as
 * the session will (the exercise, "In place of ..." over it); one with nothing that fits says so.
 */
@Composable
private fun MissingSwaps(gym: String, swaps: List<SessionSwap>, names: Map<String, String>, declined: Set<String>, onToggle: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.start_missing, gym),
        Modifier.padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
    )
    swaps.forEach { swap ->
        val from = names[swap.exercise] ?: swap.exercise
        val to = swap.replacement
        if (to == null) {
            Text(
                stringResource(R.string.start_missing_none, from),
                Modifier.padding(start = 64.dp, end = 24.dp).heightIn(min = 48.dp).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        } else {
            val on = swap.slot !in declined
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(on, role = Role.Checkbox, onValueChange = { onToggle(swap.slot) })
                    .heightIn(min = 56.dp)
                    .padding(start = 12.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = null, Modifier.size(48.dp))
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        stringResource(R.string.edit_swapped_from, from),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Text(names[to] ?: to, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Preview
@Composable
private fun TodayScreenPreview() {
    HyperlogTheme { TodayScreen(onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _, _ -> }) }
}
