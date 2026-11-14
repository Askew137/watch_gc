package com.askew137.gcwatch.core.communication

import com.askew137.gcwatch.core.model.Waypoint
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.core.model.ComputedRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
object WaypointSerializer {
    fun encode(waypoint: Waypoint): ByteArray = ProtoBuf.encodeToByteArray(waypoint)
    fun decode(bytes: ByteArray): Waypoint = ProtoBuf.decodeFromByteArray(bytes)
}

@OptIn(ExperimentalSerializationApi::class)
object ConfigSerializer {
    fun encode(config: AppConfig): ByteArray = ProtoBuf.encodeToByteArray(config)
    fun decode(bytes: ByteArray): AppConfig = ProtoBuf.decodeFromByteArray(bytes)
}

@OptIn(ExperimentalSerializationApi::class)
object RouteSerializer {
    fun encode(route: ComputedRoute): ByteArray = ProtoBuf.encodeToByteArray(route)
    fun decode(bytes: ByteArray): ComputedRoute = ProtoBuf.decodeFromByteArray(bytes)
}

/** Sent from the watch to the phone to request an on-foot route. */
@OptIn(ExperimentalSerializationApi::class)
@kotlinx.serialization.Serializable
data class RouteRequest(
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
) {
    companion object {
        fun encode(req: RouteRequest): ByteArray = ProtoBuf.encodeToByteArray(req)
        fun decode(bytes: ByteArray): RouteRequest = ProtoBuf.decodeFromByteArray(bytes)
    }
}
