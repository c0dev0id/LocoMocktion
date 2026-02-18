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
 * Simplify a list of [TrackPoint]s using the Ramer-Douglas-Peucker algorithm.
 * Points that deviate less than [toleranceMeters] from a straight line are removed.
 */
fun simplifyTrack(points: List<TrackPoint>, toleranceMeters: Double = 5.0): List<TrackPoint> {
    if (points.size <= 2) return points

    var maxDist = 0.0
    var maxIndex = 0
    for (i in 1 until points.lastIndex) {
        val dist = perpendicularDistance(points[i], points.first(), points.last())
        if (dist > maxDist) {
            maxDist = dist
            maxIndex = i
        }
    }

    return if (maxDist > toleranceMeters) {
        val left = simplifyTrack(points.subList(0, maxIndex + 1), toleranceMeters)
        val right = simplifyTrack(points.subList(maxIndex, points.size), toleranceMeters)
        left.dropLast(1) + right
    } else {
        listOf(points.first(), points.last())
    }
}

/**
 * Parses a GPX file and extracts track points from all track segments.
 */
fun parseGpx(input: InputStream): GpxTrack {
    val factory = XmlPullParserFactory.newInstance()
    factory.isNamespaceAware = false
    val parser = factory.newPullParser()
    parser.setInput(input, null)

    var trackName: String? = null
    val points = mutableListOf<TrackPoint>()

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
                    "trk" -> inTrk = true
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
                    trackName = parser.text?.trim()
                }
                if (inEle) {
                    ele = parser.text?.trim()?.toDoubleOrNull()
                }
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "trk" -> inTrk = false
                    "name" -> inTrkName = false
                    "trkpt" -> {
                        if (lat != null && lon != null) {
                            points.add(TrackPoint(lat!!, lon!!, ele))
                        }
                        inTrkpt = false
                    }
                    "ele" -> inEle = false
                }
            }
        }
        event = parser.next()
    }

    return GpxTrack(name = trackName, points = points)
}
