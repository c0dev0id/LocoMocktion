package de.codevoid.locomocktion.gpx

import android.location.Location
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Instant
import kotlin.math.*

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val speed: Double? = null, // m/s, from <speed>, Garmin extension, or derived from <time>
)

data class GpxTrack(
    val name: String?,
    val points: List<TrackPoint>,
) {
    val totalDistanceMeters: Double by lazy {
        points.zipWithNext { a, b -> distanceBetween(a, b) }.sum()
    }
    val hasSpeedData: Boolean by lazy {
        points.any { it.speed != null }
    }
}

fun distanceBetween(a: TrackPoint, b: TrackPoint): Double {
    val results = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
    return results[0].toDouble()
}

/**
 * Perpendicular distance from point [p] to the line segment [start]-[end], in meters.
 */
private fun perpendicularDistance(p: TrackPoint, start: TrackPoint, end: TrackPoint): Double {
    if (start.latitude == end.latitude && start.longitude == end.longitude) {
        return distanceBetween(p, start)
    }
    val totalDist = distanceBetween(start, end)
    if (totalDist < 0.1) return distanceBetween(p, start)

    // Project p onto the line using geographic distances
    val dStartP = distanceBetween(start, p)
    val dEndP = distanceBetween(end, p)

    // Use Heron's formula to get the area, then derive height
    val s = (totalDist + dStartP + dEndP) / 2.0
    val area = sqrt((s * (s - totalDist) * (s - dStartP) * (s - dEndP)).coerceAtLeast(0.0))
    return 2.0 * area / totalDist
}

/** Intermediate representation that also holds the raw timestamp for speed derivation. */
private data class RawPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val explicitSpeed: Double?,   // from <speed> or extension
    val timeEpochMs: Long?,       // from <time>
)

/**
 * Converts a list of [RawPoint]s into [TrackPoint]s.
 *
 * If a point has an explicit speed tag that is used as-is.  Otherwise, when
 * consecutive points carry timestamps, the speed for each point is derived
 * from the distance to the next point divided by the elapsed time:
 *   speed[i] = distance(i, i+1) / (time[i+1] - time[i])
 * The last point reuses the speed of the preceding segment.
 */
private fun rawToTrackPoints(raw: List<RawPoint>): List<TrackPoint> {
    if (raw.isEmpty()) return emptyList()

    // Pre-compute segment speeds from timestamps (m/s) for points without explicit speed.
    // segSpeed[i] is the speed from point i to point i+1.
    val segSpeed = Array<Double?>(raw.size) { null }
    for (i in 0 until raw.lastIndex) {
        val a = raw[i]
        val b = raw[i + 1]
        if (a.timeEpochMs != null && b.timeEpochMs != null) {
            val dtMs = b.timeEpochMs - a.timeEpochMs
            if (dtMs > 0) {
                val dist = run {
                    val results = FloatArray(1)
                    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
                    results[0].toDouble()
                }
                segSpeed[i] = dist / (dtMs / 1000.0)
            }
        }
    }
    // Last point inherits the previous segment speed.
    if (raw.size >= 2) segSpeed[raw.lastIndex] = segSpeed[raw.lastIndex - 1]

    return raw.mapIndexed { i, p ->
        val speed = p.explicitSpeed ?: segSpeed[i]
        TrackPoint(p.latitude, p.longitude, p.elevation, speed)
    }
}

/** Parses an ISO-8601 timestamp string (e.g. "2025-07-12T19:02:10.000Z") to epoch ms. */
private fun parseTimestamp(text: String): Long? = try {
    Instant.parse(text).toEpochMilli()
} catch (_: Exception) {
    null
}

/**
 * Parses a GPX file and extracts tracks. Each `<trk>` element becomes a
 * separate [GpxTrack] so the caller can let the user choose when a file
 * contains more than one track.
 */
fun parseGpx(input: InputStream): List<GpxTrack> {
    val factory = XmlPullParserFactory.newInstance()
    factory.isNamespaceAware = false
    val parser = factory.newPullParser()
    parser.setInput(input, null)

    val tracks = mutableListOf<GpxTrack>()
    var currentTrackName: String? = null
    var currentRaw = mutableListOf<RawPoint>()

    var inTrk = false
    var inTrkName = false
    var inTrkpt = false
    var inEle = false
    var inSpeed = false
    var inTime = false

    var lat: Double? = null
    var lon: Double? = null
    var ele: Double? = null
    var speed: Double? = null
    var timeEpochMs: Long? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                // Strip any namespace prefix for tag matching (e.g. "gpxtpx:speed" -> "speed")
                val localName = parser.name.substringAfterLast(':')
                when {
                    parser.name == "trk" -> {
                        inTrk = true
                        currentTrackName = null
                        currentRaw = mutableListOf()
                    }
                    parser.name == "name" -> if (inTrk && !inTrkpt) inTrkName = true
                    parser.name == "trkpt" -> {
                        inTrkpt = true
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        ele = null
                        speed = null
                        timeEpochMs = null
                    }
                    localName == "ele" && inTrkpt -> inEle = true
                    localName == "speed" && inTrkpt -> inSpeed = true
                    localName == "time" && inTrkpt -> inTime = true
                }
            }
            XmlPullParser.TEXT -> {
                if (inTrkName) currentTrackName = parser.text?.trim()
                if (inEle) ele = parser.text?.trim()?.toDoubleOrNull()
                if (inSpeed) speed = parser.text?.trim()?.toDoubleOrNull()
                if (inTime) timeEpochMs = parser.text?.trim()?.let { parseTimestamp(it) }
            }
            XmlPullParser.END_TAG -> {
                val localName = parser.name.substringAfterLast(':')
                when {
                    parser.name == "trk" -> {
                        if (currentRaw.isNotEmpty()) {
                            tracks.add(GpxTrack(name = currentTrackName, points = rawToTrackPoints(currentRaw)))
                        }
                        inTrk = false
                    }
                    parser.name == "name" -> inTrkName = false
                    parser.name == "trkpt" -> {
                        if (lat != null && lon != null) {
                            currentRaw.add(RawPoint(lat!!, lon!!, ele, speed, timeEpochMs))
                        }
                        inTrkpt = false
                    }
                    localName == "ele" -> inEle = false
                    localName == "speed" -> inSpeed = false
                    localName == "time" -> inTime = false
                }
            }
        }
        event = parser.next()
    }

    // Handle malformed GPX where <trk> was never closed
    if (tracks.isEmpty() && currentRaw.isNotEmpty()) {
        tracks.add(GpxTrack(name = currentTrackName, points = rawToTrackPoints(currentRaw)))
    }

    return tracks
}
