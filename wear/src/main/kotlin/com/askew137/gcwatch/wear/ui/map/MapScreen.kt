package com.askew137.gcwatch.wear.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.IconButton
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SatelliteAlt
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.GpsSource
import com.askew137.gcwatch.core.model.Waypoint
import com.askew137.gcwatch.core.model.toComposeColor
import com.askew137.gcwatch.wear.gnss.GnssRepository
import com.askew137.gcwatch.wear.gnss.WatchLocation
import com.askew137.gcwatch.wear.map.AutoMapSelector
import com.askew137.gcwatch.wear.map.MapEngineService
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import timber.log.Timber
import java.io.File

/**
 * Full circular map screen.
 * Layout (all inside the circular bounds):
 *   - Center: [MapCanvas]
 *   - Top: zoom [+] / [-] buttons
 *   - Left edge: GPS source + mode toggle
 *   - Right edge: bearing snap + route plan
 *   - Bottom: menu icon opening [BottomMenuSheet]
 */
@Composable
fun MapScreen(
    config: AppConfig,
    location: WatchLocation?,
    activeTarget: Waypoint?,
    route: ComputedRoute?,
    mapEngine: MapEngineService,
    autoMapSelector: AutoMapSelector,
    mapViewModel: MapViewModel,
    gnssRepository: GnssRepository,
    onNavigateToCompass: () -> Unit,
    onRequestRoute: (WatchLocation, Waypoint) -> Unit,
) {
    val zoom by mapViewModel.zoom.collectAsStateWithLifecycle()
    val rotationDeg by mapViewModel.mapRotationDeg.collectAsStateWithLifecycle()
    val isLocked by mapViewModel.isRotationLocked.collectAsStateWithLifecycle()
    val gnssSource by gnssRepository.source.collectAsStateWithLifecycle()

    val accentColor = remember(config.accentColor) { config.accentColor.toComposeColor() }

    val center = remember(location) {
        location?.let { LatLon(it.lat, it.lon) } ?: LatLon(50.0755, 14.4378) // Prague fallback
    }

    // Map file management
    val availableMaps = remember(autoMapSelector) { autoMapSelector.availableMapFiles() }
    var selectedMaps by remember { mutableStateOf<List<File>>(emptyList()) }
    var automaticMapSelection by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    // Update engine when map files change
    val effectiveMaps by remember(automaticMapSelection, selectedMaps, center) {
        derivedStateOf {
            if (automaticMapSelection) {
                autoMapSelector.selectMapsForPosition(center.lat, center.lon, true)
            } else {
                selectedMaps
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .rotaryWithScroll(onScroll = { delta -> mapViewModel.zoomBy(delta * 0.05f) }),
        contentAlignment = Alignment.Center,
    ) {
        // ── Map canvas ───────────────────────────────────────────────────────
        MapCanvas(
            engine = mapEngine,
            center = center,
            zoom = zoom,
            rotationDeg = rotationDeg,
            route = route,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Top: zoom buttons ────────────────────────────────────────────────
        EdgeIconButton(
            onClick = { mapViewModel.zoomIn() },
            icon = { Icon(Icons.Rounded.Add, contentDescription = "Přiblížit", tint = accentColor) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )
        EdgeIconButton(
            onClick = { mapViewModel.zoomOut() },
            icon = { Icon(Icons.Rounded.Remove, contentDescription = "Oddálit", tint = accentColor) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
        )

        // ── Left edge: GNSS source + mode toggle ─────────────────────────────
        EdgeIconButton(
            onClick = {
                val newSrc = if (gnssSource == GpsSource.WATCH_HARDWARE) GpsSource.PHONE_FUSED
                            else GpsSource.WATCH_HARDWARE
                gnssRepository.switchSource(newSrc)
            },
            icon = {
                Icon(
                    Icons.Rounded.SatelliteAlt,
                    contentDescription = "GPS zdroj",
                    tint = if (gnssSource == GpsSource.WATCH_HARDWARE) accentColor else Color.Gray,
                )
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).offset(y = (-22).dp),
        )
        EdgeIconButton(
            onClick = onNavigateToCompass,
            icon = { Icon(Icons.Rounded.Navigation, contentDescription = "Kompas", tint = accentColor) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).offset(y = 22.dp),
        )

        // ── Right edge: bearing snap + route plan ─────────────────────────────
        EdgeIconButton(
            onClick = {
                val bearing = location?.bearingDeg ?: 0f
                if (isLocked) mapViewModel.unlock() else mapViewModel.snapBearing(bearing)
            },
            icon = {
                Icon(
                    if (isLocked) Icons.Rounded.Lock else Icons.Rounded.Explore,
                    contentDescription = "Zamknout azimut",
                    tint = if (isLocked) accentColor else Color.White,
                )
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).offset(y = (-22).dp),
        )
        EdgeIconButton(
            onClick = {
                val loc = location
                val target = activeTarget
                if (loc != null && target != null) {
                    onRequestRoute(loc, target)
                } else {
                    Timber.w("MapScreen: cannot request route — location=%s target=%s", loc, target)
                }
            },
            icon = { Icon(Icons.Rounded.Route, contentDescription = "Naplánovat trasu", tint = accentColor) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).offset(y = 22.dp),
        )

        // ── Bottom: menu icon ────────────────────────────────────────────────
        EdgeIconButton(
            onClick = { showMenu = true },
            icon = { Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = accentColor) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )

        // ── Bottom sheet overlay ─────────────────────────────────────────────
        if (showMenu) {
            BottomMenuSheet(
                config = config,
                availableMaps = availableMaps,
                selectedMaps = selectedMaps,
                automaticMapSelection = automaticMapSelection,
                onConfigChange = { /* caller handles */ },
                onMapSelectionChange = { maps, auto ->
                    selectedMaps = maps
                    automaticMapSelection = auto
                },
                onDismiss = { showMenu = false },
            )
        }
    }
}

@Composable
private fun BoxScope.EdgeIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        icon()
    }
}
