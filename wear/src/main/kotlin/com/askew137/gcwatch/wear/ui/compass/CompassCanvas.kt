package com.askew137.gcwatch.wear.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a compass needle pointing toward the target bearing relative to current azimuth.
 *
 * @param azimuthDeg      Current device heading (degrees, 0 = north).
 * @param targetBearingDeg Absolute bearing from current position to target.
 * @param showCardinals   Whether to draw N/S/E/W labels.
 * @param accentColor     Accent color used for the arrow fill.
 */
@Composable
fun CompassCanvas(
    azimuthDeg: Float,
    targetBearingDeg: Float,
    showCardinals: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // ── Background circle ────────────────────────────────────────────────
        drawCircle(color = Color.Black, radius = radius, center = Offset(cx, cy))
        drawCircle(
            color = accentColor.copy(alpha = 0.15f),
            radius = radius - 4.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )

        // ── Cardinal labels ──────────────────────────────────────────────────
        if (showCardinals) {
            val cardinals = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
            val labelRadius = radius - 16.dp.toPx()
            cardinals.forEach { (label, angleDeg) ->
                val rotated = (angleDeg - azimuthDeg + 360f) % 360f
                val rad = Math.toRadians(rotated.toDouble() - 90.0)
                val lx = cx + (labelRadius * cos(rad)).toFloat()
                val ly = cy + (labelRadius * sin(rad)).toFloat()

                val measured = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        color = if (label == "N") accentColor else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(lx - measured.size.width / 2f, ly - measured.size.height / 2f),
                )
            }
        }

        // ── Arrow pointing to target ──────────────────────────────────────────
        val arrowAngle = targetBearingDeg - azimuthDeg
        rotate(degrees = arrowAngle, pivot = Offset(cx, cy)) {
            drawArrow(
                cx = cx,
                cy = cy,
                radius = radius * 0.65f,
                accentColor = accentColor,
            )
        }

        // ── Center dot ───────────────────────────────────────────────────────
        drawCircle(color = accentColor, radius = 5.dp.toPx(), center = Offset(cx, cy))
    }
}

private fun DrawScope.drawArrow(
    cx: Float,
    cy: Float,
    radius: Float,
    accentColor: Color,
) {
    val tipY = cy - radius
    val baseHalfWidth = radius * 0.22f
    val baseY = cy + radius * 0.35f

    val arrowPath = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx + baseHalfWidth, baseY)
        lineTo(cx - baseHalfWidth, baseY)
        close()
    }

    // Fill
    drawPath(path = arrowPath, color = accentColor)

    // 2dp white stroke
    drawPath(
        path = arrowPath,
        color = Color.White,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
    )

    // Tail triangle (opposite direction, grey)
    val tailPath = Path().apply {
        moveTo(cx, cy + radius * 0.5f)
        lineTo(cx + baseHalfWidth * 0.7f, cy)
        lineTo(cx - baseHalfWidth * 0.7f, cy)
        close()
    }
    drawPath(path = tailPath, color = Color.DarkGray)
}
