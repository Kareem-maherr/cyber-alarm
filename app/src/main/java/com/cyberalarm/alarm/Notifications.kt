package com.cyberalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ALARM = "alarm_ringing"
    const val CHANNEL_STATUS = "status"

    const val ID_RINGING = 2077
    const val ID_LOCK = 2078
    const val ID_SNOOZE = 2079

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Ringing alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen alarm. Sound is played by the app itself."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Snooze & app lock status", NotificationManager.IMPORTANCE_LOW),
        )
    }
}
