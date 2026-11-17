package com.destinationalarm.app.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import com.destinationalarm.app.data.Prefs

/**
 * Resolves the sound the alarm should play.
 *
 * Stored values:
 *  - [Prefs.SOUND_SYSTEM_ALARM]  -> device's default alarm sound (follows system changes)
 *  - [Prefs.SOUND_RINGTONE]      -> device's default ringtone
 *  - [Prefs.SOUND_NOTIFICATION]  -> device's default notification sound
 *  - [Prefs.SOUND_APP_TONE]      -> bundled tone inside the APK (always works offline)
 *  - content:// / file:// URI    -> a specific sound picked by the user
 */
object Sounds {

    fun uri(context: Context, value: String = Prefs.alarmSound(context)): Uri? = when {
        value == Prefs.SOUND_APP_TONE -> null

        value == Prefs.SOUND_RINGTONE ->
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI

        value == Prefs.SOUND_NOTIFICATION ->
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_NOTIFICATION_URI

        value.startsWith("content:") || value.startsWith("file:") -> Uri.parse(value)

        else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
    }

    fun label(context: Context, value: String = Prefs.alarmSound(context)): String = when {
        value == Prefs.SOUND_APP_TONE -> "App tone (offline)"

        value == Prefs.SOUND_RINGTONE -> "System ringtone"

        value == Prefs.SOUND_NOTIFICATION -> "Notification sound"

        value.startsWith("content:") || value.startsWith("file:") ->
            titleOf(context, Uri.parse(value), "Custom sound")

        else -> titleOf(context, uri(context, value), "System alarm sound")
    }

    private fun titleOf(context: Context, uri: Uri?, fallback: String): String {
        if (uri == null) return fallback
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }
}
