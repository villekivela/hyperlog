package com.hyperlog.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Every sheet in the app: a Material 3 modal bottom sheet that opens fully expanded. It stops
 * below the status bar and keeps one height wherever it is (#24). Each sheet's content pads for
 * the navigation bar itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val statusBarInsets = WindowInsets.statusBars
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Box(
            Modifier.layout { measurable, constraints ->
                val room = (constraints.maxHeight - statusBarInsets.getTop(this)).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = room))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        ) { content() }
    }
}
