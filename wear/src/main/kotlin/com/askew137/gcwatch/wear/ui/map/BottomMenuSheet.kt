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
import com.askew137.gcwatch.core.model.ThemeMode
import com.askew137.gcwatch.core.model.Units
import com.askew137.gcwatch.core.model.toComposeColor
import com.askew137.gcwatch.wear.ui.theme.Black
import java.io.File

/**
 * Bottom sheet-style overlay with three tabs:
 * - Mapy: map file selection with auto-detect toggle
 * - Vzhled: accent color and theme mode
 * - Jednotky: metric / imperial units
 */
@Composable
fun BottomMenuSheet(
    config: AppConfig,
    availableMaps: List<File>,
    selectedMaps: List<File>,
    automaticMapSelection: Boolean,
    onConfigChange: (AppConfig) -> Unit,
    onMapSelectionChange: (List<File>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mapy", "Vzhled", "Jednotky")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black.copy(alpha = 0.95f))
            .clickable(onClick = {}), // consume clicks
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                        color = if (isSelected) MaterialTheme.colors.primary else Color.Gray,
                        modifier = Modifier
                            .clickable { selectedTab = index }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

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
                2 -> JednotkyTab(
                    config = config,
                    onConfigChange = onConfigChange,
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ToggleChip(
            checked = automatic,
            onCheckedChange = { onSelectionChange(selectedMaps, it) },
            label = { Text("Auto podle polohy", fontSize = 10.sp) },
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
                label = { Text(file.name, fontSize = 9.sp, maxLines = 1) },
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Akcent", fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AccentColor.entries) { color ->
                val isSelected = config.accentColor == color
                Box(
                    modifier = Modifier
                        .size(28.dp)
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
        Spacer(Modifier.height(8.dp))
        Text("Téma", fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                Chip(
                    onClick = { onConfigChange(config.copy(themeMode = mode)) },
                    label = {
                        Text(
                            text = if (mode == ThemeMode.NORMAL) "Normální" else "Noční",
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
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

// ── Tab 3: Units ──────────────────────────────────────────────────────────────

@Composable
private fun JednotkyTab(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
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
}
