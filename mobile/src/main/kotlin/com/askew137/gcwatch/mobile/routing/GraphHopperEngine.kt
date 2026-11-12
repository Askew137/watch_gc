package com.askew137.gcwatch.mobile.routing

import android.content.Context
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.RoutePoint
import com.askew137.gcwatch.core.model.RouteSegment
import com.askew137.gcwatch.core.model.SegmentType
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.Parameters
import com.graphhopper.util.shapes.GHPoint
import timber.log.Timber
import java.io.File
import kotlin.math.sqrt

/**
 * Singleton wrapper around [GraphHopper] for offline routing on the phone.
 *
 * Graph files live in `Context.filesDir/routing/` as a GraphHopper cache directory.
 * The caller must ensure the graph has been imported (e.g. via [RoutingFileRepository])
 * before calling [computeRoute].
 */
object GraphHopperEngine {

    private var hopper: GraphHopper? = null
    private var initializedDir: String? = null

    /** Returns true if the graph directory exists and contains data. */
    fun isReady(context: Context): Boolean {
        val dir = getGraphDirectory(context)
        return dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
    }

    /** Directory where the GraphHopper graph cache is stored. */
    fun getGraphDirectory(context: Context): File =
        File(context.filesDir, "routing")

    /**
     * Compute a route from [from] to [to].
     *
     * Returns a [ComputedRoute] with:
     *  - `ROAD` segments for each GraphHopper path response leg.
     *  - A trailing `DASHED_DIRECT` segment if the route endpoint is >50 m from [to],
     *    or if no route is found at all.
     */
    fun computeRoute(context: Context, from: GHPoint, to: GHPoint): ComputedRoute {
        if (!isReady(context)) {
            Timber.w("GraphHopper graph not ready – returning direct segment")
            return directRoute(from, to)
        }

        return try {
            val gh = getOrInitHopper(context)
            val request = GHRequest(from, to)
                .setProfile("car")
                .setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI)

            val response = gh.route(request)

            if (response.hasErrors()) {
                Timber.w("GraphHopper errors: ${response.errors.joinToString { it.message ?: "?" }}")
                return directRoute(from, to)
            }

            val path = response.best
            val points = path.points
            val routePoints = (0 until points.size).map { i ->
                RoutePoint(lat = points.getLat(i), lon = points.getLon(i))
            }

            val segments = mutableListOf<RouteSegment>()

            if (routePoints.isNotEmpty()) {
                segments += RouteSegment(points = routePoints, type = SegmentType.ROAD)

                // Check if last road point is far from the actual target.
                val lastPt = routePoints.last()
                val distToTarget = haversineMeters(lastPt.lat, lastPt.lon, to.lat, to.lon)
                if (distToTarget > 50.0) {
                    segments += RouteSegment(
                        points = listOf(
                            lastPt,
                            RoutePoint(lat = to.lat, lon = to.lon),
                        ),
                        type = SegmentType.DASHED_DIRECT,
                    )
                }
            } else {
                return directRoute(from, to)
            }

            val totalDistanceM = path.distance.toFloat()
            ComputedRoute(segments = segments, totalDistanceM = totalDistanceM)
        } catch (e: Exception) {
            Timber.e(e, "GraphHopper routing failed")
            directRoute(from, to)
        }
    }

    /** Release the GraphHopper instance (call when user removes graph files). */
    fun release() {
        hopper?.close()
        hopper = null
        initializedDir = null
        Timber.d("GraphHopper instance released")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun getOrInitHopper(context: Context): GraphHopper {
        val graphDir = getGraphDirectory(context).absolutePath
        val existing = hopper
        if (existing != null && initializedDir == graphDir) return existing

        Timber.i("Initializing GraphHopper from $graphDir")
        val gh = GraphHopper()
        gh.forMobile()
        gh.graphHopperLocation = graphDir
        gh.setProfiles(Profile("car").apply {
            vehicle = "car"
            weighting = "fastest"
        })
        gh.encodingManager = EncodingManager.create("car")
        gh.load(graphDir)

        hopper = gh
        initializedDir = graphDir
        Timber.i("GraphHopper ready")
        return gh
    }

    /** Build a [ComputedRoute] with a single DASHED_DIRECT segment from [from] to [to]. */
    private fun directRoute(from: GHPoint, to: GHPoint): ComputedRoute {
        val dist = haversineMeters(from.lat, from.lon, to.lat, to.lon).toFloat()
        val seg = RouteSegment(
            points = listOf(
                RoutePoint(lat = from.lat, lon = from.lon),
                RoutePoint(lat = to.lat, lon = to.lon),
            ),
            type = SegmentType.DASHED_DIRECT,
        )
        return ComputedRoute(segments = listOf(seg), totalDistanceM = dist)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        return r * 2 * Math.atan2(sqrt(a), sqrt(1 - a))
    }
}
