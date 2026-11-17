package com.destinationalarm.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import org.maplibre.android.MapLibre

object Channels {
    const val TRACKING = "tracking"
    const val ALARM = "alarm"
}

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val tracking = NotificationChannel(
            Channels.TRACKING,
            getString(R.string.channel_tracking),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows distance and ETA while an alarm is armed"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val alarm = NotificationChannel(
            Channels.ALARM,
            getString(R.string.channel_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rings when you reach your destination"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            enableVibration(false)
            // The alarm sound is played by AlarmService on the ALARM audio stream,
            // so the channel itself stays silent.
            setSound(null, null)
            setBypassDnd(true)
        }

        nm.createNotificationChannels(listOf(tracking, alarm))
    }
}
