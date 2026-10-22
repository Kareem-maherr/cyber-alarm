package com.cyberalarm.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Message paths, same values as the phone app's WearPaths. */
object WearPaths {
    const val RING = "/cyberalarm/ring"
    const val STOP = "/cyberalarm/stop"
    const val AWAKE = "/cyberalarm/awake"
    const val OPEN_AWAKE = "/cyberalarm/open_awake"
}

/** What the watch is currently showing. */
sealed interface Alert {
    data class Ringing(val label: String) : Alert
    data class AwakeCheck(val stage: Int, val deadline: Long) : Alert
}

object AlertState {
    private val _current = MutableStateFlow<Alert?>(null)
    val current: StateFlow<Alert?> = _current.asStateFlow()

    fun set(alert: Alert?) {
        _current.value = alert
    }
}

/**
 * Receives the phone's alarm events. A full-screen notification brings up [AlertActivity] (Wear OS
 * doesn't let a background service start an activity directly); STOP clears everything.
 */
class WatchListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val payload = event.data.decodeToString()
        when (event.path) {
            WearPaths.RING -> show(Alert.Ringing(payload.ifBlank { "ALARM" }))
            WearPaths.AWAKE -> {
                val parts = payload.split("|")
                show(Alert.AwakeCheck(parts.getOrNull(0)?.toIntOrNull() ?: 1, parts.getOrNull(1)?.toLongOrNull() ?: 0L))
            }
            WearPaths.STOP -> {
                AlertState.set(null)
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }
        }
    }

    private fun show(alert: Alert) {
        AlertState.set(alert)
        val nm = getSystemService(NotificationManager::class.java)
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, AlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (alert) {
            is Alert.Ringing -> "ALARM // ${alert.label}"
            is Alert.AwakeCheck -> "STILL WITH US, CHOOM?"
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_watch)
            .setContentTitle(title)
            .setContentText("Solve it on your phone.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    companion object {
        private const val CHANNEL = "watch_alarm"
        private const val NOTIFICATION_ID = 2077

        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Phone alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 300, 600)
                },
            )
        }
    }
}
