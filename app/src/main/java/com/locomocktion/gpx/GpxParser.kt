package com.locomocktion.gpx

import android.location.Location
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import kotlin.math.*

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
)

data class GpxTrack(
    val name: String?,
    val points: List<TrackPoint>,
) {
    val totalDistanceMeters: Double by lazy {
        points.zipWithNext { a, b -> distanceBetween(a, b) }.sum()
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
    var currentPoints = mutableListOf<TrackPoint>()

    var inTrk = false
    var inTrkName = false
    var inTrkpt = false
    var inEle = false

    var lat: Double? = null
    var lon: Double? = null
    var ele: Double? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "trk" -> {
                        inTrk = true
                        currentTrackName = null
                        currentPoints = mutableListOf()
                    }
                    "name" -> if (inTrk && !inTrkpt) inTrkName = true
                    "trkpt" -> {
                        inTrkpt = true
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        ele = null
                    }
                    "ele" -> if (inTrkpt) inEle = true
                }
            }
            XmlPullParser.TEXT -> {
                if (inTrkName) {
                    currentTrackName = parser.text?.trim()
                }
                if (inEle) {
                    ele = parser.text?.trim()?.toDoubleOrNull()
                }
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "trk" -> {
                        if (currentPoints.isNotEmpty()) {
                            tracks.add(GpxTrack(name = currentTrackName, points = currentPoints.toList()))
                        }
                        inTrk = false
                    }
                    "name" -> inTrkName = false
                    "trkpt" -> {
                        if (lat != null && lon != null) {
                            currentPoints.add(TrackPoint(lat!!, lon!!, ele))
                        }
                        inTrkpt = false
                    }
                    "ele" -> inEle = false
                }
            }
        }
        event = parser.next()
    }

    // Handle malformed GPX where <trk> was never closed
    if (tracks.isEmpty() && currentPoints.isNotEmpty()) {
        tracks.add(GpxTrack(name = currentTrackName, points = currentPoints.toList()))
    }

    return tracks
}
