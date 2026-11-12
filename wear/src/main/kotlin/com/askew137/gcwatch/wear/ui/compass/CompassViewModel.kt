package com.askew137.gcwatch.wear.ui.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _wakeLockActive = MutableStateFlow(false)
    val wakeLockActive: StateFlow<Boolean> = _wakeLockActive.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

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

    /** Call from Activity.onPause / DisposableEffect cleanup. */
    fun stopSensor() {
        sensorListener?.let { sensorManager?.unregisterListener(it) }
        sensorListener = null
        Timber.d("CompassViewModel: sensor unregistered")
    }

    fun toggleCardinals() {
        _showCardinals.value = !_showCardinals.value
    }

    fun toggleWakeLock(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val current = wakeLock

        if (current != null && current.isHeld) {
            current.release()
            wakeLock = null
            _wakeLockActive.value = false
            Timber.d("CompassViewModel: WakeLock released")
        } else {
            val wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "GcWatch::CompassWakeLock"
            )
            wl.acquire(10 * 60 * 1000L) // max 10 minutes
            wakeLock = wl
            _wakeLockActive.value = true
            Timber.d("CompassViewModel: WakeLock acquired")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSensor()
        wakeLock?.takeIf { it.isHeld }?.release()
    }
}
