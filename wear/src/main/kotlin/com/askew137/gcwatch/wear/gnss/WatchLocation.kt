package com.askew137.gcwatch.wear.gnss

/**
 * Location-compatible data class emitted by both [HardwareGnssManager] and [PhoneGnssReceiver].
 *
 * @param lat        WGS-84 latitude in degrees.
 * @param lon        WGS-84 longitude in degrees.
 * @param bearingDeg Bearing in degrees (0-360), NaN if unavailable.
 * @param accuracyM  Horizontal accuracy in metres.
 * @param speedMps   Speed in m/s, NaN if unavailable.
 */
data class WatchLocation(
    val lat: Double,
    val lon: Double,
    val bearingDeg: Float,
    val accuracyM: Float,
    val speedMps: Float,
) {
    companion object {
        fun fromAndroidLocation(loc: android.location.Location) = WatchLocation(
            lat = loc.latitude,
            lon = loc.longitude,
            bearingDeg = if (loc.hasBearing()) loc.bearing else Float.NaN,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else Float.NaN,
            speedMps = if (loc.hasSpeed()) loc.speed else Float.NaN,
        )
    }
}
