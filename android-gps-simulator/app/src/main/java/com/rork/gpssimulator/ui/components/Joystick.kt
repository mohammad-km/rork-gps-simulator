package com.rork.gpssimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.ui.theme.LocalAppColors
import kotlin.math.hypot

/**
 * 360° floating joystick for manual movement simulation.
 * Emits a normalised vector where (0,0) means "stop".
 */
@Composable
fun JoystickControl(
    speedLabel: String,
    isPaused: Boolean,
    onVector: (Float, Float) -> Unit,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val baseRadiusDp = 62.dp
    val baseRadiusPx = with(density) { baseRadiusDp.toPx() }

    var knob by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                RoundedCornerShape(26.dp),
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = speedLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.padding(start = 6.dp),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onTogglePause, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = {
                    knob = Offset.Zero
                    onVector(0f, 0f)
                    onClose()
                },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = appColors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(baseRadiusDp * 2)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            knob = clampToRadius(start - center, baseRadiusPx)
                            emit(knob, baseRadiusPx, onVector)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            knob = clampToRadius(knob + amount, baseRadiusPx)
                            emit(knob, baseRadiusPx, onVector)
                        },
                        onDragEnd = {
                            knob = Offset.Zero
                            onVector(0f, 0f)
                        },
                        onDragCancel = {
                            knob = Offset.Zero
                            onVector(0f, 0f)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(baseRadiusDp * 2)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = accent.copy(alpha = 0.10f),
                    radius = baseRadiusPx,
                    center = center,
                )
                drawCircle(
                    color = accent.copy(alpha = 0.30f),
                    radius = baseRadiusPx,
                    center = center,
                    style = Stroke(width = 3f),
                )
                // Cross guides
                drawLine(
                    color = accent.copy(alpha = 0.18f),
                    start = Offset(center.x - baseRadiusPx * 0.55f, center.y),
                    end = Offset(center.x + baseRadiusPx * 0.55f, center.y),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = accent.copy(alpha = 0.18f),
                    start = Offset(center.x, center.y - baseRadiusPx * 0.55f),
                    end = Offset(center.x, center.y + baseRadiusPx * 0.55f),
                    strokeWidth = 2f,
                )
                val knobCenter = center + knob
                drawCircle(color = Color.White, radius = 25f, center = knobCenter)
                drawCircle(color = accent, radius = 21f, center = knobCenter)
            }
        }
    }
}

private fun clampToRadius(offset: Offset, radius: Float): Offset {
    val distance = hypot(offset.x, offset.y)
    if (distance <= radius || distance == 0f) return offset
    val scale = radius / distance
    return Offset(offset.x * scale, offset.y * scale)
}

private fun emit(knob: Offset, radius: Float, onVector: (Float, Float) -> Unit) {
    if (radius <= 0f) return
    onVector((knob.x / radius).coerceIn(-1f, 1f), (knob.y / radius).coerceIn(-1f, 1f))
}
