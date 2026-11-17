package com.destinationalarm.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.destinationalarm.app.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Prefs.isArmed(context)) return
        runCatching { TrackingControl.start(context) }
    }
}
