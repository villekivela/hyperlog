package com.hyperlog.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Follows the Android theme: dynamic colour from the wallpaper and light/dark from the system.
 * There is no in-app theme picker. Below Android 12 it falls back to the logo palette.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperlogTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = hyperlogColorScheme(isSystemInDarkTheme()),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
private fun hyperlogColorScheme(dark: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> LogoDarkColorScheme
        else -> LogoLightColorScheme
    }
}
