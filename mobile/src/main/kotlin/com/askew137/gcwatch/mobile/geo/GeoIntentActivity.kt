package com.askew137.gcwatch.mobile.geo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.askew137.gcwatch.core.communication.MessagePaths
import com.askew137.gcwatch.core.communication.WaypointSerializer
import com.askew137.gcwatch.core.model.Waypoint
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Transparent trampoline activity that handles `geo:` URIs from c:geo, Google Maps, etc.
 * It decodes the URI, builds a [Waypoint], and forwards it to every connected Wear OS node.
 *
 * Supported URI forms:
 *  - `geo:lat,lon`
 *  - `geo:0,0?q=lat,lon(Label)`
 *  - `geo:0,0?q=lat,lon`
 */
class GeoIntentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    /** An already-alive instance can receive another link; never drop it silently. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Theme.NoDisplay: finish() MUST be called before onResume() completes, so we parse
        // synchronously, hand the network work to a process-wide scope and finish right away.
        val uri = intent?.data
        if (uri == null) {
            Timber.w("GeoIntentActivity launched with null URI")
            finish()
            return
        }
        Timber.d("Handling geo URI: $uri")

        val waypoint = parseGeoUri(uri)
        if (waypoint == null) {
            Timber.e("Failed to parse geo URI: $uri")
            finish()
            return
        }

        val appContext = applicationContext
        sendScope.launch { forwardToWatch(appContext, waypoint) }
        finish()
    }

    private suspend fun forwardToWatch(context: Context, waypoint: Waypoint) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Timber.w("No connected Wear OS nodes – dropping waypoint")
                return
            }
            val payload = WaypointSerializer.encode(waypoint)
            val client = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                client.sendMessage(node.id, MessagePaths.SET_TARGET, payload).await()
                Timber.i("Sent waypoint ${waypoint.code.ifEmpty { waypoint.label }} to ${node.displayName}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error forwarding geo URI waypoint to watch")
        }
    }

    companion object {
        /** Outlives the (immediately finished) activity so the message is still sent. */
        private val sendScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // lat,lon with optional "(label)" – used for the `q=` parameter.
        private val coordRegex = Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)(?:\s*\(([^)]*)\))?""")

        // GC code embedded in a label, e.g. "GC1A2B3 – Some Cache".
        private val gcCodeRegex = Regex("""\b(GC[A-Z0-9]{2,})\b""", RegexOption.IGNORE_CASE)

        /**
         * Supported forms (all are opaque URIs, so no Uri.getQueryParameter):
         *  - `geo:lat,lon`            (optionally `;u=…`, `?z=…`)
         *  - `geo:0,0?q=lat,lon(Label)`
         *  - `geo:lat,lon?q=Label`
         */
        internal fun parseGeoUri(uri: Uri): Waypoint? = try {
            val ssp = uri.schemeSpecificPart ?: return null
            val path = ssp.substringBefore('?').substringBefore(';')
            val query = ssp.substringAfter('?', "")
            val q = query.split('&')
                .firstOrNull { it.startsWith("q=") }
                ?.removePrefix("q=")
                ?.let { Uri.decode(it.replace('+', ' ')) }

            val pathParts = path.split(',')
            val pLat = pathParts.getOrNull(0)?.trim()?.toDoubleOrNull()
            val pLon = pathParts.getOrNull(1)?.trim()?.toDoubleOrNull()
            val hasPathCoords = pLat != null && pLon != null && !(pLat == 0.0 && pLon == 0.0)

            val qMatch = q?.let { coordRegex.find(it) }

            when {
                qMatch != null -> {
                    val lat = qMatch.groupValues[1].toDouble()
                    val lon = qMatch.groupValues[2].toDouble()
                    build(lat, lon, qMatch.groupValues[3].trim())
                }
                hasPathCoords -> build(pLat!!, pLon!!, q?.trim().orEmpty())
                else -> null
            }?.takeIf { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
        } catch (e: Exception) {
            Timber.e(e, "Exception parsing geo URI")
            null
        }

        private fun build(lat: Double, lon: Double, label: String): Waypoint {
            val code = gcCodeRegex.find(label)?.groupValues?.get(1)?.uppercase() ?: ""
            return Waypoint(lat = lat, lon = lon, label = label, code = code)
        }
    }
}
