package com.askew137.gcwatch.wear.ui.map

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

private const val ZOOM_DEFAULT = 14f
private const val ZOOM_MIN = 8f
private const val ZOOM_MAX = 19f

/**
 * ViewModel for the map screen. Manages zoom, map rotation, and rotation-lock state.
 */
class MapViewModel : ViewModel() {

    private val _zoom = MutableStateFlow(ZOOM_DEFAULT)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _mapRotationDeg = MutableStateFlow(0f)
    val mapRotationDeg: StateFlow<Float> = _mapRotationDeg.asStateFlow()

    private val _isRotationLocked = MutableStateFlow(false)
    val isRotationLocked: StateFlow<Boolean> = _isRotationLocked.asStateFlow()

    fun zoomIn() {
        _zoom.value = min(_zoom.value + 1f, ZOOM_MAX)
        Timber.d("MapViewModel: zoom -> %.1f", _zoom.value)
    }

    fun zoomOut() {
        _zoom.value = max(_zoom.value - 1f, ZOOM_MIN)
        Timber.d("MapViewModel: zoom -> %.1f", _zoom.value)
    }

    /** Delta in tiles; mapped from rotary scroll events. */
    fun zoomBy(delta: Float) {
        _zoom.value = (_zoom.value + delta).coerceIn(ZOOM_MIN, ZOOM_MAX)
    }

    /**
     * Locks map rotation to the current sensor azimuth so "up" = current heading.
     */
    fun snapBearing(sensorAzimuthDeg: Float) {
        _mapRotationDeg.value = sensorAzimuthDeg
        _isRotationLocked.value = true
        Timber.d("MapViewModel: bearing snapped to %.1f deg", sensorAzimuthDeg)
    }

    /** Releases bearing lock and resets to north-up. */
    fun unlock() {
        _isRotationLocked.value = false
        _mapRotationDeg.value = 0f
        Timber.d("MapViewModel: rotation lock released")
    }
}
