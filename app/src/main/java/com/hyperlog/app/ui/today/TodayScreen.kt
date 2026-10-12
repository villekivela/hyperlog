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
 * Today: the next day's prescription. Until there is a program, it says so and offers an ad-hoc
 * session, or the way back into the one running. Starting one asks for the gym, with today's
 * usual gym picked; with no gyms set up it starts straight away.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    sessionRunning: Boolean,
    onResume: () -> Unit,
    onStart: (gymId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    gyms: List<Gym> = emptyList(),
    usualGymId: Long? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val onSession: () -> Unit = {
        when {
            sessionRunning -> onResume()
            gyms.isEmpty() -> onStart(null)
            else -> picking = true
        }
    }
    if (picking && !sessionRunning) {
        StartSheet(gyms, usualGymId, onStart = { picking = false; onStart(it) }, onDismiss = { picking = false })
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        SymbolIcon(Symbol.Settings, stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SymbolIcon(
                Symbol.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.today_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onSession) {
                Text(stringResource(if (sessionRunning) R.string.today_resume_session else R.string.today_start_session))
            }
            // Optical centre: sit a little above the middle, clear of the large app bar.
            Spacer(Modifier.height(96.dp))
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
    HyperlogTheme { TodayScreen(onSettings = {}, sessionRunning = false, onResume = {}, onStart = {}) }
}
