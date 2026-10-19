package com.xcover.ledtester

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class XCoverLedManager(private val context: Context) {

    data class LedColor(
        val key: String,
        val label: String,
        val argb: Int
    )

    companion object {
        const val ACTION_DISMISS_LED = "com.xcover.ledtester.ACTION_DISMISS_LED"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_PREFIX = "xcover_led_"

        val SUPPORTED_COLORS = listOf(
            LedColor("red", "Red", Color.RED),
            LedColor("green", "Green", Color.GREEN),
            LedColor("blue", "Blue", Color.BLUE),
            LedColor("yellow", "Yellow", 0xFFFFFF00.toInt()),
            LedColor("cyan", "Cyan", Color.CYAN),
            LedColor("magenta", "Magenta", Color.MAGENTA),
            LedColor("white", "White", Color.WHITE)
        )

        fun channelIdFor(key: String): String = "$CHANNEL_PREFIX$key"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun channelIdForColor(key: String): String = channelIdFor(key)

    fun colorForKey(key: String): LedColor =
        SUPPORTED_COLORS.firstOrNull { it.key == key } ?: SUPPORTED_COLORS[0]

    fun registerAllChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        for (color in SUPPORTED_COLORS) {
            registerChannel(color)
        }
    }

    fun recreateChannelForColor(color: LedColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.deleteNotificationChannel(channelIdFor(color.key))
        registerChannel(color)
    }

    private fun registerChannel(color: LedColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channelId = channelIdFor(color.key)
        if (notificationManager.getNotificationChannel(channelId) != null) return

        val channel = NotificationChannel(
            channelId,
            "LED ${color.label}",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Hardware notification LED test channel for ${color.label} on Galaxy XCover Pro."
            enableLights(true)
            lightColor = color.argb
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun fireLed(color: LedColor) {
        recreateChannelForColor(color)
        val channelId = channelIdFor(color.key)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, DismissLedReceiver::class.java).apply {
                action = ACTION_DISMISS_LED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle("XCover LED: ${color.label}")
            .setContentText("Hardware LED active (${color.label}). Tap Dismiss LED to turn it off.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLights(color.argb, 500, 500)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss LED",
                dismissIntent
            )
            .build()

        notification.flags = notification.flags or Notification.FLAG_SHOW_LIGHTS
        notification.ledARGB = color.argb
        notification.ledOnMS = 500
        notification.ledOffMS = 500

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun clearAll() {
        notificationManager.cancelAll()
    }

    val isDndBypassGranted: Boolean
        get() = notificationManager.isNotificationPolicyAccessGranted
}
