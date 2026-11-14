package com.askew137.gcwatch.wear.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.askew137.gcwatch.wear.ui.compass.CompassViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.askew137.gcwatch.wear.navigation.TargetLineMode
import com.askew137.gcwatch.wear.ui.components.IconButton
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Route
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.GpsSource
import com.askew137.gcwatch.core.model.Waypoint
import com.askew137.gcwatch.wear.ui.theme.toComposeColor
import com.askew137.gcwatch.wear.gnss.GnssRepository
import com.askew137.gcwatch.wear.gnss.WatchLocation
import com.askew137.gcwatch.wear.map.AutoMapSelector
import com.askew137.gcwatch.wear.map.MapEngineService
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import timber.log.Timber
import java.io.File

/**
 * Full circular map screen.
 * Layout (all inside the circular bounds):
 *   - Center: [MapCanvas]
 *   - Top: zoom [+] / [-] buttons
 *   - Left edge: route plan + compass screen
 *   - Right edge: align map to heading + recenter / north-up
 *   - Bottom: menu icon opening [BottomMenuSheet]
 */
@Composable
fun MapScreen(
    config: AppConfig,
    location: WatchLocation?,
    activeTarget: Waypoint?,
    route: ComputedRoute?,
    lineMode: TargetLineMode,
    mapEngine: MapEngineService,
    autoMapSelector: AutoMapSelector,
    mapViewModel: MapViewModel,
    compassViewModel: CompassViewModel,
    gnssRepository: GnssRepository,
    onNavigateToCompass: () -> Unit,
    onConfigChange: (AppConfig) -> Unit,
    onCycleLineMode: () -> TargetLineMode?,
) {
    val zoom by mapViewModel.zoom.collectAsStateWithLifecycle()
    val rotationDeg by mapViewModel.mapRotationDeg.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ROUTE without a route (e.g. cleared) falls back to the air line.
    val effectiveLineMode =
        if (lineMode == TargetLineMode.ROUTE && route == null) TargetLineMode.AIR_LINE else lineMode

    // Short on-screen label after tapping the line button, so the current state is obvious.
    var lineHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lineHint) {
        if (lineHint != null) {
            delay(1600)
            lineHint = null
        }
    }
    val gnssSource by gnssRepository.source.collectAsStateWithLifecycle()

    val accentColor = remember(config.accentColor) { config.accentColor.toComposeColor() }

    val center = remember(location) {
        location?.let { LatLon(it.lat, it.lon) } ?: LatLon(50.0755, 14.4378) // Prague fallback
    }

    // Map file management
    var showMenu by remember { mutableStateOf(false) }
    val availableMaps = remember(autoMapSelector, showMenu) { autoMapSelector.availableMapFiles() }
    var selectedMaps by remember { mutableStateOf<List<File>>(emptyList()) }
    var automaticMapSelection by remember { mutableStateOf(true) }

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

    // Feed the selected .map files / theme into the render engine.
    var mapsVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(effectiveMaps) {
        mapEngine.setMapFiles(effectiveMaps)
        mapsVersion++
    }
    LaunchedEffect(config.themeMode) {
        mapEngine.applyThemeMode(config.themeMode)
        mapsVersion++
    }

    val rotaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rotaryFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { e ->
                mapViewModel.zoomBy(e.verticalScrollPixels * 0.05f)
                true
            }
            .focusRequester(rotaryFocus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        // ── Map canvas ───────────────────────────────────────────────────────
        MapCanvas(
            engine = mapEngine,
            center = center,
            zoom = zoom,
            mapsVersion = mapsVersion,
            rotationDeg = rotationDeg,
            route = route,
            target = activeTarget,
            lineMode = effectiveLineMode,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Top: zoom buttons ────────────────────────────────────────────────
        EdgeIconButton(
            onClick = { mapViewModel.zoomIn() },
            icon = { Icon(Icons.Rounded.Add, contentDescription = "Přiblížit", modifier = Modifier.size(20.dp), tint = accentColor) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).offset(x = (-26).dp),
        )
        EdgeIconButton(
            onClick = { mapViewModel.zoomOut() },
            icon = { Icon(Icons.Rounded.Remove, contentDescription = "Oddálit", modifier = Modifier.size(20.dp), tint = accentColor) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).offset(x = 26.dp),
        )

        // ── Left edge: target line mode (air line / route / marker only) + compass ──
        EdgeIconButton(
            onClick = {
                val mode = onCycleLineMode()
                lineHint = when {
                    mode == null -> "Žádný cíl"
                    mode == TargetLineMode.AIR_LINE -> "Vzdušná čára"
                    mode == TargetLineMode.ROUTE -> "Trasa"
                    route == null -> "Jen cíl (trasa není k dispozici)"
                    else -> "Jen cíl"
                }
            },
            icon = {
                val (icon, tint) = when (effectiveLineMode) {
                    TargetLineMode.AIR_LINE -> Icons.Rounded.CallMade to accentColor
                    TargetLineMode.ROUTE -> Icons.Rounded.Route to accentColor
                    TargetLineMode.OFF -> Icons.Rounded.LocationOn to Color.Gray
                }
                Icon(
                    icon,
                    contentDescription = "Čára k cíli",
                    modifier = Modifier.size(20.dp),
                    tint = tint,
                )
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).offset(y = (-22).dp),
        )
        EdgeIconButton(
            onClick = onNavigateToCompass,
            icon = { Icon(Icons.Rounded.Explore, contentDescription = "Kompas", modifier = Modifier.size(20.dp), tint = accentColor) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).offset(y = 22.dp),
        )

        // ── Right edge: align to heading + recenter / north-up ───────────────
        EdgeIconButton(
            onClick = {
                // One-shot: map "up" = the direction I'm facing right now (not a toggle).
                scope.launch {
                    compassViewModel.readAzimuthOnce(context)?.let { mapViewModel.alignToHeading(it) }
                        ?: Timber.w("MapScreen: no heading available")
                }
            },
            icon = { Icon(Icons.Rounded.Navigation, contentDescription = "Srovnat podle směru", modifier = Modifier.size(20.dp), tint = accentColor) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).offset(y = (-22).dp),
        )
        EdgeIconButton(
            onClick = { mapViewModel.resetNorthUp() },
            icon = { Icon(Icons.Rounded.MyLocation, contentDescription = "Vycentrovat, sever nahoře", modifier = Modifier.size(20.dp), tint = accentColor) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).offset(y = 22.dp),
        )

        lineHint?.let { hint ->
            Text(
                text = hint,
                fontSize = 11.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 58.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // ── Bottom: menu icon ────────────────────────────────────────────────
        EdgeIconButton(
            onClick = { showMenu = true },
            icon = { Icon(Icons.Rounded.Menu, contentDescription = "Menu", modifier = Modifier.size(20.dp), tint = accentColor) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )

        // ── Bottom sheet overlay ─────────────────────────────────────────────
        if (showMenu) {
            BottomMenuSheet(
                config = config,
                availableMaps = availableMaps,
                selectedMaps = selectedMaps,
                automaticMapSelection = automaticMapSelection,
                onConfigChange = onConfigChange,
                gpsSource = gnssSource,
                onGpsSourceChange = { gnssRepository.switchSource(it) },
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
        modifier = modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
    ) {
        icon()
    }
}
