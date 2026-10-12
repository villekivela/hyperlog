package com.hyperlog.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.search
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

const val LibraryListTag = "library-list"

/**
 * The exercise library (docs/design/exercise-library.md): search on top, then every exercise by
 * name, hidden ones last and muted. Tapping one opens it for editing; the search is kept for
 * coming back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    exercises: List<Exercise>,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val found = exercises.search(query)
    val (hidden, shown) = found.partition { it.hidden }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { SymbolIcon(Symbol.Add, null) },
                text = { Text(stringResource(R.string.library_new)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SearchField(
                query,
                onChange = onQuery,
                placeholder = stringResource(R.string.library_search, exercises.count { !it.hidden }),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize().testTag(LibraryListTag),
                // Room for the button over the last rows.
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (found.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.library_none_found, query.trim()),
                            Modifier.padding(horizontal = Margin, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(shown, key = { it.id }) { LibraryRow(it, onOpen) }
                if (hidden.isNotEmpty()) {
                    item(key = "hidden-label") {
                        Text(
                            stringResource(R.string.library_hidden),
                            Modifier.padding(start = Margin, end = Margin, top = 24.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(hidden, key = { it.id }) { LibraryRow(it, onOpen) }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

/** Name, then equipment and primary muscles; a video symbol when there is a technique video. */
@Composable
private fun LibraryRow(exercise: Exercise, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val muted = exercise.hidden
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(exercise.id) }.heightIn(min = 72.dp)
            .padding(start = Margin, end = Margin, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) cs.outline else cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                exercise.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (muted) cs.outline else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (exercise.video != null) {
            Spacer(Modifier.width(16.dp))
            SymbolIcon(
                Symbol.SmartDisplay,
                stringResource(R.string.library_has_video),
                Modifier.size(20.dp),
                tint = if (muted) cs.outline else cs.onSurfaceVariant,
            )
        }
    }
}

/** A pill-shaped search field on the system keyboard. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().height(56.dp),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { SymbolIcon(Symbol.Search, null, tint = cs.onSurfaceVariant) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onChange("") }) {
                    SymbolIcon(Symbol.Close, stringResource(R.string.library_search_clear), tint = cs.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
