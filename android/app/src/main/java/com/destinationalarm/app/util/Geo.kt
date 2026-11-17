package com.destinationalarm.app.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p = PI / 180.0
        val dLat = (lat2 - lat1) * p
        val dLon = (lon2 - lon1) * p
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * p) * cos(lat2 * p) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a).coerceAtMost(1.0))
    }

    fun fmtDistance(meter: Double): String {
        if (meter.isNaN() || meter < 0) return "—"
        return if (meter < 1000) "${meter.roundToInt()} m"
        else String.format(Locale.US, "%.2f km", meter / 1000.0)
    }

    fun fmtShortDistance(meter: Double): String {
        return if (meter < 1000) "${meter.roundToInt()} m"
        else String.format(Locale.US, "%.1f km", meter / 1000.0)
    }

    fun fmtDuration(sec: Int?): String {
        if (sec == null || sec < 0) return "—"
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    /** Polygon GeoJSON FeatureCollection approximating a circle of [radiusM] around the point. */
    fun circleGeoJson(lat: Double, lon: Double, radiusM: Double, steps: Int = 72): String {
        val earth = 6378137.0
        val latRad = lat * PI / 180.0
        val ring = JSONArray()
        for (i in 0..steps) {
            val th = i * 2 * PI / steps
            val dy = radiusM * sin(th)
            val dx = radiusM * cos(th)
            val la = lat + (dy / earth) * (180.0 / PI)
            val lo = lon + (dx / (earth * cos(latRad))) * (180.0 / PI)
            ring.put(JSONArray().put(lo).put(la))
        }
        val geometry = JSONObject()
            .put("type", "Polygon")
            .put("coordinates", JSONArray().put(ring))
        return featureCollection(feature(geometry))
    }

    /** LineString GeoJSON from (lon, lat) pairs. */
    fun lineGeoJson(points: List<Pair<Double, Double>>): String {
        if (points.size < 2) return emptyCollection()
        val coords = JSONArray()
        for ((lon, lat) in points) coords.put(JSONArray().put(lon).put(lat))
        val geometry = JSONObject()
            .put("type", "LineString")
            .put("coordinates", coords)
        return featureCollection(feature(geometry))
    }

    fun emptyCollection(): String {
        return JSONObject().put("type", "FeatureCollection").put("features", JSONArray()).toString()
    }

    /** Point GeoJSON FeatureCollection. */
    fun pointGeoJson(lat: Double, lon: Double): String {
        val geometry = JSONObject()
            .put("type", "Point")
            .put("coordinates", JSONArray().put(lon).put(lat))
        return featureCollection(feature(geometry))
    }

    private fun feature(geometry: JSONObject): JSONObject =
        JSONObject()
            .put("type", "Feature")
            .put("properties", JSONObject())
            .put("geometry", geometry)

    private fun featureCollection(feature: JSONObject): String =
        JSONObject()
            .put("type", "FeatureCollection")
            .put("features", JSONArray().put(feature))
            .toString()

    /** Distance between a fix and a destination, in metres, keeping only sane values. */
    fun safeDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val d = distanceM(lat1, lon1, lat2, lon2)
        return if (d.isNaN() || d.isInfinite() || abs(d) > 20_000_000) -1.0 else d
    }
}
