package com.destinationalarm.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object TrackingControl {

    fun start(context: Context) {
        val i = Intent(context, TrackingService::class.java)
            .setAction(TrackingService.ACTION_START)
        runCatching { ContextCompat.startForegroundService(context, i) }
    }

    fun reload(context: Context) {
        val i = Intent(context, TrackingService::class.java)
            .setAction(TrackingService.ACTION_RELOAD)
        runCatching { ContextCompat.startForegroundService(context, i) }
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, TrackingService::class.java)) }
    }
}
