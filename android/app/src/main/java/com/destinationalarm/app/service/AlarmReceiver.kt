package com.destinationalarm.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.destinationalarm.app.alarm.AlarmService
import com.destinationalarm.app.data.Prefs

/**
 * Optional fallback entry point (e.g. a scheduled exact alarm). The primary trigger
 * lives inside TrackingService, so this only needs to be best-effort.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isArmed(context)) return
        if (Prefs.snoozeUntil(context) > System.currentTimeMillis()) return

        val i = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
            .putExtra(AlarmService.EXTRA_TITLE, "Destination reached")
            .putExtra(AlarmService.EXTRA_SUB, Prefs.trip(context)?.name ?: "Estimated arrival")

        runCatching { ContextCompat.startForegroundService(context, i) }
    }
}
