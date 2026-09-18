package com.rork.gpssimulator.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.SavedKind
import com.rork.gpssimulator.data.model.SavedLocation
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.data.model.SpeedProfile
import kotlinx.serialization.json.Json
import java.util.UUID

/** Outcome of an import attempt, so the UI can report precise feedback. */
sealed interface ImportResult {
    data class Locations(val items: List<SavedLocation>) : ImportResult
    data class Routes(val items: List<SimRoute>) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/**
 * Reads and writes backup files for saved locations (JSON/CSV) and routes (GPX).
 * Every import is validated before the data reaches the repository.
 */
class TransferService(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---------- Export ----------

    fun locationsToJson(locations: List<SavedLocation>): String = json.encodeToString(locations)

    fun locationsToCsv(locations: List<SavedLocation>): String = buildString {
        appendLine("name,latitude,longitude,address,kind,icon,notes")
        locations.forEach { location ->
            appendLine(
                listOf(
                    location.name,
                    location.point.lat.toString(),
                    location.point.lng.toString(),
                    location.address,
                    location.kind.name,
                    location.iconKey,
                    location.notes,
                ).joinToString(",") { escapeCsv(it) },
            )
        }
    }

    fun routesToGpx(routes: List<SimRoute>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="GPS Simulator" xmlns="http://www.topografix.com/GPX/1/1">""",
        )
        routes.forEach { route ->
            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(route.name)}</name>")
            appendLine("    <trkseg>")
            route.points.forEach { point ->
                appendLine("""      <trkpt lat="${point.lat}" lon="${point.lng}"></trkpt>""")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
        }
        appendLine("</gpx>")
    }

    /** Writes [content] to the user-chosen document URI. */
    fun write(uri: Uri, content: String): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray())
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Export failed: ${e.javaClass.simpleName}")
        false
    }

    // ---------- Import ----------

    fun importLocations(uri: Uri): ImportResult {
        val text = read(uri) ?: return ImportResult.Failure("File could not be read")
        return when {
            text.trimStart().startsWith("[") -> parseLocationsJson(text)
            text.contains(",") -> parseLocationsCsv(text)
            else -> ImportResult.Failure("Unsupported file format")
        }
    }

    fun importGpx(uri: Uri): ImportResult {
        val text = read(uri) ?: return ImportResult.Failure("File could not be read")
        if (!text.contains("<gpx", ignoreCase = true)) {
            return ImportResult.Failure("Not a valid GPX file")
        }

        val nameRegex = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
        val pointRegex = Regex(
            """<(?:trkpt|rtept|wpt)\s+[^>]*?lat="(-?[\d.]+)"[^>]*?lon="(-?[\d.]+)"""",
            RegexOption.IGNORE_CASE,
        )

        val points = pointRegex.findAll(text).mapNotNull { match ->
            val lat = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val lng = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            LatLng(lat, lng).takeIf { it.isValid() }
        }.toList()

        if (points.size < 2) {
            return ImportResult.Failure("GPX file needs at least two points")
        }

        val name = nameRegex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: "Imported route"

        return ImportResult.Routes(
            listOf(
                SimRoute(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    points = points,
                    profile = SpeedProfile.WALKING,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
    }

    private fun parseLocationsJson(text: String): ImportResult = try {
        val parsed = json.decodeFromString<List<SavedLocation>>(text)
        val valid = parsed.filter { it.point.isValid() && it.name.isNotBlank() }
            .map { it.copy(id = UUID.randomUUID().toString()) }
        if (valid.isEmpty()) {
            ImportResult.Failure("No valid locations found")
        } else {
            ImportResult.Locations(valid)
        }
    } catch (e: Exception) {
        Log.w(TAG, "JSON import failed: ${e.javaClass.simpleName}")
        ImportResult.Failure("File could not be read")
    }

    private fun parseLocationsCsv(text: String): ImportResult {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return ImportResult.Failure("No valid locations found")

        val items = lines.drop(1).mapNotNull { line ->
            val cells = splitCsv(line)
            if (cells.size < 3) return@mapNotNull null
            val lat = cells[1].toDoubleOrNull() ?: return@mapNotNull null
            val lng = cells[2].toDoubleOrNull() ?: return@mapNotNull null
            val point = LatLng(lat, lng)
            if (!point.isValid()) return@mapNotNull null

            SavedLocation(
                id = UUID.randomUUID().toString(),
                name = cells[0].ifBlank { "Imported location" },
                address = cells.getOrElse(3) { "" },
                point = point,
                kind = runCatching { SavedKind.valueOf(cells.getOrElse(4) { "FAVORITE" }) }
                    .getOrDefault(SavedKind.FAVORITE),
                iconKey = cells.getOrElse(5) { "pin" }.ifBlank { "pin" },
                notes = cells.getOrElse(6) { "" },
                createdAt = System.currentTimeMillis(),
            )
        }

        return if (items.isEmpty()) {
            ImportResult.Failure("No valid locations found")
        } else {
            ImportResult.Locations(items)
        }
    }

    private fun read(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Import read failed: ${e.javaClass.simpleName}")
        null
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    cells.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells.add(current.toString())
        return cells.map { it.trim() }
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val TAG = "TransferService"
    }
}
