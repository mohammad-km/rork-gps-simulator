package com.rork.gpssimulator.ui.map

import androidx.compose.ui.geometry.Offset
import com.rork.gpssimulator.data.model.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

const val TILE_SIZE = 256.0
const val MIN_ZOOM = 2f
const val MAX_ZOOM = 19f
private const val MAX_LATITUDE = 85.05112878

/**
 * Web-Mercator projection between screen pixels and coordinates for a given camera.
 *
 * [effectiveZoom] already includes the display-density compensation so that one
 * logical zoom level covers the same geographic area on every screen.
 */
class MapProjector(
    val center: LatLng,
    val effectiveZoom: Double,
    val widthPx: Float,
    val heightPx: Float,
) {
    val worldSize: Double = TILE_SIZE * 2.0.pow(effectiveZoom)

    private val centerWorld: Offset = worldOf(center)

    private fun worldOf(point: LatLng): Offset {
        val lat = point.lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val x = (point.lng + 180.0) / 360.0 * worldSize
        val latRad = Math.toRadians(lat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * worldSize
        return Offset(x.toFloat(), y.toFloat())
    }

    private fun latLngOfWorld(x: Double, y: Double): LatLng {
        val lng = x / worldSize * 360.0 - 180.0
        val n = PI * (1.0 - 2.0 * y / worldSize)
        val lat = Math.toDegrees(atan(sinh(n)))
        return LatLng(lat, ((lng + 540.0) % 360.0) - 180.0)
    }

    /** World-pixel origin of the top-left screen corner. */
    val originX: Double = centerWorld.x - widthPx / 2.0
    val originY: Double = centerWorld.y - heightPx / 2.0

    fun toScreen(point: LatLng): Offset {
        val world = worldOf(point)
        var x = world.x - originX
        // Choose the wrapped copy of the world closest to the viewport.
        if (x < -worldSize / 2) x += worldSize.toFloat()
        if (x > worldSize / 2) x -= worldSize.toFloat()
        return Offset(x.toFloat(), (world.y - originY).toFloat())
    }

    fun toLatLng(screen: Offset): LatLng =
        latLngOfWorld(originX + screen.x, (originY + screen.y).coerceIn(0.0, worldSize))

    /** Meters represented by one screen pixel at the camera center. */
    fun metersPerPixel(): Double {
        val latRad = Math.toRadians(center.lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        return 156543.03392 * cos(latRad) / 2.0.pow(effectiveZoom)
    }
}

fun clampLatitude(lat: Double): Double = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
