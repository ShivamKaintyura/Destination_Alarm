package com.destinationalarm.app.data

import android.content.Context
import com.destinationalarm.app.model.SavedPlace
import com.destinationalarm.app.model.Trip
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object Prefs {
    private const val FILE = "destination_alarm"

    private const val K_ARMED = "armed"
    private const val K_NAME = "dest_name"
    private const val K_LAT = "dest_lat"
    private const val K_LON = "dest_lon"
    private const val K_RADIUS = "radius"
    private const val K_MINS = "mins_before"
    private const val K_MODE = "mode"
    private const val K_SNOOZE = "snooze_until"
    private const val K_ONBOARDED = "onboarded"
    private const val K_SOUND = "alarm_sound"
    private const val K_VIBRATE = "vibrate"
    private const val K_SAVED = "saved_places"

    const val SOUND_SYSTEM_ALARM = "system_alarm"
    const val SOUND_RINGTONE = "system_ringtone"
    const val SOUND_NOTIFICATION = "system_notification"
    const val SOUND_APP_TONE = "app_tone"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun saveTrip(c: Context, t: Trip) {
        sp(c).edit()
            .putBoolean(K_ARMED, true)
            .putString(K_NAME, t.name)
            .putString(K_LAT, t.lat.toString())
            .putString(K_LON, t.lon.toString())
            .putInt(K_RADIUS, t.radiusM)
            .putInt(K_MINS, t.minsBefore)
            .putString(K_MODE, t.mode)
            .apply()
    }

    fun trip(c: Context): Trip? {
        val p = sp(c)
        if (!p.getBoolean(K_ARMED, false)) return null
        val name = p.getString(K_NAME, null) ?: return null
        val lat = p.getString(K_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = p.getString(K_LON, null)?.toDoubleOrNull() ?: return null
        return Trip(
            lat = lat,
            lon = lon,
            name = name,
            radiusM = p.getInt(K_RADIUS, 500),
            minsBefore = p.getInt(K_MINS, 0),
            mode = p.getString(K_MODE, "DRIVE") ?: "DRIVE"
        )
    }

    fun isArmed(c: Context): Boolean = sp(c).getBoolean(K_ARMED, false)

    fun clearTrip(c: Context) {
        sp(c).edit()
            .remove(K_ARMED)
            .remove(K_NAME)
            .remove(K_LAT)
            .remove(K_LON)
            .apply()
    }

    fun snoozeUntil(c: Context): Long = sp(c).getLong(K_SNOOZE, 0L)

    fun setSnooze(c: Context, until: Long) {
        sp(c).edit().putLong(K_SNOOZE, until).apply()
    }

    fun clearSnooze(c: Context) {
        sp(c).edit().remove(K_SNOOZE).apply()
    }

    fun isOnboarded(c: Context): Boolean = sp(c).getBoolean(K_ONBOARDED, false)

    fun setOnboarded(c: Context) {
        sp(c).edit().putBoolean(K_ONBOARDED, true).apply()
    }

    fun alarmSound(c: Context): String =
        sp(c).getString(K_SOUND, SOUND_SYSTEM_ALARM) ?: SOUND_SYSTEM_ALARM

    fun setAlarmSound(c: Context, value: String) {
        sp(c).edit().putString(K_SOUND, value).apply()
    }

    fun vibrate(c: Context): Boolean = sp(c).getBoolean(K_VIBRATE, true)

    fun setVibrate(c: Context, on: Boolean) {
        sp(c).edit().putBoolean(K_VIBRATE, on).apply()
    }

    /* ---------------- saved places (local only) ---------------- */

    fun savedPlaces(c: Context): List<SavedPlace> {
        val raw = sp(c).getString(K_SAVED, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        SavedPlace(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            label = o.optString("label").ifBlank { "Place" },
                            name = o.optString("name"),
                            lat = o.optDouble("lat"),
                            lon = o.optDouble("lon")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSavedPlaces(c: Context, places: List<SavedPlace>) {
        val arr = JSONArray()
        places.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("label", p.label)
                    .put("name", p.name)
                    .put("lat", p.lat)
                    .put("lon", p.lon)
            )
        }
        sp(c).edit().putString(K_SAVED, arr.toString()).apply()
    }

    /** Removes every stored value except the onboarding flag. */
    fun clearAllData(c: Context) {
        val onboarded = isOnboarded(c)
        sp(c).edit().clear().apply()
        if (onboarded) setOnboarded(c)
    }
}
