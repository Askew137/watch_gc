package com.askew137.gcwatch.wear.config

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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private const val PREFS_NAME = "gc_watch_config"
private const val KEY_CONFIG = "app_config"
private const val DATA_KEY_CONFIG = "config_bytes"

/**
 * Repository for [AppConfig]. Persists to [SharedPreferences] and syncs bidirectionally
 * with the paired phone via [DataClient].
 */
class ConfigRepository(private val context: Context) : DataClient.OnDataChangedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dataClient: DataClient = Wearable.getDataClient(context)

    private val _config = MutableStateFlow(loadFromPrefs())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        dataClient.addListener(this)
        Timber.d("ConfigRepository: initialized with %s", _config.value)
    }

    /** Update config locally, persist to prefs, and push to DataClient. */
    fun updateConfig(newConfig: AppConfig) {
        _config.value = newConfig
        saveToPrefs(newConfig)
        pushToDataClient(newConfig)
    }

    /** Called by [com.askew137.gcwatch.wear.navigation.WearMessageService] on CONFIG_SYNC message. */
    fun onRemoteSync(bytes: ByteArray) {
        try {
            val incoming = ConfigSerializer.decode(bytes)
            _config.value = incoming
            saveToPrefs(incoming)
            Timber.d("ConfigRepository: remote sync applied %s", incoming)
        } catch (e: Exception) {
            Timber.e(e, "ConfigRepository: failed to decode remote config")
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == DataPaths.CONFIG
            ) {
                try {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val bytes = dataMap.getByteArray(DATA_KEY_CONFIG) ?: return@forEach
                    val incoming = ConfigSerializer.decode(bytes)
                    _config.value = incoming
                    saveToPrefs(incoming)
                    Timber.d("ConfigRepository: DataClient sync applied %s", incoming)
                } catch (e: Exception) {
                    Timber.e(e, "ConfigRepository: DataClient sync failed")
                }
            }
        }
        events.release()
    }

    private fun pushToDataClient(config: AppConfig) {
        scope.launch {
            try {
                val request = PutDataMapRequest.create(DataPaths.CONFIG).apply {
                    dataMap.putByteArray(DATA_KEY_CONFIG, ConfigSerializer.encode(config))
                }
                dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
                Timber.d("ConfigRepository: pushed config to DataClient")
            } catch (e: Exception) {
                Timber.e(e, "ConfigRepository: failed to push config to DataClient")
            }
        }
    }

    private fun loadFromPrefs(): AppConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return AppConfig()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            Timber.e(e, "ConfigRepository: corrupt prefs; resetting to default")
            AppConfig()
        }
    }

    private fun saveToPrefs(config: AppConfig) {
        prefs.edit().putString(KEY_CONFIG, Json.encodeToString(config)).apply()
    }

    fun destroy() {
        dataClient.removeListener(this)
    }
}
