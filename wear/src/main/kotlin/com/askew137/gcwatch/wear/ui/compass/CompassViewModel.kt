package com.askew137.gcwatch.wear.ui.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import timber.log.Timber

/**
 * ViewModel for the compass screen.
 * Fuses [Sensor.TYPE_ROTATION_VECTOR] via [SensorManager.getRotationMatrix] +
 * [SensorManager.getOrientation] to produce [azimuthDeg].
 */
class CompassViewModel : ViewModel() {

    private val _azimuthDeg = MutableStateFlow(0f)
    val azimuthDeg: StateFlow<Float> = _azimuthDeg.asStateFlow()

    private val _showCardinals = MutableStateFlow(true)
    val showCardinals: StateFlow<Boolean> = _showCardinals.asStateFlow()

    /** Keep the display on while the compass is shown (default on: walking to a cache). */
    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /** Call from Activity.onResume / LaunchedEffect. */
    fun startSensor(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm

        val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            Timber.w("CompassViewModel: TYPE_ROTATION_VECTOR not available")
            return
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    // orientationAngles[0] = azimuth in radians
                    val azRad = orientationAngles[0]
                    val azDeg = Math.toDegrees(azRad.toDouble()).toFloat()
                    _azimuthDeg.value = (azDeg + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        sensorListener = listener
        Timber.d("CompassViewModel: sensor registered")
    }

    /**
     * Reads the current heading once (first rotation-vector sample) and unregisters again,
     * so the map screen does not need to keep the sensor running. Returns null if the
     * sensor is unavailable or no sample arrives within [timeoutMs].
     */
    suspend fun readAzimuthOnce(context: Context, timeoutMs: Long = 1_500L): Float? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val matrix = FloatArray(9)
                val angles = FloatArray(3)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        SensorManager.getOrientation(matrix, angles)
                        val az = (Math.toDegrees(angles[0].toDouble()).toFloat() + 360f) % 360f
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(az)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    /** Call from Activity.onPause / DisposableEffect cleanup. */
    fun stopSensor() {
        sensorListener?.let { sensorManager?.unregisterListener(it) }
        sensorListener = null
        Timber.d("CompassViewModel: sensor unregistered")
    }

    fun toggleCardinals() {
        _showCardinals.value = !_showCardinals.value
    }

    fun toggleKeepScreenOn() {
        _keepScreenOn.value = !_keepScreenOn.value
        Timber.d("CompassViewModel: keepScreenOn=%s", _keepScreenOn.value)
    }

    override fun onCleared() {
        super.onCleared()
        stopSensor()
    }
}
