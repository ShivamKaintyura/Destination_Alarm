package com.destinationalarm.app.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.destinationalarm.app.MainActivity
import com.destinationalarm.app.R
import com.destinationalarm.app.alarm.AlarmService
import com.destinationalarm.app.data.Prefs
import com.destinationalarm.app.model.Fix
import com.destinationalarm.app.model.Trip
import com.destinationalarm.app.model.TripBus
import com.destinationalarm.app.net.Net
import com.destinationalarm.app.util.Geo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class TrackingService : android.app.Service() {

    companion object {
        const val ACTION_START = "com.destinationalarm.START"
        const val ACTION_RELOAD = "com.destinationalarm.RELOAD"
        const val ACTION_STOP = "com.destinationalarm.STOP"

        private const val NOTIF_ID = 1001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fused: FusedLocationProviderClient

    private var callback: LocationCallback? = null
    private var trip: Trip? = null
    private var lastFix: Fix? = null
    private var etaSec: Int? = null

    private var lastRouteAt = 0L
    private var routeBusy = false
    private var deadman: Job? = null
    private var alarming = false
    private var armedAt = 0L

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.clearTrip(this)
                shutdown()
                return START_NOT_STICKY
            }

            else -> {
                val t = Prefs.trip(this)
                if (t == null) {
                    shutdown()
                    return START_NOT_STICKY
                }
                trip = t
                alarming = false
                armedAt = System.currentTimeMillis()
                TripBus.alarming.value = false
                TripBus.tracking.value = true

                startAsForeground()

                if (hasFineLocation()) {
                    startUpdates()
                } else {
                    updateNotification("Location permission is missing. Open the app to fix it.")
                }

                if (lastFix != null) refreshRoute(force = true)
            }
        }
        return START_STICKY
    }

    /* ---------------- permission ---------------- */

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /* ---------------- foreground ---------------- */

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun buildNotification(statusOverride: String? = null): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = statusOverride ?: run {
            val t = trip
            val f = lastFix
            if (t == null || f == null) {
                "Waiting for GPSâ€¦"
            } else {
                val d = Geo.safeDistance(f.lat, f.lon, t.lat, t.lon)
                buildString {
                    append(Geo.fmtDistance(d))
                    append(" to go")
                    etaSec?.let { append(" Â· ETA ").append(Geo.fmtDuration(it)) }
                }
            }
        }

        return NotificationCompat.Builder(this, com.destinationalarm.app.Channels.TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(trip?.name ?: "Destination alarm")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(statusOverride: String? = null) {
        if (!Prefs.isArmed(this)) return
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(statusOverride))
        }
    }

    /* ---------------- location ---------------- */

    private fun startUpdates() {
        if (callback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(8_000L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onFix(it) }
            }
        }
        callback = cb

        try {
            fused.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            callback = null
        }
    }

    private fun stopUpdates() {
        val cb = callback ?: return
        callback = null
        runCatching { fused.removeLocationUpdates(cb) }
    }

    private fun onFix(loc: Location) {
        val fix = Fix(
            lat = loc.latitude,
            lon = loc.longitude,
            acc = if (loc.hasAccuracy()) loc.accuracy else 999f,
            speed = if (loc.hasSpeed()) loc.speed else -1f,
            t = System.currentTimeMillis()
        )
        lastFix = fix
        TripBus.fix.value = fix

        val t = trip ?: return
        updateNotification()

        if (alarming) return
        if (System.currentTimeMillis() < Prefs.snoozeUntil(this)) return

        val d = Geo.safeDistance(fix.lat, fix.lon, t.lat, t.lon)
        if (d < 0) return

        val reliable = fix.acc <= 150f || d <= fix.acc * 1.5

        maybeRefreshRoute()

        // Give the user a few seconds after pressing Start: standing at the
        // destination should not ring instantly.
        if (System.currentTimeMillis() - armedAt < 8_000) return

        if (d <= t.radiusM && reliable) {
            fire("Destination reached", "${d.roundToInt()} m away")
            return
        }

        val eta = etaSec ?: fallbackEta(d, fix)
        if (t.minsBefore > 0 && eta != null && eta <= t.minsBefore * 60) {
            fire("Almost there", "About ${Geo.fmtDuration(eta)} to destination")
        }
    }

    /* ---------------- ETA ---------------- */

    private fun maybeRefreshRoute() {
        val now = System.currentTimeMillis()
        if (routeBusy || now - lastRouteAt < 20_000) return
        refreshRoute(force = false)
    }

    private fun refreshRoute(force: Boolean) {
        val t = trip ?: return
        val f = lastFix ?: return
        if (!force && System.currentTimeMillis() - lastRouteAt < 15_000) return
        if (routeBusy) return

        if (t.mode == "TRANSIT") {
            val d = Geo.safeDistance(f.lat, f.lon, t.lat, t.lon)
            if (d < 0) return
            val e = fallbackEta(d, f)
            etaSec = e
            TripBus.etaSec.value = e
            TripBus.routeKm.value = d / 1000.0
            TripBus.routeLine.value = listOf(f.lon to f.lat, t.lon to t.lat)
            scheduleDeadman(e)
            updateNotification()
            return
        }

        routeBusy = true
        lastRouteAt = System.currentTimeMillis()
        val from = f

        scope.launch {
            val route = runCatching {
                Net.route(t.mode, from.lat, from.lon, t.lat, t.lon)
            }.getOrNull()

            if (route != null) {
                etaSec = route.durationSec
                TripBus.etaSec.value = route.durationSec
                TripBus.routeKm.value = route.distanceM / 1000.0
                TripBus.routeLine.value = route.points
                scheduleDeadman(route.durationSec)
            } else {
                val d = Geo.safeDistance(from.lat, from.lon, t.lat, t.lon)
                if (d >= 0) {
                    val e = fallbackEta(d, from)
                    etaSec = e
                    TripBus.etaSec.value = e
                    TripBus.routeKm.value = d / 1000.0
                    scheduleDeadman(e)
                }
            }

            routeBusy = false
            withContext(Dispatchers.Main) { updateNotification() }
        }
    }

    private fun fallbackEta(distanceM: Double, fix: Fix): Int? {
        val t = trip ?: return null
        val measured = if (fix.speed > 0.5f) fix.speed.toDouble() else 0.0
        val speed = when {
            measured > 0.5 -> measured
            t.mode == "TRANSIT" -> 7.0   // ~25 km/h average metro speed
            t.mode == "WALK" -> 1.3
            else -> 0.0
        }
        if (speed <= 0) return null
        return (distanceM / speed).roundToInt().coerceAtLeast(1)
    }

    /**
     * ETA-based safety net: if GPS dies in a tunnel, this still rings close to the
     * estimated arrival time (minus the "ring before" lead).
     */
    private fun scheduleDeadman(eta: Int?) {
        deadman?.cancel()
        val t = trip ?: return
        if (eta == null) return

        val leadSeconds = t.minsBefore * 60
        val delayMs = ((eta - leadSeconds).coerceAtLeast(3)).toLong() * 1000L

        deadman = scope.launch {
            delay(delayMs)
            if (alarming) return@launch
            if (!Prefs.isArmed(this@TrackingService)) return@launch
            if (System.currentTimeMillis() < Prefs.snoozeUntil(this@TrackingService)) return@launch

            withContext(Dispatchers.Main) {
                fire(
                    if (leadSeconds > 0) "Almost there" else "Destination reached",
                    "Estimated arrival"
                )
            }
        }
    }

    /* ---------------- alarm ---------------- */

    private fun fire(title: String, sub: String) {
        if (alarming) return
        alarming = true
        deadman?.cancel()
        TripBus.alarming.value = true
        stopUpdates()

        val i = Intent(this, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
            .putExtra(AlarmService.EXTRA_TITLE, title)
            .putExtra(AlarmService.EXTRA_SUB, sub)

        runCatching { ContextCompat.startForegroundService(this, i) }
        updateNotification("$title â€” $sub")
    }

    /* ---------------- lifecycle ---------------- */

    private fun shutdown() {
        deadman?.cancel()
        deadman = null
        stopUpdates()
        TripBus.tracking.value = false
        TripBus.alarming.value = false
        TripBus.etaSec.value = null
        TripBus.routeLine.value = emptyList()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        deadman?.cancel()
        stopUpdates()
        scope.cancel()
        TripBus.tracking.value = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep tracking even if the user swipes the app away while a trip is armed.
        if (!Prefs.isArmed(this)) {
            shutdown()
        }
        super.onTaskRemoved(rootIntent)
    }
}

