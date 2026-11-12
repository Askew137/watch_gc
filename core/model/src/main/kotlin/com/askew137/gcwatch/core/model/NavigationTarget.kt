package com.askew137.gcwatch.core.model

import kotlinx.serialization.Serializable

/**
 * Live navigation state towards an active target.
 * @param bearingDeg  Bearing from current position to target, 0–360°.
 * @param distanceM   Great-circle distance in metres.
 */
@Serializable
data class NavigationTarget(
    val waypoint: Waypoint,
    val bearingDeg: Float = 0f,
    val distanceM: Float = 0f,
)
