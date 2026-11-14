package com.askew137.gcwatch.core.communication

object MessagePaths {
    /** Phone → Watch: set active navigation target. Payload: protobuf Waypoint. */
    const val SET_TARGET = "/set_target"

    /**
     * Phone → Watch: route to the active target, pushed right after SET_TARGET (not requested
     * by the watch). Payload: protobuf ComputedRoute.
     */
    const val ROUTE_RESULT = "/route_result"

    /** Bidirectional: config changed. Payload: protobuf AppConfig. */
    const val CONFIG_SYNC = "/config_sync"

    /** Phone → Watch: live location update when GpsSource = PHONE_FUSED. Payload: protobuf PhoneLocation. */
    const val PHONE_LOCATION = "/phone_location"

    /** Watch → Phone: watch-side GPS fix for phone's awareness. Payload: protobuf PhoneLocation. */
    const val WATCH_LOCATION = "/watch_location"
}

/** DataClient item path for persistent config sync. */
object DataPaths {
    const val CONFIG = "/data/config"
}
