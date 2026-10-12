package com.hyperlog.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.program.StarterOffer
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.session.Tnum
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.HyperlogTheme

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
    /** Starts a session at the gym (null with no gyms): the program's next day, or ad hoc when [adHoc]. */
    onStart: (gymId: Long?, adHoc: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    gyms: List<Gym> = emptyList(),
    usualGymId: Long? = null,
    program: ActiveProgram? = null,
    exerciseNames: Map<String, String> = emptyMap(),
    onProgram: () -> Unit = {},
    /** Opens the past sessions, to look at or correct (History hosts them later). */
    onPastSessions: () -> Unit = {},
    /** The starter program, offered while no program runs; null if unreadable. */
    starter: Program? = null,
    onRunStarter: () -> Unit = {},
) {
    /** The kind of session the gym sheet is open for: null when closed, true for ad hoc. */
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val start: (Boolean) -> Unit = { adHoc ->
        when {
            sessionRunning -> onResume()
            gyms.isEmpty() -> onStart(null, adHoc)
            else -> picking = adHoc
        }
    }
    val kind = picking
    if (kind != null && !sessionRunning) {
        StartSheet(gyms, usualGymId, onStart = { picking = null; onStart(it, kind) }, onDismiss = { picking = null })
    }
    val next = program?.next
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                // The bar sets titles 16 dp in; the screen's text edge is 24 dp, as on the logging screen.
                title = { Text(stringResource(R.string.today_title), Modifier.padding(start = 8.dp)) },
                actions = {
                    IconButton(onClick = onPastSessions) {
                        SymbolIcon(Symbol.History, stringResource(R.string.past_title))
                    }
                    IconButton(onClick = onProgram) {
                        SymbolIcon(Symbol.ViewTimeline, stringResource(R.string.program_title))
                    }
                    IconButton(onClick = onSettings) {
                        SymbolIcon(Symbol.Settings, stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (next != null) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!sessionRunning) {
                        TextButton(onClick = { start(true) }) { Text(stringResource(R.string.today_ad_hoc)) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = { start(false) }) {
                        Text(stringResource(if (sessionRunning) R.string.today_resume_session else R.string.today_start_session))
                    }
                }
            }
        },
    ) { padding ->
        if (next != null) {
            NextDay(next, exerciseNames, Modifier.padding(padding))
        } else if (program == null && starter != null && !sessionRunning) {
            StarterOffer(starter, onRunStarter, Modifier.padding(padding)) {
                TextButton(onClick = { start(true) }) { Text(stringResource(R.string.today_start_session)) }
                TextButton(onClick = onProgram) { Text(stringResource(R.string.program_import_short)) }
            }
        } else {
            val finished = program?.finished == true
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SymbolIcon(
                    if (finished) Symbol.Flag else Symbol.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(if (finished) R.string.program_finished else R.string.today_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (finished) R.string.program_finished_body else R.string.today_no_program_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { start(true) }) {
                    Text(stringResource(if (sessionRunning) R.string.today_resume_session else R.string.today_start_session))
                }
                if (program == null && !sessionRunning) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onProgram) { Text(stringResource(R.string.program_import_short)) }
                }
                // Optical centre: sit a little above the middle, clear of the large app bar.
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

/** The day to train: week and block, its name, and a line per slot with its sets. */
@Composable
private fun NextDay(day: PlannedDay, names: Map<String, String>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                Text(
                    stringResource(R.string.today_week_block, day.position.week, day.block.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(day.day.name, style = MaterialTheme.typography.headlineSmall)
            }
        }
        items(day.slots, key = { it.id }) { slot ->
            Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 24.dp, vertical = 8.dp)) {
                // The label sits on the name's baseline.
                Row {
                    Text(
                        slot.label,
                        Modifier.width(40.dp).alignByBaseline(),
                        style = MaterialTheme.typography.labelLarge.merge(Tnum),
                        color = cs.onSurfaceVariant,
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
        }
    }
}


/**
 * Where you train today: the gyms as a single choice, today's usual one picked and saying why,
 * and Start. Changing the gym is one tap on another row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartSheet(gyms: List<Gym>, usualGymId: Long?, onStart: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { StartSheetContent(gyms, usualGymId, onStart) }
}

@Composable
internal fun StartSheetContent(gyms: List<Gym>, usualGymId: Long?, onStart: (Long) -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(usualGymId ?: gyms.first().id) }
    val today = remember { LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s" }
    run {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).selectableGroup()) {
            Text(
                stringResource(R.string.start_title),
                Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            gyms.forEach { gym ->
                val selected = gym.id == chosen
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected, role = Role.RadioButton, onClick = { chosen = gym.id })
                        .heightIn(min = 56.dp)
                        .padding(start = 12.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, Modifier.size(48.dp))
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(gym.name, style = MaterialTheme.typography.bodyLarge)
                        if (gym.id == usualGymId) {
                            Text(
                                stringResource(R.string.start_usual, today),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onStart(chosen) }, Modifier.align(Alignment.End).padding(end = 24.dp)) {
                Text(stringResource(R.string.start_start))
            }
        }
    }
}

@Preview
@Composable
private fun TodayScreenPreview() {
    HyperlogTheme { TodayScreen(onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _ -> }) }
}
