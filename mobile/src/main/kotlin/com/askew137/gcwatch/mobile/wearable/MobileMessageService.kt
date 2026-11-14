package com.askew137.gcwatch.mobile.wearable

import android.content.Intent
import com.askew137.gcwatch.core.communication.ConfigSerializer
import com.askew137.gcwatch.core.communication.MessagePaths
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * [WearableListenerService] running on the phone side.
 *
 * Handles:
 *  - [MessagePaths.CONFIG_SYNC] – decodes [AppConfig] and persists it to SharedPreferences,
 *    then broadcasts [ACTION_CONFIG_UPDATED] so the UI can react without polling.
 */
class MobileMessageService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("Message received: path=${event.path} from=${event.sourceNodeId}")
        when (event.path) {
            MessagePaths.CONFIG_SYNC   -> handleConfigSync(event)
            else -> Timber.w("Unhandled message path: ${event.path}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Config sync ──────────────────────────────────────────────────────────

    private fun handleConfigSync(event: MessageEvent) {
        scope.launch {
            try {
                val config = ConfigSerializer.decode(event.data)
                Timber.d("CONFIG_SYNC received: $config")

                // Persist to SharedPreferences for fast access.
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit().apply {
                    putString(KEY_CONFIG_PROTO, event.data.toHexString())
                    apply()
                }

                // Broadcast so running UI can update without re-launching.
                val broadcast = Intent(ACTION_CONFIG_UPDATED).apply {
                    setPackage(applicationContext.packageName)
                }
                applicationContext.sendBroadcast(broadcast)
                Timber.d("Config persisted and broadcast sent")
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle CONFIG_SYNC")
            }
        }
    }

    companion object {
        const val PREFS_NAME        = "mobile_config_prefs"
        const val KEY_CONFIG_PROTO  = "config_proto_hex"
        const val ACTION_CONFIG_UPDATED = "com.askew137.gcwatch.mobile.CONFIG_UPDATED"

        private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    }
}
