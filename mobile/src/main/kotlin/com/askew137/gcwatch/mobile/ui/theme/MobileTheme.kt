package com.askew137.gcwatch.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.core.model.ThemeMode
import com.askew137.gcwatch.core.model.toComposeColor

/**
 * Material 3 dark theme for the GC Watch mobile companion.
 *
 * Always uses a pure-black (AMOLED) background. The [primary] color is driven
 * by the user's chosen accent from [AppConfig.accentColor].
 */
@Composable
fun MobileTheme(
    config: AppConfig,
    content: @Composable () -> Unit,
) {
    val accentColor = if (config.themeMode == ThemeMode.NIGHT_RED) {
        Color(0xFFFF2222)
    } else {
        config.accentColor.toComposeColor()
    }

    val colorScheme = darkColorScheme(
        primary            = accentColor,
        onPrimary          = Color.Black,
        primaryContainer   = accentColor.copy(alpha = 0.15f),
        onPrimaryContainer = accentColor,
        secondary          = accentColor.copy(alpha = 0.7f),
        onSecondary        = Color.Black,
        background         = Color.Black,
        onBackground       = Color.White,
        surface            = Color.Black,
        onSurface          = Color.White,
        surfaceVariant     = Color(0xFF1A1A1A),
        onSurfaceVariant   = Color(0xFFCCCCCC),
        outline            = Color(0xFF444444),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
