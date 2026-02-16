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
