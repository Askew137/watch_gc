package com.askew137.gcwatch.wear.gnss

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Streams GPS fixes directly from [LocationManager.GPS_PROVIDER], bypassing Fused Location.
 * Uses 1-second interval when active.
 */
class HardwareGnssManager(private val context: Context) {

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun locationFlow(intervalMs: Long = 1_000L): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location).isSuccess
            }

            @Deprecated("Kept for API < 29 compat")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) {
                Timber.w("GPS provider disabled: %s", provider)
            }
        }

        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Timber.w("GPS_PROVIDER is disabled on this device")
        }

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalMs,
                /*minDistanceM=*/ 0f,
                listener
            )
            Timber.d("HardwareGnssManager: registered listener, interval=%dms", intervalMs)
        } catch (e: SecurityException) {
            Timber.e(e, "ACCESS_FINE_LOCATION not granted")
            close(e)
        }

        awaitClose {
            lm.removeUpdates(listener)
            Timber.d("HardwareGnssManager: unregistered listener")
        }
    }
}
