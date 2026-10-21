package com.cyberalarm.alarm

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.cyberalarm.R
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.lock.LockManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Single entry point for the ringing lifecycle: start -> (snooze)* -> dismiss. */
@Singleton
class AlarmController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val store: RingingStateStore,
    private val lockManager: LockManager,
) {
    fun startRinging(alarmId: Long) {
        store.markRinging(alarmId)
        AlarmService.start(context, alarmId)
    }

    fun snoozesLeft(alarm: Alarm): Int =
        if (!alarm.snoozeEnabled) 0 else (alarm.maxSnoozes - store.snoozeCount(alarm.id)).coerceAtLeast(0)

    /** Only call after the dismissal challenge chain has been passed. */
    suspend fun dismiss(alarm: Alarm) {
        store.clearRinging()
        store.resetSnooze()
        scheduler.cancelGuard(alarm.id)
        AlarmService.stop(context)
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_SNOOZE)
        if (alarm.id > 0) {
            if (alarm.isRepeating) {
                scheduler.schedule(alarm)
            } else {
                repo.setEnabled(alarm.id, false)
                scheduler.cancel(alarm.id)
            }
        }
        lockManager.startLocks(alarm)
    }

    /** Returns false if no snoozes are left (the caller keeps ringing). */
    fun snooze(alarm: Alarm): Boolean {
        if (snoozesLeft(alarm) <= 0) return false
        val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        store.recordSnooze(alarm.id, until)
        store.clearRinging()
        scheduler.cancelGuard(alarm.id)
        scheduler.scheduleSnooze(alarm.id, until)
        AlarmService.stop(context)
        postSnoozeNotice(alarm, until)
        return true
    }

    private fun postSnoozeNotice(alarm: Alarm, until: Long) {
        val time = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()))
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("SNOOZED // ${alarm.label}")
            .setContentText("Re-engaging at $time. Snoozes left: ${snoozesLeft(alarm)}")
            .setOngoing(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_SNOOZE, n) }
    }
}
