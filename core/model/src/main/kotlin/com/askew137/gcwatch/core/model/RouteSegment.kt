package com.askew137.gcwatch.core.model

import kotlinx.serialization.Serializable

/** A single point along a computed route. */
@Serializable
data class RoutePoint(val lat: Double, val lon: Double)

@Serializable
enum class SegmentType {
    /** On-road / on-trail segment computed by GraphHopper. */
    ROAD,
    /** Straight dashed line from last road node to geocache. */
    DASHED_DIRECT,
}

@Serializable
data class RouteSegment(
    val points: List<RoutePoint>,
    val type: SegmentType,
)

/** Full route from current position to cache, computed on the phone. */
@Serializable
data class ComputedRoute(
    val segments: List<RouteSegment>,
    val totalDistanceM: Float,
)
