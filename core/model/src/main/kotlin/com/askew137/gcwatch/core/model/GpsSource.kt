package com.askew137.gcwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GpsSource {
    /** FusedLocationProviderClient via phone BT tether – lower power, slightly lower accuracy. */
    PHONE_FUSED,
    /** Direct LocationManager.GPS_PROVIDER on the watch hardware – L1+L5, fully autonomous. */
    WATCH_HARDWARE,
}
