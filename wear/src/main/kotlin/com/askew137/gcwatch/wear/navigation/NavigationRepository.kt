package com.askew137.gcwatch.wear.navigation

import android.content.Context
import com.askew137.gcwatch.core.communication.MessagePaths
import com.askew137.gcwatch.core.communication.RouteRequest
import com.askew137.gcwatch.core.communication.RouteSerializer
import com.askew137.gcwatch.core.communication.WaypointSerializer
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.Waypoint
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Holds the active navigation target, the route the phone computed for it, and how the map
 * currently shows the connection to the target ([TargetLineMode]).
 *
 * As soon as a target is set and the watch has a GPS fix, the route is requested from the phone
 * automatically ([requestRoute]); it is computed in the background, so cycling
 * [cycleLineMode] never waits for anything.
 */
class NavigationRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var routeTimeout: Job? = null

    /** Set when a target arrives; cleared once the route request was sent. */
    private val _routeRequestNeeded = MutableStateFlow<Waypoint?>(null)
    val routeRequestNeeded: StateFlow<Waypoint?> = _routeRequestNeeded.asStateFlow()

    /** True from sending the request until the phone answers (or the wait times out). */
    private val _routePending = MutableStateFlow(false)
    val routePending: StateFlow<Boolean> = _routePending.asStateFlow()

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
            _routePending.value = false
            routeTimeout?.cancel()
            _routeRequestNeeded.value = waypoint
            Timber.d("NavigationRepository: target set to %s", waypoint.label)
        } catch (e: Exception) {
            Timber.e(e, "NavigationRepository: failed to decode Waypoint")
        }
    }

    /**
     * Asks the phone for a route from ([fromLat], [fromLon]) to [target]. Fire and forget: the
     * answer arrives via [onRouteResult].
     */
    fun requestRoute(fromLat: Double, fromLon: Double, target: Waypoint) {
        _routeRequestNeeded.value = null
        _routePending.value = true
        routeTimeout?.cancel()
        routeTimeout = scope.launch {
            delay(ROUTE_WAIT_MS)
            if (_routePending.value) {
                Timber.w("NavigationRepository: no route answer from the phone")
                _routePending.value = false
            }
        }
        scope.launch {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isEmpty()) {
                    Timber.w("NavigationRepository: no connected phone; cannot request route")
                    _routePending.value = false
                    return@launch
                }
                val payload = RouteRequest.encode(
                    RouteRequest(fromLat, fromLon, target.lat, target.lon)
                )
                val client = Wearable.getMessageClient(context)
                nodes.forEach { Tasks.await(client.sendMessage(it.id, MessagePaths.ROUTE_REQUEST, payload)) }
                Timber.d("NavigationRepository: route requested from %d node(s)", nodes.size)
            } catch (e: Exception) {
                Timber.e(e, "NavigationRepository: failed to send route request")
                _routePending.value = false
            }
        }
    }

    /**
     * Called by [WearMessageService] when ROUTE_RESULT arrives. An empty route means the phone
     * has none (missing data, no road nearby). The route stays until the next route (or
     * target) arrives; the display mode is deliberately not changed.
     */
    fun onRouteResult(bytes: ByteArray) {
        try {
            val route = RouteSerializer.decode(bytes).takeIf { it.segments.isNotEmpty() }
            _routePending.value = false
            routeTimeout?.cancel()
            if (route == null) {
                Timber.d("NavigationRepository: phone has no route for this target")
                return
            }
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

    private companion object {
        const val ROUTE_WAIT_MS = 75_000L
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
        _routeRequestNeeded.value = null
        _routePending.value = false
        Timber.d("NavigationRepository: target and route cleared")
    }
}
