package com.askew137.gcwatch.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.wear.ui.theme.toComposeColor

/**
 * Wear OS MaterialTheme wrapper.
 * - Background & surface = pure [Black] (AMOLED zero-pixel cost).
 * - Primary accent = [AppConfig.accentColor].
 */
@Composable
fun GcWatchTheme(
    config: AppConfig,
    content: @Composable () -> Unit,
) {
    val accentColor = remember(config.accentColor) { config.accentColor.toComposeColor() }

    val colors = Colors(
        primary = accentColor,
        primaryVariant = accentColor.copy(alpha = 0.7f),
        secondary = accentColor,
        secondaryVariant = accentColor.copy(alpha = 0.5f),
        background = Black,
        surface = Black,
        error = NightRed,
        onPrimary = Black,
        onSecondary = Black,
        onBackground = PureWhite,
        onSurface = PureWhite,
        onSurfaceVariant = CoolSilver,
        onError = Black,
    )

    MaterialTheme(
        colors = colors,
        content = content,
    )
}
