package com.askew137.gcwatch.mobile.geo

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
import kotlinx.coroutines.cancel
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Regex: captures lat, lon, and optional label from the query portion.
    private val geoQueryRegex = Regex("""(-?\d+\.?\d*),(-?\d+\.?\d*)(?:\(([^)]*)\))?""")

    // Regex: GC code embedded in label, e.g. "GC1A2B3 – Some Cache"
    private val gcCodeRegex = Regex("""(GC[A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI – this activity is Theme.NoDisplay.
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

        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@GeoIntentActivity)
                    .connectedNodes
                    .await()

                if (nodes.isEmpty()) {
                    Timber.w("No connected Wear OS nodes – dropping waypoint")
                } else {
                    val payload = WaypointSerializer.encode(waypoint)
                    val client = Wearable.getMessageClient(this@GeoIntentActivity)
                    nodes.forEach { node ->
                        client.sendMessage(node.id, MessagePaths.SET_TARGET, payload).await()
                        Timber.i("Sent waypoint ${waypoint.code.ifEmpty { waypoint.label }} to ${node.displayName}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error forwarding geo URI waypoint to watch")
            } finally {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── URI parsing ──────────────────────────────────────────────────────────

    private fun parseGeoUri(uri: Uri): Waypoint? {
        return try {
            val ssp = uri.schemeSpecificPart ?: return null

            if (ssp.startsWith("0,0") || ssp.startsWith("0.0,0.0")) {
                // geo:0,0?q=lat,lon(Label) form
                val query = uri.getQueryParameter("q") ?: return null
                parseQueryParam(query)
            } else {
                // geo:lat,lon form
                val parts = ssp.substringBefore("?").split(",")
                val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
                val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
                Waypoint(lat = lat, lon = lon)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception parsing geo URI")
            null
        }
    }

    private fun parseQueryParam(query: String): Waypoint? {
        val match = geoQueryRegex.find(query) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        val label = match.groupValues[3].trim()
        val code = gcCodeRegex.find(label)?.groupValues?.getOrNull(1)?.uppercase() ?: ""
        return Waypoint(lat = lat, lon = lon, label = label, code = code)
    }
}
