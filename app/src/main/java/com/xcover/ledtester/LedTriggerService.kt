package com.xcover.ledtester

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class LedTriggerService : Service() {

    companion object {
        const val EXTRA_COLOR_KEY = "extra_color_key"
        const val EXTRA_DELAY_MS = "extra_delay_ms"
        const val COUNTDOWN_CHANNEL_ID = "xcover_led_countdown"
        const val COUNTDOWN_NOTIFICATION_ID = 1002
        const val ACTION_COUNTDOWN_TICK = "com.xcover.ledtester.ACTION_COUNTDOWN_TICK"
        const val ACTION_COUNTDOWN_FIRED = "com.xcover.ledtester.ACTION_COUNTDOWN_FIRED"
        const val EXTRA_SECONDS_LEFT = "extra_seconds_left"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var fireRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureCountdownChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val colorKey = intent?.getStringExtra(EXTRA_COLOR_KEY)
            ?: XCoverLedManager.SUPPORTED_COLORS[0].key
        val delayMs = intent?.getLongExtra(EXTRA_DELAY_MS, 5000L) ?: 5000L

        startForeground(COUNTDOWN_NOTIFICATION_ID, buildCountdownNotification(5))
        acquireWakeLock(delayMs + 10_000L)
        scheduleCountdown(colorKey, delayMs)

        return START_NOT_STICKY
    }

    private fun ensureCountdownChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(COUNTDOWN_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            COUNTDOWN_CHANNEL_ID,
            "LED Countdown",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the LED trigger countdown while the tester locks the screen."
            enableLights(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildCountdownNotification(secondsLeft: Int): Notification {
        return NotificationCompat.Builder(this, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("LED fires in $secondsLeft s — LOCK YOUR SCREEN NOW!")
            .setContentText("Unplug charger, press power button, watch the top LED.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateCountdown(secondsLeft: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(COUNTDOWN_NOTIFICATION_ID, buildCountdownNotification(secondsLeft))
        sendBroadcast(Intent(ACTION_COUNTDOWN_TICK).putExtra(EXTRA_SECONDS_LEFT, secondsLeft))
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XCoverLedTester::TriggerLock"
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    private fun scheduleCountdown(colorKey: String, delayMs: Long) {
        tickRunnable?.let { handler.removeCallbacks(it) }
        fireRunnable?.let { handler.removeCallbacks(it) }

        val totalSeconds = (delayMs / 1000L).toInt().coerceAtLeast(1)
        var secondsLeft = totalSeconds
        updateCountdown(secondsLeft)

        tickRunnable = object : Runnable {
            override fun run() {
                secondsLeft -= 1
                if (secondsLeft > 0) {
                    updateCountdown(secondsLeft)
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        handler.postDelayed(tickRunnable!!, 1000L)

        fireRunnable = Runnable {
            val manager = XCoverLedManager(applicationContext)
            manager.fireLed(manager.colorForKey(colorKey))
            buzzOnFire()
            playBeepOnFire()
            sendBroadcast(Intent(ACTION_COUNTDOWN_FIRED))
            stopSelf()
        }
        handler.postDelayed(fireRunnable!!, delayMs)
    }

    private fun buzzOnFire() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(400)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun playBeepOnFire() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        fireRunnable?.let { handler.removeCallbacks(it) }
        releaseWakeLock()
        super.onDestroy()
    }
}
