package com.askew137.gcwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val accentColor: AccentColor = AccentColor.ELECTRIC_CYAN,
    val themeMode: ThemeMode = ThemeMode.NORMAL,
    val units: Units = Units.METRIC,
    val gpsSource: GpsSource = GpsSource.WATCH_HARDWARE,
)

@Serializable
enum class Units { METRIC, IMPERIAL }
