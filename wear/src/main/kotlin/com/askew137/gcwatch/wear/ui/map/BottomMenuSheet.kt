package com.askew137.gcwatch.wear.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.askew137.gcwatch.core.model.AccentColor
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.core.model.GpsSource
import com.askew137.gcwatch.core.model.ThemeMode
import com.askew137.gcwatch.core.model.Units
import com.askew137.gcwatch.wear.ui.theme.toComposeColor
import com.askew137.gcwatch.wear.ui.theme.Black
import java.io.File

/**
 * Bottom sheet-style overlay with three tabs:
 * - Mapy: map file selection with auto-detect toggle
 * - Vzhled: accent color and theme mode
 * - Ostatní: units (metric / imperial) and GPS source
 */
@Composable
fun BottomMenuSheet(
    config: AppConfig,
    availableMaps: List<File>,
    selectedMaps: List<File>,
    automaticMapSelection: Boolean,
    onConfigChange: (AppConfig) -> Unit,
    gpsSource: GpsSource,
    onGpsSourceChange: (GpsSource) -> Unit,
    onMapSelectionChange: (List<File>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mapy", "Vzhled", "Ostatní")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .clickable(onClick = {}), // consume clicks
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 26.dp, end = 26.dp, top = 30.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Tab row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        maxLines = 1,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        color = if (isSelected) MaterialTheme.colors.primary else Color.Gray,
                        modifier = Modifier
                            .clickable { selectedTab = index }
                            .padding(horizontal = 3.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                0 -> MapyTab(
                    availableMaps = availableMaps,
                    selectedMaps = selectedMaps,
                    automatic = automaticMapSelection,
                    onSelectionChange = onMapSelectionChange,
                )
                1 -> GivenThemeTab(
                    config = config,
                    onConfigChange = onConfigChange,
                )
                2 -> OstatniTab(
                    config = config,
                    onConfigChange = onConfigChange,
                    gpsSource = gpsSource,
                    onGpsSourceChange = onGpsSourceChange,
                )
            }
        }
    }
}

// ── Tab 1: Map files ──────────────────────────────────────────────────────────

@Composable
private fun MapyTab(
    availableMaps: List<File>,
    selectedMaps: List<File>,
    automatic: Boolean,
    onSelectionChange: (List<File>, Boolean) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        ToggleChip(
            checked = automatic,
            onCheckedChange = { onSelectionChange(selectedMaps, it) },
            label = { Text("Auto podle polohy", fontSize = 11.sp, maxLines = 1) },
            toggleControl = { Switch(checked = automatic) },
            modifier = Modifier.fillMaxWidth(),
            colors = ToggleChipDefaults.toggleChipColors(
                checkedStartBackgroundColor = Color.Transparent,
                checkedEndBackgroundColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(4.dp))
        availableMaps.forEach { file ->
            val isChecked = file in selectedMaps
            ToggleChip(
                checked = isChecked,
                onCheckedChange = { checked ->
                    val newSelection = if (checked) selectedMaps + file else selectedMaps - file
                    onSelectionChange(newSelection, automatic)
                },
                label = { Text(file.name, fontSize = 11.sp, maxLines = 1) },
                toggleControl = { Switch(checked = isChecked) },
                modifier = Modifier.fillMaxWidth(),
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedStartBackgroundColor = Color.Transparent,
                    checkedEndBackgroundColor = Color.Transparent,
                ),
            )
        }
    }
}

// ── Tab 2: Theme ──────────────────────────────────────────────────────────────

@Composable
private fun GivenThemeTab(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text("Akcent", fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        AccentColor.entries.chunked(3).forEach { rowColors ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 3.dp),
            ) {
                rowColors.forEach { color ->
                    val isSelected = config.accentColor == color
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(color.toComposeColor())
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { onConfigChange(config.copy(accentColor = color)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Téma", fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                Chip(
                    onClick = { onConfigChange(config.copy(themeMode = mode)) },
                    label = {
                        Text(
                            text = if (mode == ThemeMode.NORMAL) "Normální" else "Noční",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (config.themeMode == mode)
                            MaterialTheme.colors.primary.copy(alpha = 0.3f)
                        else Color.DarkGray,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── Tab 3: Units + GPS source ─────────────────────────────────────────────────

@Composable
private fun OstatniTab(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
    gpsSource: GpsSource,
    onGpsSourceChange: (GpsSource) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Units.entries.forEach { units ->
                Chip(
                    onClick = { onConfigChange(config.copy(units = units)) },
                    label = {
                        Text(
                            text = if (units == Units.METRIC) "Metry" else "Míle",
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (config.units == units)
                            MaterialTheme.colors.primary.copy(alpha = 0.3f)
                        else Color.DarkGray,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val phone = gpsSource == GpsSource.PHONE_FUSED
        ToggleChip(
            checked = phone,
            onCheckedChange = {
                onGpsSourceChange(if (it) GpsSource.PHONE_FUSED else GpsSource.WATCH_HARDWARE)
            },
            label = { Text("GPS z telefonu", fontSize = 11.sp, maxLines = 1) },
            secondaryLabel = {
                Text(if (phone) "telefon" else "hodinky", fontSize = 9.sp, color = Color.Gray)
            },
            toggleControl = { Switch(checked = phone) },
            modifier = Modifier.fillMaxWidth(),
            colors = ToggleChipDefaults.toggleChipColors(
                checkedStartBackgroundColor = Color.Transparent,
                checkedEndBackgroundColor = Color.Transparent,
            ),
        )
    }
}
