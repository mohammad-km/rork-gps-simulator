package com.rork.gpssimulator.ui.map

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.util.Log
import com.rork.gpssimulator.data.model.MapType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Raster tile endpoints used for each map type. */
enum class TileSource(val id: String, val maxZoom: Int, val attribution: String) {
    STANDARD("osm", 19, "© OpenStreetMap"),
    SATELLITE("esri", 18, "© Esri, Maxar"),
    TERRAIN("topo", 16, "© OpenTopoMap (CC-BY-SA)");

    fun url(z: Int, x: Int, y: Int): String = when (this) {
        STANDARD -> "https://tile.openstreetmap.org/$z/$x/$y.png"
        SATELLITE ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        TERRAIN -> "https://tile.opentopomap.org/$z/$x/$y.png"
    }

    companion object {
        fun of(type: MapType): TileSource = when (type) {
            MapType.STANDARD -> STANDARD
            MapType.SATELLITE -> SATELLITE
            MapType.TERRAIN -> TERRAIN
        }
    }
}

/**
 * Downloads and caches map tiles in memory. Requests are de-duplicated and
 * throttled; [version] increments whenever new pixels arrive so the map redraws.
 */
class TileStore(private val scope: CoroutineScope) {

    private val cache = object : LruCache<String, ImageBitmap>(360) {}
    private val inFlight = mutableSetOf<String>()
    private val failed = mutableMapOf<String, Int>()
    private val semaphore = Semaphore(6)

    var version by mutableIntStateOf(0)
        private set

    /** Returns a cached tile, scheduling a download when it is missing. */
    fun tile(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "${source.id}/$z/$x/$y"
        cache.get(key)?.let { return it }
        if (failed.getOrDefault(key, 0) >= MAX_RETRIES) return null
        if (!inFlight.add(key)) return null

        scope.launch {
            val bitmap = semaphore.withPermit { download(source.url(z, x, y)) }
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    cache.put(key, bitmap)
                    failed.remove(key)
                    version++
                } else {
                    failed[key] = failed.getOrDefault(key, 0) + 1
                }
                inFlight.remove(key)
            }
        }
        return null
    }

    /** Looks up a tile without triggering a download (used for low-res fallback). */
    fun cached(source: TileSource, z: Int, x: Int, y: Int): ImageBitmap? =
        cache.get("${source.id}/$z/$x/$y")

    private suspend fun download(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/png,image/jpeg,image/*")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            val bytes = connection.inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            Log.d(TAG, "Tile download failed: ${e.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "TileStore"
        private const val MAX_RETRIES = 3
        private const val USER_AGENT = "GPSSimulator/1.0 (Android developer testing tool)"
    }
}
