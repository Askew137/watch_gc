package com.askew137.gcwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    /** Standard AMOLED black with selected accent. */
    NORMAL,
    /** Pure red accents + Mapsforge night XML theme – preserves dark adaptation. */
    NIGHT_RED,
}
