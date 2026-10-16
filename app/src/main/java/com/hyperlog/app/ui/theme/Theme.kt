package com.hyperlog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Hyperlog's own theme: Navy on grey (neutral surfaces, one navy accent) and Space Grotesk on every phone, with no dynamic colour.
 * Light and dark follow the system; there is no in-app theme picker.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperlogTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) NavyDark else NavyLight,
        typography = HyperlogTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
