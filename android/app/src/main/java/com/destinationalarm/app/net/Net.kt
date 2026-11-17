package com.destinationalarm.app.net

import com.destinationalarm.app.model.Fix
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Free map services, no API key:
 *  - Photon (Komoot) for fast global place search
 *  - Nominatim (OpenStreetMap) as a fallback for addresses Photon misses
 *  - OSRM for road routing / ETA
 *  - Nominatim for reverse geocoding the map pin
 */
object Net {

    private const val UA = "DestinationAlarm/1.0 (Android)"

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class Place(
        val name: String,
        val sub: String,
        val lat: Double,
        val lon: Double,
        val icon: String
    )

    data class Route(
        val durationSec: Int,
        val distanceM: Int,
        val points: List<Pair<Double, Double>>
    )

    /**
     * Global search. Nominatim (OSM addresses, great for Indian localities) and
     * Photon (fast autocomplete, strong for POIs/stations) are merged so results
     * are not limited to one index.
     */
    fun search(query: String, near: Fix?): List<Place> {
        val q = query.trim()
        if (q.length < 3) return emptyList()

        val nominatim = searchNominatim(q, near)
        val photon = searchPhoton(q, near)

        val seen = HashSet<String>()
        val out = ArrayList<Place>(12)
        for (place in nominatim + photon) {
            val k = key(place.lat, place.lon)
            if (!seen.add(k)) continue
            val duplicateName = out.any {
                it.name.equals(place.name, ignoreCase = true) && it.sub == place.sub
            }
            if (duplicateName) continue
            out.add(place)
        }
        return out.take(10)
    }

    private fun key(lat: Double, lon: Double): String =
        "${(lat * 10000).roundToInt()}:${(lon * 10000).roundToInt()}"

    private val nominatimCache = HashMap<String, List<Place>>()
    private var lastNominatimCall = 0L

    /** Nominatim asks for max 1 request/second, so calls are throttled + cached. */
    @Synchronized
    private fun searchNominatim(q: String, near: Fix?): List<Place> {
        nominatimCache[q]?.let { return it }

        val now = System.currentTimeMillis()
        if (now - lastNominatimCall < 1150L) return emptyList()
        lastNominatimCall = now

        val bias = if (near != null) {
            "&viewbox=${near.lon - 1.5},${near.lat + 1.5},${near.lon + 1.5},${near.lat - 1.5}&bounded=0"
        } else {
            ""
        }

        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2" +
            "&addressdetails=1&limit=6&accept-language=en&q=" +
            URLEncoder.encode(q, "UTF-8") + bias

        val body = get(url) ?: return emptyList()
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()

        val out = ArrayList<Place>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val lat = o.optString("lat").toDoubleOrNull() ?: continue
            val lon = o.optString("lon").toDoubleOrNull() ?: continue
            val address = o.optJSONObject("address") ?: JSONObject()

            val display = o.optString("display_name")
            val name = o.optString("name").ifBlank {
                display.split(",").firstOrNull()?.trim().orEmpty()
            }.ifBlank { "Unnamed place" }

            val parts = listOf(
                address.optString("suburb"),
                address.optString("city_district"),
                address.optString("city"),
                address.optString("state"),
                address.optString("country")
            ).filter { it.isNotBlank() && it != name }.distinct().take(3)

            out.add(
                Place(
                    name = name,
                    sub = parts.joinToString(", "),
                    lat = lat,
                    lon = lon,
                    icon = iconFor(o.optString("type"), name)
                )
            )
        }

        if (nominatimCache.size > 40) nominatimCache.clear()
        nominatimCache[q] = out
        return out
    }

    private fun searchPhoton(q: String, near: Fix?): List<Place> {
        val url = buildString {
            append("https://photon.komoot.io/api/?limit=8&lang=en&q=")
            append(URLEncoder.encode(q, "UTF-8"))
            if (near != null) append("&lat=${near.lat}&lon=${near.lon}&location_bias_scale=0.7")
        }

        val body = get(url) ?: return emptyList()
        val features = runCatching { JSONObject(body).optJSONArray("features") }.getOrNull()
            ?: return emptyList()

        val out = ArrayList<Place>(features.length())
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val g = f.optJSONObject("geometry") ?: continue
            val c = g.optJSONArray("coordinates") ?: continue
            if (c.length() < 2) continue

            val lon = c.optDouble(0)
            val lat = c.optDouble(1)
            if (lat.isNaN() || lon.isNaN()) continue

            val p = f.optJSONObject("properties") ?: JSONObject()
            val name = p.optString("name").ifBlank { p.optString("street") }
                .ifBlank { p.optString("city") }.ifBlank { "Unnamed place" }

            val parts = listOf(
                p.optString("district"),
                p.optString("city"),
                p.optString("state"),
                p.optString("country")
            ).filter { it.isNotBlank() && it != name }.distinct().take(3)

            out.add(
                Place(
                    name = name,
                    sub = parts.joinToString(", "),
                    lat = lat,
                    lon = lon,
                    icon = iconFor(p.optString("osm_value"), name)
                )
            )
        }
        return out
    }

    fun route(mode: String, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route? {
        if (mode == "TRANSIT") return null // no free transit routing; caller uses an estimate

        val base = if (mode == "WALK")
            "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"
        else
            "https://router.project-osrm.org/route/v1/driving/"

        val url = "$base$fromLon,$fromLat;$toLon,$toLat" +
            "?overview=full&geometries=geojson&alternatives=false&steps=false"

        val body = get(url) ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optString("code") != "Ok") return null

        val routes = json.optJSONArray("routes") ?: return null
        val r = routes.optJSONObject(0) ?: return null
        val duration = r.optDouble("duration", -1.0)
        val distance = r.optDouble("distance", 0.0)
        if (duration < 0) return null

        val points = ArrayList<Pair<Double, Double>>()
        val coords = r.optJSONObject("geometry")?.optJSONArray("coordinates")
        if (coords != null) {
            for (i in 0 until coords.length()) {
                val c = coords.optJSONArray(i) ?: continue
                points.add(c.optDouble(0) to c.optDouble(1))
            }
        }

        return Route(
            durationSec = duration.roundToInt(),
            distanceM = distance.roundToInt(),
            points = points
        )
    }

    fun reverseName(lat: Double, lon: Double): String? {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18" +
            "&accept-language=en&lat=$lat&lon=$lon"
        val body = get(url) ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val address = json.optJSONObject("address") ?: JSONObject()

        return listOf(
            json.optString("name"),
            address.optString("railway"),
            address.optString("bus_stop"),
            address.optString("amenity"),
            address.optString("road"),
            address.optString("neighbourhood"),
            address.optString("suburb"),
            address.optString("city"),
            json.optString("display_name")
        ).firstOrNull { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()
    }

    private fun get(url: String): String? {
        return runCatching {
            val req = Request.Builder().url(url).header("User-Agent", UA).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    private fun iconFor(osmValue: String, name: String): String {
        val v = osmValue.lowercase()
        return when {
            v.contains("station") || v.contains("subway") || v.contains("railway") -> "🚇"
            v.contains("bus") -> "🚌"
            v.contains("airport") -> "✈️"
            v.contains("hospital") || v.contains("clinic") -> "🏥"
            v.contains("school") || v.contains("university") || v.contains("college") -> "🎓"
            v.contains("restaurant") || v.contains("cafe") || v.contains("fast_food") -> "🍽️"
            v.contains("hotel") || v.contains("guest_house") -> "🏨"
            v.contains("park") -> "🌳"
            v.contains("mall") || v.contains("marketplace") -> "🛍️"
            v.contains("bank") || v.contains("atm") -> "🏦"
            v.contains("fuel") -> "⛽"
            v.contains("temple") || v.contains("place_of_worship") -> "🛕"
            name.contains("station", ignoreCase = true) -> "🚉"
            else -> "📌"
        }
    }
}
