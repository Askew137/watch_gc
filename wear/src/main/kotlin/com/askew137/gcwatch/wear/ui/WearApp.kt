package com.askew137.gcwatch.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.core.model.NavigationTarget
import com.askew137.gcwatch.wear.ui.theme.toComposeColor
import com.askew137.gcwatch.wear.gnss.GnssRepository
import com.askew137.gcwatch.wear.map.AutoMapSelector
import com.askew137.gcwatch.wear.map.MapEngineService
import com.askew137.gcwatch.wear.ui.compass.CompassScreen
import com.askew137.gcwatch.wear.ui.compass.CompassViewModel
import com.askew137.gcwatch.wear.ui.map.MapScreen
import com.askew137.gcwatch.wear.ui.map.MapViewModel
import com.askew137.gcwatch.wear.ui.theme.GcWatchTheme

private const val DEST_MAP = "map"
private const val DEST_COMPASS = "compass"

/**
 * Root composable. Wraps everything in [GcWatchTheme] and sets up [SwipeDismissableNavHost].
 */
@Composable
fun WearApp(
    configViewModel: ConfigViewModel,
    mapViewModel: MapViewModel,
    navigationViewModel: NavigationViewModel,
    compassViewModel: CompassViewModel,
    gnssRepository: GnssRepository,
    mapEngine: MapEngineService,
    autoMapSelector: AutoMapSelector,
) {
    val config by configViewModel.config.collectAsStateWithLifecycle()
    val location by gnssRepository.location.collectAsStateWithLifecycle()
    val activeTarget by navigationViewModel.activeTarget.collectAsStateWithLifecycle()
    val currentRoute by navigationViewModel.currentRoute.collectAsStateWithLifecycle()

    // Build NavigationTarget from location + active target
    val navigationTarget = remember(location, activeTarget) {
        val loc = location ?: return@remember null
        val target = activeTarget ?: return@remember null
        val bearing = bearingTo(loc.lat, loc.lon, target.lat, target.lon)
        val distance = distanceTo(loc.lat, loc.lon, target.lat, target.lon)
        NavigationTarget(waypoint = target, bearingDeg = bearing, distanceM = distance)
    }

    GcWatchTheme(config = config) {
        val navController = rememberSwipeDismissableNavController()

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = DEST_MAP,
        ) {
            composable(DEST_MAP) {
                MapScreen(
                    config = config,
                    location = location,
                    activeTarget = activeTarget,
                    route = currentRoute,
                    mapEngine = mapEngine,
                    autoMapSelector = autoMapSelector,
                    mapViewModel = mapViewModel,
                    gnssRepository = gnssRepository,
                    onNavigateToCompass = { navController.navigate(DEST_COMPASS) },
                    onRequestRoute = { from, to -> navigationViewModel.requestRoute(from, to) },
                )
            }
            composable(DEST_COMPASS) {
                CompassScreen(
                    config = config,
                    location = location,
                    navigationTarget = navigationTarget,
                    compassViewModel = compassViewModel,
                    gnssRepository = gnssRepository,
                    onNavigateToMap = { navController.popBackStack() },
                )
            }
        }
    }
}

// ── Great-circle helpers ──────────────────────────────────────────────────────

private fun bearingTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1R = Math.toRadians(lat1)
    val lat2R = Math.toRadians(lat2)
    val y = Math.sin(dLon) * Math.cos(lat2R)
    val x = Math.cos(lat1R) * Math.sin(lat2R) - Math.sin(lat1R) * Math.cos(lat2R) * Math.cos(dLon)
    return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
}

private fun distanceTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2).let { it * it }
    return (r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
}
