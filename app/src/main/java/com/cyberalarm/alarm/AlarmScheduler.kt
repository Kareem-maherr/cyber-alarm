package com.cyberalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.cyberalarm.MainActivity
import com.cyberalarm.data.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [AlarmManager]. Everything goes through `setAlarmClock()`, which is exact, wakes the
 * device from Doze and shows the alarm icon in the status bar.
 *
 * Three PendingIntents exist per alarm (distinct request codes):
 *  - FIRE   the regular occurrence
 *  - SNOOZE a snoozed re-fire
 *  - GUARD  a rolling "dead man's switch" kept ~90s ahead while ringing; if our process gets
 *           killed mid-alarm, the guard fires and the alarm resumes.
 */
@Singleton
class AlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val at = NextTrigger.compute(alarm, now).toInstant().toEpochMilli()
        setClock(at, pending(alarm.id, AlarmReceiver.ACTION_FIRE))
    }

    fun rescheduleAll(alarms: List<Alarm>) = alarms.forEach { schedule(it) }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(pending(alarmId, AlarmReceiver.ACTION_FIRE))
        alarmManager.cancel(pending(alarmId, AlarmReceiver.ACTION_SNOOZE_FIRE))
        cancelGuard(alarmId)
    }

    fun scheduleSnooze(alarmId: Long, atMillis: Long) =
        setClock(atMillis, pending(alarmId, AlarmReceiver.ACTION_SNOOZE_FIRE))

    fun scheduleGuard(alarmId: Long, atMillis: Long) =
        setClock(atMillis, pending(alarmId, AlarmReceiver.ACTION_GUARD))

    fun cancelGuard(alarmId: Long) = alarmManager.cancel(pending(alarmId, AlarmReceiver.ACTION_GUARD))

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setClock(atMillis: Long, operation: PendingIntent) {
        if (canScheduleExact()) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        } else {
            // Best effort only: without the exact-alarm grant the OS may delay this by minutes.
            Log.w(TAG, "Exact alarms not permitted; falling back to inexact scheduling")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun pending(alarmId: Long, action: String): PendingIntent {
        val slot = when (action) {
            AlarmReceiver.ACTION_FIRE -> 0
            AlarmReceiver.ACTION_SNOOZE_FIRE -> 1
            else -> 2
        }
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context, (alarmId * 4 + slot).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
