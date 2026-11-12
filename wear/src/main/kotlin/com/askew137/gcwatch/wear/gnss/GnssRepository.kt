package com.askew137.gcwatch.wear.gnss

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.askew137.gcwatch.core.model.GpsSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

private const val PHONE_TIMEOUT_MS = 10_000L

/**
 * ViewModel-scoped repository that arbitrates between [GpsSource.WATCH_HARDWARE] and
 * [GpsSource.PHONE_FUSED], automatically falling back to hardware if phone location
 * stops arriving for more than 10 seconds.
 */
class GnssRepository(private val context: Context) : ViewModel() {

    private val hardwareManager = HardwareGnssManager(context)
    private val phoneReceiver = PhoneGnssReceiver(context)

    private val _source = MutableStateFlow(GpsSource.WATCH_HARDWARE)
    val source: StateFlow<GpsSource> = _source.asStateFlow()

    private val _location = MutableStateFlow<WatchLocation?>(null)
    val location: StateFlow<WatchLocation?> = _location.asStateFlow()

    private var activeJob: Job? = null
    private var watchdogJob: Job? = null

    init {
        switchSource(GpsSource.WATCH_HARDWARE)
    }

    /** Cancel the previous flow and start a new one for [src]. */
    fun switchSource(src: GpsSource) {
        Timber.d("GnssRepository: switching source to %s", src)
        activeJob?.cancel()
        watchdogJob?.cancel()
        _source.value = src

        when (src) {
            GpsSource.WATCH_HARDWARE -> startHardwareFlow()
            GpsSource.PHONE_FUSED -> startPhoneFlow()
        }
    }

    private fun startHardwareFlow() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Timber.w("GnssRepository: ACCESS_FINE_LOCATION not granted; cannot start hardware GPS")
            return
        }

        activeJob = viewModelScope.launch {
            @Suppress("MissingPermission")
            hardwareManager.locationFlow(intervalMs = 1_000L).collect { loc ->
                _location.value = WatchLocation.fromAndroidLocation(loc)
            }
        }
    }

    private fun startPhoneFlow() {
        activeJob = viewModelScope.launch {
            phoneReceiver.locationFlow().collect { loc ->
                _location.value = loc
                // Reset watchdog on every received update
                watchdogJob?.cancel()
                watchdogJob = viewModelScope.launch {
                    delay(PHONE_TIMEOUT_MS)
                    Timber.w("GnssRepository: phone location timed out; falling back to WATCH_HARDWARE")
                    switchSource(GpsSource.WATCH_HARDWARE)
                }
            }
        }

        // Also start the watchdog timer immediately in case no updates ever arrive
        watchdogJob = viewModelScope.launch {
            delay(PHONE_TIMEOUT_MS)
            Timber.w("GnssRepository: phone location never received; falling back to WATCH_HARDWARE")
            switchSource(GpsSource.WATCH_HARDWARE)
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        watchdogJob?.cancel()
    }
}
