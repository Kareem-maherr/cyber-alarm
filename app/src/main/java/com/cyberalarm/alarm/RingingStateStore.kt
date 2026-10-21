package com.cyberalarm.alarm

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable "an alarm is ringing" flag. Written with commit() (synchronous) because the whole point
 * is surviving a kill or power-off a moment later: after reboot [BootReceiver] sees the flag and
 * re-fires the alarm, so the challenge still has to be solved.
 */
@Singleton
class RingingStateStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("ringing_state", Context.MODE_PRIVATE)

    fun activeAlarmId(): Long? = prefs.getLong(KEY_ACTIVE, NONE).takeIf { it != NONE }

    fun isRinging(): Boolean = activeAlarmId() != null

    fun markRinging(alarmId: Long) = prefs.edit(commit = true) {
        putLong(KEY_ACTIVE, alarmId)
        putLong(KEY_STARTED, System.currentTimeMillis())
        remove(KEY_SNOOZE_UNTIL)
    }

    fun clearRinging() = prefs.edit(commit = true) { remove(KEY_ACTIVE).remove(KEY_STARTED) }

    fun snoozeCount(alarmId: Long): Int =
        if (prefs.getLong(KEY_SNOOZE_ALARM, NONE) == alarmId) prefs.getInt(KEY_SNOOZE_COUNT, 0) else 0

    fun recordSnooze(alarmId: Long, until: Long) = prefs.edit(commit = true) {
        putInt(KEY_SNOOZE_COUNT, snoozeCount(alarmId) + 1)
        putLong(KEY_SNOOZE_ALARM, alarmId)
        putLong(KEY_SNOOZE_UNTIL, until)
    }

    /** Pending snooze (alarmId to fire time), so it can be re-armed after a reboot. */
    fun pendingSnooze(): Pair<Long, Long>? {
        val id = prefs.getLong(KEY_SNOOZE_ALARM, NONE)
        val until = prefs.getLong(KEY_SNOOZE_UNTIL, 0)
        return if (id != NONE && until > 0) id to until else null
    }

    fun resetSnooze() = prefs.edit(commit = true) {
        remove(KEY_SNOOZE_COUNT).remove(KEY_SNOOZE_ALARM).remove(KEY_SNOOZE_UNTIL)
    }

    private companion object {
        const val NONE = -1L
        const val KEY_ACTIVE = "active_alarm_id"
        const val KEY_STARTED = "started_at"
        const val KEY_SNOOZE_ALARM = "snooze_alarm_id"
        const val KEY_SNOOZE_COUNT = "snooze_count"
        const val KEY_SNOOZE_UNTIL = "snooze_until"
    }
}
