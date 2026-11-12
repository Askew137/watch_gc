package com.askew137.gcwatch.wear.navigation

import android.content.Context
import com.askew137.gcwatch.core.communication.MessagePaths
import com.askew137.gcwatch.core.communication.RouteRequest
import com.askew137.gcwatch.core.communication.RouteSerializer
import com.askew137.gcwatch.core.communication.WaypointSerializer
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.Waypoint
import com.askew137.gcwatch.wear.gnss.WatchLocation
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Holds the active navigation target and computed route.
 * [requestRoute] sends a [RouteRequest] to the paired phone via MessageClient.
 */
class NavigationRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageClient = Wearable.getMessageClient(context)

    private val _activeTarget = MutableStateFlow<Waypoint?>(null)
    val activeTarget: StateFlow<Waypoint?> = _activeTarget.asStateFlow()

    private val _currentRoute = MutableStateFlow<ComputedRoute?>(null)
    val currentRoute: StateFlow<ComputedRoute?> = _currentRoute.asStateFlow()

    /** Called by [WearMessageService] when SET_TARGET arrives. */
    fun onSetTarget(bytes: ByteArray) {
        try {
            val waypoint = WaypointSerializer.decode(bytes)
            _activeTarget.value = waypoint
            Timber.d("NavigationRepository: target set to %s", waypoint.label)
        } catch (e: Exception) {
            Timber.e(e, "NavigationRepository: failed to decode Waypoint")
        }
    }

    /** Called by [WearMessageService] when ROUTE_RESULT arrives. */
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
     * Sends a [RouteRequest] to the first connected phone node.
     */
    fun requestRoute(from: WatchLocation, to: Waypoint) {
        scope.launch {
            try {
                val nodes: List<Node> = Tasks.await(
                    Wearable.getNodeClient(context).connectedNodes
                )
                if (nodes.isEmpty()) {
                    Timber.w("NavigationRepository: no connected nodes; cannot request route")
                    return@launch
                }
                val payload = RouteRequest.encode(
                    RouteRequest(
                        fromLat = from.lat,
                        fromLon = from.lon,
                        toLat = to.lat,
                        toLon = to.lon,
                    )
                )
                nodes.forEach { node ->
                    Tasks.await(
                        messageClient.sendMessage(node.id, MessagePaths.ROUTE_REQUEST, payload)
                    )
                }
                Timber.d("NavigationRepository: route request sent to %d node(s)", nodes.size)
            } catch (e: Exception) {
                Timber.e(e, "NavigationRepository: failed to send route request")
            }
        }
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
        Timber.d("NavigationRepository: target and route cleared")
    }
}
