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
 * Iterative Ramer-Douglas-Peucker simplification.
 * Uses an explicit stack to avoid stack overflow on large tracks.
 */
private fun rdpSimplify(points: List<TrackPoint>, toleranceMeters: Double): List<TrackPoint> {
    if (points.size <= 2) return points

    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true

    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.lastIndex)

    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        if (end - start < 2) continue

        var maxDist = 0.0
        var maxIndex = start
        for (i in start + 1 until end) {
            val dist = perpendicularDistance(points[i], points[start], points[end])
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }

        if (maxDist > toleranceMeters) {
            keep[maxIndex] = true
            stack.addLast(start to maxIndex)
            stack.addLast(maxIndex to end)
        }
    }

    return points.filterIndexed { index, _ -> keep[index] }
}

/**
 * Simplify a list of [TrackPoint]s using the Ramer-Douglas-Peucker algorithm.
 * Points that deviate less than [toleranceMeters] from a straight line are removed.
 * If the result still exceeds [maxPoints], the tolerance is progressively doubled
 * until the point count is within budget (with a uniform-subsample fallback).
 */
fun simplifyTrack(
    points: List<TrackPoint>,
    toleranceMeters: Double = 10.0,
    maxPoints: Int = 2000,
): List<TrackPoint> {
    if (points.size <= 2) return points

    var result = rdpSimplify(points, toleranceMeters)

    // Progressively increase tolerance until under the cap
    var tol = toleranceMeters * 2
    while (result.size > maxPoints && tol < 10_000.0) {
        result = rdpSimplify(result, tol)
        tol *= 2
    }

    // Final fallback: uniform subsample keeping first and last
    if (result.size > maxPoints) {
        val step = (result.size - 1).toDouble() / (maxPoints - 1)
        result = (0 until maxPoints).map { result[(it * step).toInt() .coerceAtMost(result.lastIndex)] }
    }

    return result
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
