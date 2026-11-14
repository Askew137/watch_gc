package com.askew137.gcwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccentColor(val hex: Long) {
    SIGNAL_GREEN(0xFF00FF66),
    ELECTRIC_CYAN(0xFF00E5FF),
    SAFETY_ORANGE(0xFFFF6D00),
    SIGNAL_GOLD(0xFFFFD700),
    PURE_WHITE(0xFFFFFFFF),
    NIGHT_RED(0xFFFF2222),
}
