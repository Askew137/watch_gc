package com.askew137.gcwatch.mobile.routing

import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.Waypoint

/**
 * Source of on-foot routes to a cache. The phone calls it right after a waypoint was sent to
 * the watch; a non-null result is pushed to the watch immediately (MessagePaths.ROUTE_RESULT),
 * so switching the map to "route" on the watch is instant.
 *
 * There is no routing engine yet (GraphHopper 10 does not run on Android; see PLAN.md), so
 * [Routing.provider] defaults to [NoRouteProvider]. Plug a real engine in here.
 */
interface RouteProvider {
    /** Route from the user's current position to [target], or null if none can be computed. */
    suspend fun routeTo(target: Waypoint): ComputedRoute?
}

/** No routing available: the watch keeps showing the straight line to the cache. */
object NoRouteProvider : RouteProvider {
    override suspend fun routeTo(target: Waypoint): ComputedRoute? = null
}

object Routing {
    @Volatile
    var provider: RouteProvider = NoRouteProvider
}
