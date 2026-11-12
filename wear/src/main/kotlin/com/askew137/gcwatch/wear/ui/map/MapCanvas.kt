package com.askew137.gcwatch.wear.ui.map

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.SegmentType
import com.askew137.gcwatch.wear.map.MapEngineService
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.PI

private const val TILE_SIZE_PX = 256

data class LatLon(val lat: Double, val lon: Double)

/**
 * Renders Mapsforge map tiles, an optional route overlay, and the current-position dot.
 *
 * @param engine       The [MapEngineService] used to render tiles.
 * @param center       Current WGS-84 center of the viewport.
 * @param zoom         Zoom level (8f..19f).
 * @param rotationDeg  Canvas rotation in degrees (0 = north-up).
 * @param route        Optional computed route to draw.
 * @param accentColor  Accent color used for the position dot and route highlight.
 */
@Composable
fun MapCanvas(
    engine: MapEngineService,
    center: LatLon,
    zoom: Float,
    rotationDeg: Float,
    route: ComputedRoute?,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val zoomByte = remember(zoom) { zoom.toInt().toByte() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        withTransform(
            transformBlock = { rotate(degrees = rotationDeg, pivot = center(size)) }
        ) {
            drawTiles(engine, center, zoomByte, canvasW, canvasH)
            route?.let { drawRoute(it, center, zoomByte, canvasW, canvasH, accentColor) }
        }

        // Always draw position dot on top (un-rotated)
        drawCircle(
            color = accentColor,
            radius = 8.dp.toPx(),
            center = center(size),
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = center(size),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

// ── Tile rendering ────────────────────────────────────────────────────────────

private fun DrawScope.drawTiles(
    engine: MapEngineService,
    center: LatLon,
    zoom: Byte,
    canvasW: Float,
    canvasH: Float,
) {
    val scale = TILE_SIZE_PX.toFloat()
    val (centerTileX, centerTileY, offX, offY) = worldToTile(center.lat, center.lon, zoom.toInt())

    val tilesX = (canvasW / scale + 2).toInt()
    val tilesY = (canvasH / scale + 2).toInt()
    val startX = centerTileX - tilesX / 2
    val startY = centerTileY - tilesY / 2

    for (tx in startX..startX + tilesX) {
        for (ty in startY..startY + tilesY) {
            val bitmap: Bitmap = engine.renderTile(tx.toLong(), ty.toLong(), zoom) ?: continue
            val px = (tx - centerTileX) * scale + canvasW / 2 - offX
            val py = (ty - centerTileY) * scale + canvasH / 2 - offY
            drawImage(
                image = bitmap.asImageBitmap(),
                topLeft = Offset(px, py),
            )
        }
    }
}

// ── Route overlay ─────────────────────────────────────────────────────────────

private fun DrawScope.drawRoute(
    route: ComputedRoute,
    center: LatLon,
    zoom: Byte,
    canvasW: Float,
    canvasH: Float,
    accentColor: Color,
) {
    route.segments.forEach { segment ->
        val path = Path()
        segment.points.forEachIndexed { i, pt ->
            val (sx, sy) = latLonToCanvas(pt.lat, pt.lon, center, zoom.toInt(), canvasW, canvasH)
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }

        val strokeWidth = 4.dp.toPx()
        val style = when (segment.type) {
            SegmentType.ROAD -> Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            SegmentType.DASHED_DIRECT -> Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(12.dp.toPx(), 6.dp.toPx()),
                    phase = 0f,
                ),
            )
        }
        drawPath(path = path, color = accentColor, style = style)
    }
}

// ── Coordinate math ──────────────────────────────────────────────────────────

private data class TileCoord(
    val tileX: Int,
    val tileY: Int,
    val offX: Float, // pixel offset within tile
    val offY: Float,
)

private fun worldToTile(lat: Double, lon: Double, zoom: Int): TileCoord {
    val n = 2.0.pow(zoom.toDouble())
    val xTile = ((lon + 180.0) / 360.0 * n)
    val yTile = ((1.0 - ln(tan(lat * PI / 180.0) + 1.0 / cos(lat * PI / 180.0)) / PI) / 2.0 * n)
    val offX = ((xTile - xTile.toInt()) * TILE_SIZE_PX).toFloat()
    val offY = ((yTile - yTile.toInt()) * TILE_SIZE_PX).toFloat()
    return TileCoord(xTile.toInt(), yTile.toInt(), offX, offY)
}

private fun latLonToCanvas(
    lat: Double,
    lon: Double,
    center: LatLon,
    zoom: Int,
    canvasW: Float,
    canvasH: Float,
): Pair<Float, Float> {
    val n = 2.0.pow(zoom.toDouble())
    fun toTileF(la: Double, lo: Double): Pair<Double, Double> {
        val x = (lo + 180.0) / 360.0 * n
        val y = (1.0 - ln(tan(la * PI / 180.0) + 1.0 / cos(la * PI / 180.0)) / PI) / 2.0 * n
        return x to y
    }

    val (cx, cy) = toTileF(center.lat, center.lon)
    val (px, py) = toTileF(lat, lon)
    val scale = TILE_SIZE_PX.toFloat()
    return (
        canvasW / 2 + ((px - cx) * scale).toFloat() to
            canvasH / 2 + ((py - cy) * scale).toFloat()
    )
}

private fun center(size: Size) = Offset(size.width / 2, size.height / 2)
