package com.askew137.gcwatch.wear.navigation

import com.askew137.gcwatch.core.communication.RouteSerializer
import com.askew137.gcwatch.core.communication.WaypointSerializer
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Holds the active navigation target, the route the phone pushed for it, and how the map
 * currently shows the connection to the target ([TargetLineMode]).
 *
 * The phone sends the waypoint and, right after it, the route (if it can compute one); the
 * watch never asks. Cycling [cycleLineMode] therefore never waits for anything.
 */
class NavigationRepository {

    private val _activeTarget = MutableStateFlow<Waypoint?>(null)
    val activeTarget: StateFlow<Waypoint?> = _activeTarget.asStateFlow()

    private val _currentRoute = MutableStateFlow<ComputedRoute?>(null)
    val currentRoute: StateFlow<ComputedRoute?> = _currentRoute.asStateFlow()

    private val _lineMode = MutableStateFlow(TargetLineMode.AIR_LINE)
    val lineMode: StateFlow<TargetLineMode> = _lineMode.asStateFlow()

    /** Called by [WearMessageService] when SET_TARGET arrives. */
    fun onSetTarget(bytes: ByteArray) {
        try {
            val waypoint = WaypointSerializer.decode(bytes)
            _activeTarget.value = waypoint
            // A route belongs to its target: drop the old one. The phone pushes the new route
            // (if any) right after the waypoint. A new target always starts with the air line.
            _currentRoute.value = null
            _lineMode.value = TargetLineMode.AIR_LINE
            Timber.d("NavigationRepository: target set to %s", waypoint.label)
        } catch (e: Exception) {
            Timber.e(e, "NavigationRepository: failed to decode Waypoint")
        }
    }

    /**
     * Called by [WearMessageService] when ROUTE_RESULT arrives. The route stays until the next
     * route (or target) arrives; the display mode is deliberately not changed.
     */
    fun onRouteResult(bytes: ByteArray) {
        try {
            val route = RouteSerializer.decode(bytes)
            _currentRoute.value = route
            Timber.d(
                "NavigationRepository: route received, segments=%d, dist=%.0fm",
                route.segments.size,
                route.totalDistanceM
            )
        } catch (e: Exception) {
            Timber.e(e, "NavigationRepository: failed to decode ComputedRoute")
        }
    }

    /**
     * Air line -> route (only if a route exists) -> target only -> air line.
     * Returns the new mode, or null when there is no target.
     */
    fun cycleLineMode(): TargetLineMode? {
        if (_activeTarget.value == null) return null
        val next = when (_lineMode.value) {
            TargetLineMode.AIR_LINE ->
                if (_currentRoute.value != null) TargetLineMode.ROUTE else TargetLineMode.OFF
            TargetLineMode.ROUTE -> TargetLineMode.OFF
            TargetLineMode.OFF -> TargetLineMode.AIR_LINE
        }
        _lineMode.value = next
        Timber.d("NavigationRepository: line mode -> %s", next)
        return next
    }

    /** Clears the active route (target is kept). */
    fun clearRoute() {
        _currentRoute.value = null
        Timber.d("NavigationRepository: route cleared")
    }

    /** Clears both the active target and route. */
    fun clearAll() {
        _activeTarget.value = null
        _currentRoute.value = null
        _lineMode.value = TargetLineMode.AIR_LINE
        Timber.d("NavigationRepository: target and route cleared")
    }
}
