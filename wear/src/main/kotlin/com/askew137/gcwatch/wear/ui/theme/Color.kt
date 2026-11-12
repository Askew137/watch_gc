package com.askew137.gcwatch.wear.ui.theme

import androidx.compose.ui.graphics.Color
import com.askew137.gcwatch.core.model.AccentColor

// ── AMOLED Base ──────────────────────────────────────────────────────────────
/** Pure black background for AMOLED power savings. */
val Black = Color(0xFF000000)
val DarkSurface = Color(0xFF0D0D0D)
val DarkOutline = Color(0xFF1A1A1A)

/** Converts [AccentColor] enum to Compose [Color]. Lives here (wear UI layer) to keep :core:model Compose-free. */
fun AccentColor.toComposeColor() = Color(hex)

// ── Accent Palette (mirrors AccentColor enum hex values) ─────────────────────
val SignalGreen   = Color(0xFF00FF66)
val ElectricCyan  = Color(0xFF00E5FF)
val SafetyOrange  = Color(0xFFFF6D00)
val SignalGold    = Color(0xFFFFD700)
val PureWhite     = Color(0xFFFFFFFF)
val CoolSilver    = Color(0xFFE2E8F0)
val NightRed      = Color(0xFFFF2222)
