package com.askew137.gcwatch.mobile.routing

import com.askew137.gcwatch.core.model.RoutePoint
import com.askew137.gcwatch.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BRouterEngineTest {

    // ~0.00009 deg of latitude is about 10 m.
    private fun northwards(count: Int) = (0 until count).map { RoutePoint(50.0 + it * 0.00009, 14.0) }

    @Test
    fun simplifyDropsCollinearPoints() {
        val simplified = BRouterEngine.simplify(northwards(200))
        assertEquals(2, simplified.size)
        assertEquals(northwards(200).first(), simplified.first())
        assertEquals(northwards(200).last(), simplified.last())
    }

    @Test
    fun simplifyKeepsCorners() {
        // 500 m north, then 500 m east: the corner must survive.
        val north = (0..50).map { RoutePoint(50.0 + it * 0.00009, 14.0) }
        val corner = north.last()
        val east = (1..50).map { RoutePoint(corner.lat, 14.0 + it * 0.00014) }
        val simplified = BRouterEngine.simplify(north + east)
        assertEquals(3, simplified.size)
        assertEquals(corner, simplified[1])
    }

    @Test
    fun simplifyLeavesShortRoutesAlone() {
        val two = northwards(2)
        assertEquals(two, BRouterEngine.simplify(two))
    }

    @Test
    fun routeEndingNearTheTargetHasNoDashedLeg() {
        val road = northwards(100)
        val last = road.last()
        val route = BRouterEngine.toComputedRoute(road, last.lat + 0.0002, last.lon, 990f) // ~22 m away
        assertEquals(1, route.segments.size)
        assertEquals(SegmentType.ROAD, route.segments[0].type)
        assertEquals(990f, route.totalDistanceM, 0.01f)
    }

    @Test
    fun routeEndingFarFromTheTargetGetsADashedLeg() {
        val road = northwards(100)
        val last = road.last()
        val route = BRouterEngine.toComputedRoute(road, last.lat + 0.0020, last.lon, 990f) // ~222 m away
        assertEquals(2, route.segments.size)
        val dashed = route.segments[1]
        assertEquals(SegmentType.DASHED_DIRECT, dashed.type)
        assertEquals(last, dashed.points.first())
        assertEquals(RoutePoint(last.lat + 0.0020, last.lon), dashed.points.last())
        assertTrue(route.totalDistanceM in 1190f..1230f)
    }
}
