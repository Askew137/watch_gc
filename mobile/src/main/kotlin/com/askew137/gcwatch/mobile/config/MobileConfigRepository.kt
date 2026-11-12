package com.askew137.gcwatch.mobile.config

import android.content.Context
import android.content.SharedPreferences
import com.askew137.gcwatch.core.communication.ConfigSerializer
import com.askew137.gcwatch.core.communication.DataPaths
import com.askew137.gcwatch.core.model.AppConfig
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber

/**
 * Single source of truth for [AppConfig] on the phone side.
 *
 * - Loads persisted config from [SharedPreferences] on construction.
 * - Pushes updates to the watch via [DataClient.putDataItem] on [DataPaths.CONFIG].
 * - Listens for watch-originated changes via [DataClient.addListener].
 */
@OptIn(ExperimentalSerializationApi::class)
class MobileConfigRepository(context: Context) : DataClient.OnDataChangedListener {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _config = MutableStateFlow(loadFromPrefs())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        Wearable.getDataClient(appContext).addListener(this)
        Timber.d("MobileConfigRepository initialized: ${_config.value}")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Persist the new [AppConfig] locally and push it to the watch.
     */
    fun updateConfig(config: AppConfig) {
        _config.value = config
        saveToPrefs(config)
        scope.launch {
            pushToWatch(config)
        }
    }

    /** Call from Application.onTerminate or when the repository is no longer needed. */
    fun release() {
        Wearable.getDataClient(appContext).removeListener(this)
    }

    // ── DataClient listener ───────────────────────────────────────────────────

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == DataPaths.CONFIG
            ) {
                try {
                    val bytes = DataMapItem.fromDataItem(event.dataItem)
                        .dataMap
                        .getByteArray(KEY_CONFIG_BYTES) ?: return@forEach
                    val config = ProtoBuf.decodeFromByteArray<AppConfig>(bytes)
                    Timber.d("Config update from watch DataClient: $config")
                    _config.value = config
                    saveToPrefs(config)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode config from DataClient event")
                }
            }
        }
        dataEvents.release()
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private fun loadFromPrefs(): AppConfig {
        val bytes = prefs.getString(KEY_CONFIG_HEX, null)?.hexToByteArray()
            ?: return AppConfig()
        return try {
            ProtoBuf.decodeFromByteArray(bytes)
        } catch (e: Exception) {
            Timber.w(e, "Corrupt config in prefs – using default")
            AppConfig()
        }
    }

    private fun saveToPrefs(config: AppConfig) {
        val bytes = ProtoBuf.encodeToByteArray(config)
        prefs.edit().putString(KEY_CONFIG_HEX, bytes.toHexString()).apply()
    }

    private suspend fun pushToWatch(config: AppConfig) {
        try {
            val bytes = ConfigSerializer.encode(config)
            val request = PutDataMapRequest.create(DataPaths.CONFIG).apply {
                dataMap.putByteArray(KEY_CONFIG_BYTES, bytes)
                // Force a data change event even if content is the same.
                dataMap.putLong("ts", System.currentTimeMillis())
            }
            Wearable.getDataClient(appContext).putDataItem(request.asPutDataRequest()).await()
            Timber.d("Config pushed to DataClient")
        } catch (e: Exception) {
            Timber.e(e, "Failed to push config to watch")
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray {
        val len = length
        val result = ByteArray(len / 2)
        for (i in 0 until len / 2) {
            result[i] = substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    companion object {
        private const val PREFS_NAME      = "mobile_config_prefs"
        private const val KEY_CONFIG_HEX  = "config_proto_hex"
        private const val KEY_CONFIG_BYTES = "config"
    }
}
