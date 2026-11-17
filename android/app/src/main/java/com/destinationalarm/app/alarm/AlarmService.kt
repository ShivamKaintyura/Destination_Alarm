package com.destinationalarm.app.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.destinationalarm.app.Channels
import com.destinationalarm.app.MainActivity
import com.destinationalarm.app.R
import com.destinationalarm.app.data.Prefs
import com.destinationalarm.app.model.TripBus
import com.destinationalarm.app.service.TrackingControl
import kotlin.math.roundToInt

class AlarmService : android.app.Service() {

    companion object {
        const val ACTION_START = "com.destinationalarm.ALARM_START"
        const val ACTION_DISMISS = "com.destinationalarm.ALARM_DISMISS"
        const val ACTION_SNOOZE = "com.destinationalarm.ALARM_SNOOZE"

        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_SUB = "alarm_sub"

        private const val NOTIF_ID = 2001
        private const val SNOOZE_MS = 5 * 60 * 1000L
    }

    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var savedAlarmVolume = -1

    private val alarmAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                Prefs.clearTrip(this)
                Prefs.clearSnooze(this)
                TrackingControl.stop(this)
                TripBus.tracking.value = false
                TripBus.alarming.value = false
                stopEverything()
                return START_NOT_STICKY
            }

            ACTION_SNOOZE -> {
                Prefs.setSnooze(this, System.currentTimeMillis() + SNOOZE_MS)
                TripBus.alarming.value = false
                stopEverything()
                // Resume tracking; the service will not re-trigger until the snooze ends.
                TrackingControl.start(this)
                return START_NOT_STICKY
            }

            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "You're here!"
                val sub = intent?.getStringExtra(EXTRA_SUB) ?: "Destination reached"
                startAlarm(title, sub)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(title: String, sub: String) {
        val notif = buildNotification(title, sub)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)

        startAudio()
        if (Prefs.vibrate(this)) startVibration()
        acquireWakeLock()
    }

    private fun buildNotification(title: String, sub: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            10,
            Intent(this, AlarmActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUB, sub)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismiss = PendingIntent.getService(
            this, 11,
            Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze = PendingIntent.getService(
            this, 12,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openApp = PendingIntent.getActivity(
            this, 13,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Channels.ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(sub)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Dismiss", dismiss)
            .addAction(0, "Snooze 5 min", snooze)
            .build()
    }

    /* ---------------- audio ---------------- */

    private fun startAudio() {
        if (player?.isPlaying == true) return
        if (ringtone?.isPlaying == true) return

        val am = getSystemService(AudioManager::class.java)

        // 1. Make sure the ALARM stream itself is audible. Silent/ringer mode does
        //    not touch this stream, so the alarm still rings on a silenced phone.
        forceAlarmVolume(am)

        // 2. Take transient focus on the alarm stream so music/podcasts duck out.
        requestFocus(am)

        // 3. Play: chosen system sound -> bundled fallback -> Ringtone fallback.
        val uri = Sounds.uri(this)
        player = uri?.let { runCatching { buildPlayer(it) }.getOrNull() }
            ?: runCatching { buildBundledPlayer() }.getOrNull()

        if (player == null) {
            runCatching { playWithRingtone(uri) }
        }
    }

    private fun buildPlayer(uri: Uri): MediaPlayer {
        val p = MediaPlayer()
        return try {
            p.setAudioAttributes(alarmAttributes)
            p.setDataSource(this, uri)
            p.isLooping = true
            p.setVolume(1f, 1f)
            p.prepare()
            p.setOnErrorListener { _, _, _ -> true }
            p.start()
            p
        } catch (t: Throwable) {
            runCatching { p.release() }
            throw t
        }
    }

    private fun buildBundledPlayer(): MediaPlayer {
        val afd = resources.openRawResourceFd(R.raw.alarm)
            ?: throw IllegalStateException("bundled tone missing")
        val p = MediaPlayer()
        return try {
            p.setAudioAttributes(alarmAttributes)
            p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            p.isLooping = true
            p.setVolume(1f, 1f)
            p.prepare()
            p.start()
            p
        } catch (t: Throwable) {
            runCatching { afd.close() }
            runCatching { p.release() }
            throw t
        }
    }

    /** Last resort for DRM-protected ringtones that MediaPlayer cannot open directly. */
    private fun playWithRingtone(uri: Uri?): Ringtone? {
        val u = uri ?: return null
        val r = RingtoneManager.getRingtone(this, u) ?: return null
        r.audioAttributes = alarmAttributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { r.isLooping = true }
        }
        r.play()
        ringtone = r
        return r
    }

    private fun forceAlarmVolume(am: AudioManager?) {
        if (am == null) return
        runCatching {
            if (savedAlarmVolume < 0) {
                savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (max <= 0) return@runCatching
            val target = maxOf(
                am.getStreamVolume(AudioManager.STREAM_ALARM),
                (max * 0.9f).roundToInt()
            )
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun restoreAlarmVolume(am: AudioManager?) {
        if (am == null || savedAlarmVolume < 0) return
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0) }
        savedAlarmVolume = -1
    }

    private fun requestFocus(am: AudioManager?) {
        if (am == null) return
        runCatching {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(alarmAttributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun stopAudio() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null

        runCatching { ringtone?.stop() }
        ringtone = null

        val am = getSystemService(AudioManager::class.java)
        runCatching { focusRequest?.let { am.abandonAudioFocusRequest(it) } }
        focusRequest = null
        restoreAlarmVolume(am)
    }

    /* ---------------- vibration ---------------- */

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    private fun startVibration() {
        vibrator = getVibrator()
        val pattern = longArrayOf(0, 700, 250, 700, 250, 900)
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    /* ---------------- wake lock ---------------- */

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DestinationAlarm:alarm").apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun stopEverything() {
        stopAudio()
        stopVibration()
        releaseWakeLock()
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIF_ID)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAudio()
        stopVibration()
        releaseWakeLock()
        super.onDestroy()
    }
}
