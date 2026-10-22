package com.cyberalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ALARM = "alarm_ringing"
    const val CHANNEL_STATUS = "status"
    /** Own channel for the lock countdown: Live Updates need importance above MIN. */
    const val CHANNEL_LOCK = "lock_live"
    const val CHANNEL_BEDTIME = "bedtime"

    const val ID_RINGING = 2077
    const val ID_LOCK = 2078
    const val ID_SNOOZE = 2079
    const val ID_AWAKE = 2080
    const val ID_BEDTIME = 2081

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
            NotificationChannel(CHANNEL_STATUS, "Snooze status", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BEDTIME, "Bedtime reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nudge to go to sleep so you get your sleep goal before the next alarm."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LOCK, "App lock countdown", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Live countdown while apps are locked. Shown in the status bar chip / Now Bar."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }
}
