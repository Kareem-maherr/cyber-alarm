package com.cyberalarm.alarm

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.cyberalarm.R
import com.cyberalarm.cred.WakeReport
import com.cyberalarm.cred.WakeTracker
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.lock.LockManager
import com.cyberalarm.wear.WearBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Single entry point for the ringing lifecycle: start -> (snooze)* -> dismiss -> awake checks. */
@Singleton
class AlarmController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val store: RingingStateStore,
    private val lockManager: LockManager,
    private val awake: AwakeCheckManager,
    private val tracker: WakeTracker,
    private val wear: WearBridge,
    private val sync: ScheduleSync,
) {
    /** A new occurrence (regular fire): fresh session, escalation back to 0. */
    suspend fun startFresh(alarm: Alarm) {
        store.markRinging(alarm.id, escalation = 0)
        tracker.startSession(alarm)
        AlarmService.start(context, alarm.id)
        wear.ringStarted(alarm.label)
    }

    /** Resume ringing at the current escalation (snooze ended, reboot, guard). */
    fun startRinging(alarmId: Long) {
        store.markRinging(alarmId)
        AlarmService.start(context, alarmId)
        wear.ringStarted("ALARM")
    }

    /** An awake check went unanswered: ring again, one escalation level higher. */
    suspend fun reRing(alarmId: Long) {
        val level = store.escalation() + 1
        store.markRinging(alarmId, escalation = level)
        tracker.onReRing(level)
        AlarmService.start(context, alarmId)
        wear.ringStarted("YOU FELL ASLEEP")
    }

    fun escalation(): Int = store.escalation()

    fun snoozesLeft(alarm: Alarm): Int = when {
        !alarm.snoozeEnabled || store.escalation() > 0 -> 0
        else -> (alarm.maxSnoozes - store.snoozeCount(alarm.id)).coerceAtLeast(0)
    }

    /** Only call after the dismissal challenge chain has been passed. */
    suspend fun dismiss(alarm: Alarm, mistakes: Int = 0, usedJackIn: Boolean = false): WakeReport {
        store.clearRinging()
        store.resetSnooze()
        scheduler.cancelGuard(alarm.id)
        AlarmService.stop(context)
        wear.ringStopped()
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
        val report = tracker.onDismiss(alarm, mistakes, usedJackIn)
        if (alarm.awakeCheck && alarm.id > 0) awake.begin(alarm.id)
        runCatching { sync.refresh() } // next bedtime + widget now point at the following alarm
        return report
    }

    /** Returns false if no snoozes are left (the caller keeps ringing). */
    suspend fun snooze(alarm: Alarm): Boolean {
        if (snoozesLeft(alarm) <= 0) return false
        val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        store.recordSnooze(alarm.id, until)
        store.clearRinging()
        scheduler.cancelGuard(alarm.id)
        scheduler.scheduleSnooze(alarm.id, until)
        AlarmService.stop(context)
        wear.ringStopped()
        tracker.onSnooze()
        postSnoozeNotice(alarm, until)
        return true
    }

    private fun postSnoozeNotice(alarm: Alarm, until: Long) {
        val time = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()))
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("SNOOZED // ${alarm.label}")
            .setContentText("Re-engaging at $time. Snoozes left: ${snoozesLeft(alarm)}. Don't get comfy, choom.")
            .setOngoing(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_SNOOZE, n) }
    }
}
