package com.askew137.gcwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Waypoint(
    val lat: Double,
    val lon: Double,
    val label: String = "",
    val code: String = "",   // GC code e.g. "GC1A2B3"
)
