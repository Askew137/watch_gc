package com.askew137.gcwatch.mobile.wearable

import android.content.Intent
import com.askew137.gcwatch.core.communication.ConfigSerializer
import com.askew137.gcwatch.core.communication.MessagePaths
import com.askew137.gcwatch.core.communication.RouteRequest
import com.askew137.gcwatch.core.communication.RouteSerializer
import com.askew137.gcwatch.mobile.routing.GraphHopperEngine
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.graphhopper.util.shapes.GHPoint
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
 *  - [MessagePaths.ROUTE_REQUEST] – decodes a [RouteRequest], calls [GraphHopperEngine],
 *    encodes the resulting [ComputedRoute] and sends it back via [MessagePaths.ROUTE_RESULT].
 *  - [MessagePaths.CONFIG_SYNC] – decodes [AppConfig] and persists it to SharedPreferences,
 *    then broadcasts [ACTION_CONFIG_UPDATED] so the UI can react without polling.
 */
class MobileMessageService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("Message received: path=${event.path} from=${event.sourceNodeId}")
        when (event.path) {
            MessagePaths.ROUTE_REQUEST -> handleRouteRequest(event)
            MessagePaths.CONFIG_SYNC   -> handleConfigSync(event)
            else -> Timber.w("Unhandled message path: ${event.path}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Route request ────────────────────────────────────────────────────────

    private fun handleRouteRequest(event: MessageEvent) {
        scope.launch {
            try {
                val request = RouteRequest.decode(event.data)
                Timber.d("RouteRequest from=${request.fromLat},${request.fromLon} to=${request.toLat},${request.toLon}")

                val from = GHPoint(request.fromLat, request.fromLon)
                val to   = GHPoint(request.toLat,   request.toLon)
                val route = GraphHopperEngine.computeRoute(applicationContext, from, to)

                val payload = RouteSerializer.encode(route)
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(event.sourceNodeId, MessagePaths.ROUTE_RESULT, payload)
                    .await()
                Timber.i("Route sent: ${route.segments.size} segments, ${route.totalDistanceM.toInt()} m")
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle ROUTE_REQUEST")
            }
        }
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
