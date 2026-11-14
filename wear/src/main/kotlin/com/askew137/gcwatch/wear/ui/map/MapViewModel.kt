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
 * ViewModel for the map screen. Manages zoom and map rotation.
 */
class MapViewModel : ViewModel() {

    private val _zoom = MutableStateFlow(ZOOM_DEFAULT)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _mapRotationDeg = MutableStateFlow(0f)
    val mapRotationDeg: StateFlow<Float> = _mapRotationDeg.asStateFlow()

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
     * One-shot: rotates the map so that the direction the user is facing
     * ([headingDeg], 0 = north, clockwise) points to the top of the screen.
     * The canvas is rotated clockwise by [mapRotationDeg], so "heading up" needs -heading.
     */
    fun alignToHeading(headingDeg: Float) {
        _mapRotationDeg.value = (360f - headingDeg) % 360f
        Timber.d("MapViewModel: aligned to heading %.1f deg", headingDeg)
    }

    /** Back to the default view: north up (the map is always centred on the position). */
    fun resetNorthUp() {
        _mapRotationDeg.value = 0f
        Timber.d("MapViewModel: north-up")
    }
}
