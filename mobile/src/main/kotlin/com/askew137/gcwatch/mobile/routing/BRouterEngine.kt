package com.askew137.gcwatch.mobile.routing

import android.content.Context
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.RoutePoint
import com.askew137.gcwatch.core.model.RouteSegment
import com.askew137.gcwatch.core.model.SegmentType
import timber.log.Timber
import java.io.File
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * On-foot routing with the vendored BRouter core (MIT, see third_party/brouter/README.md).
 *
 * Needs routing data (`*.rd5`, 5x5 degree tiles from brouter.de) in [segmentsDir]; see
 * [SegmentRepository]. The hiking profile and `lookups.dat` ship as assets and are copied to
 * the profiles directory because the engine reads them by file path.
 */
object BRouterEngine {

    sealed interface Result {
        data class Success(val route: ComputedRoute) : Result

        /** The engine needs a data tile that is not installed, e.g. `E10_N45.rd5`. */
        data class MissingData(val tile: String) : Result

        data class Failure(val message: String) : Result
    }

    private const val PROFILE = "hiking-mountain.brf"
    private val ASSETS = listOf("lookups.dat", PROFILE)
    private const val MAX_RUNNING_TIME_MS = 45_000L

    /** A gap between the end of the road network and the cache longer than this is drawn dashed. */
    private const val DASHED_GAP_M = 50.0

    /** Keeps the message to the watch comfortably below the Data Layer limit (~100 KB). */
    private const val MAX_POINTS = 2_000

    private val missingTileRegex = Regex("""datafile\s+(\S+\.rd5)\s+not found""")

    // The engine keeps global caches and is not meant to run concurrently.
    private val lock = Any()

    fun baseDir(context: Context) = File(context.filesDir, "brouter")
    fun segmentsDir(context: Context) = File(baseDir(context), "segments4").also { it.mkdirs() }
    private fun profilesDir(context: Context) = File(baseDir(context), "profiles").also { it.mkdirs() }

    /** Copies the profile assets next to each other (the engine finds lookups.dat by path). */
    private fun ensureProfiles(context: Context): File {
        val dir = profilesDir(context)
        for (name in ASSETS) {
            val target = File(dir, name)
            val bytes = context.assets.open("brouter/$name").use { it.readBytes() }
            if (!target.exists() || !target.readBytes().contentEquals(bytes)) {
                target.writeBytes(bytes)
            }
        }
        return File(dir, PROFILE)
    }

    /** Route on foot from [fromLat]/[fromLon] to the cache at [toLat]/[toLon]. Blocking. */
    fun compute(context: Context, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Result =
        synchronized(lock) {
            try {
                val profile = ensureProfiles(context)
                val rc = RoutingContext().apply { localFunction = profile.absolutePath }
                // Must be mutable: the engine modifies the list.
                val waypoints = mutableListOf(node("from", fromLat, fromLon), node("to", toLat, toLon))

                val engine = RoutingEngine(null, null, segmentsDir(context), waypoints, rc, 0)
                engine.quite = true
                engine.doRun(MAX_RUNNING_TIME_MS)

                val error: String? = engine.getErrorMessage()
                val track = engine.getFoundTrack()
                if (error != null || track == null || track.nodes.isEmpty()) {
                    missingTileRegex.find(error.orEmpty())?.let {
                        Timber.w("BRouter: missing routing data %s", it.groupValues[1])
                        return@synchronized Result.MissingData(it.groupValues[1])
                    }
                    Timber.w("BRouter: no route (%s)", error)
                    return@synchronized Result.Failure(error ?: "no route")
                }

                val points = track.nodes.map {
                    RoutePoint(lat = it.getILat() / 1e6 - 90.0, lon = it.getILon() / 1e6 - 180.0)
                }
                Result.Success(toComputedRoute(points, toLat, toLon, track.distance.toFloat()))
            } catch (t: Throwable) {
                Timber.e(t, "BRouter failed")
                Result.Failure(t.message ?: t.javaClass.simpleName)
            }
        }

    private fun node(name: String, lat: Double, lon: Double) = OsmNodeNamed().apply {
        this.name = name
        ilon = ((lon + 180.0) * 1_000_000.0 + 0.5).toInt()
        ilat = ((lat + 90.0) * 1_000_000.0 + 0.5).toInt()
    }

    /** ROAD segment for the network part, plus a dashed leg when the road ends far from the cache. */
    internal fun toComputedRoute(
        roadPoints: List<RoutePoint>,
        toLat: Double,
        toLon: Double,
        roadDistanceM: Float,
    ): ComputedRoute {
        val simplified = simplify(roadPoints)
        val segments = mutableListOf(RouteSegment(points = simplified, type = SegmentType.ROAD))
        var total = roadDistanceM
        val last = simplified.last()
        val gap = distanceM(last.lat, last.lon, toLat, toLon)
        if (gap > DASHED_GAP_M) {
            segments += RouteSegment(
                points = listOf(last, RoutePoint(toLat, toLon)),
                type = SegmentType.DASHED_DIRECT,
            )
            total += gap.toFloat()
        }
        return ComputedRoute(segments = segments, totalDistanceM = total)
    }

    /** Douglas–Peucker with a growing tolerance until at most [MAX_POINTS] points remain. */
    internal fun simplify(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size <= 2) return points
        var tolerance = 2.0
        var result = douglasPeucker(points, tolerance)
        while (result.size > MAX_POINTS) {
            tolerance *= 2
            result = douglasPeucker(points, tolerance)
        }
        return result
    }

    private fun douglasPeucker(points: List<RoutePoint>, toleranceM: Double): List<RoutePoint> {
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<IntArray>()
        stack.add(intArrayOf(0, points.lastIndex))
        val cosLat = cos(Math.toRadians(points[0].lat))
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            var maxDist = 0.0
            var index = -1
            for (i in start + 1 until end) {
                val d = perpendicularDistanceM(points[i], points[start], points[end], cosLat)
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index != -1 && maxDist > toleranceM) {
                keep[index] = true
                stack.add(intArrayOf(start, index))
                stack.add(intArrayOf(index, end))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun perpendicularDistanceM(p: RoutePoint, a: RoutePoint, b: RoutePoint, cosLat: Double): Double {
        // Local equirectangular projection, metres.
        val k = 111_320.0
        val ax = a.lon * k * cosLat
        val ay = a.lat * k
        val bx = b.lon * k * cosLat
        val by = b.lat * k
        val px = p.lon * k * cosLat
        val py = p.lat * k
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val s1 = Math.sin(dLat / 2)
        val s2 = Math.sin(dLon / 2)
        val a = s1 * s1 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * s2 * s2
        return 2 * r * Math.asin(sqrt(a))
    }
}
