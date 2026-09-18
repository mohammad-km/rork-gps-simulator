package com.rork.gpssimulator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Precise targeting reticle pinned to the exact centre of the map.
 * It grows slightly while the user is panning so the aim point stays obvious.
 */
@Composable
fun Crosshair(
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.14f else 1f,
        animationSpec = tween(220),
        label = "crosshairScale",
    )

    Canvas(modifier = modifier.size(96.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension / 2f
        val ringRadius = unit * 0.34f * scale
        val armOuter = unit * 0.92f * scale
        val armInner = unit * 0.46f * scale
        val halo = Color.White.copy(alpha = 0.85f)

        fun arm(dx: Float, dy: Float) {
            val start = Offset(center.x + dx * armInner, center.y + dy * armInner)
            val end = Offset(center.x + dx * armOuter, center.y + dy * armOuter)
            // White halo underneath keeps the reticle readable on any map tile.
            drawLine(halo, start, end, strokeWidth = 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, start, end, strokeWidth = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }

        arm(1f, 0f)
        arm(-1f, 0f)
        arm(0f, 1f)
        arm(0f, -1f)

        drawCircle(halo, radius = ringRadius, center = center, style = Stroke(width = 7f))
        drawCircle(color, radius = ringRadius, center = center, style = Stroke(width = 3.5f))

        drawCircle(halo, radius = 4.6f, center = center)
        drawCircle(color, radius = 2.6f, center = center)
    }
}
