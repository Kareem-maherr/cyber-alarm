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

    /** [escalation] 0 = normal; each failed awake check re-fires one level higher. */
    fun markRinging(alarmId: Long, escalation: Int = escalation()) = prefs.edit(commit = true) {
        putLong(KEY_ACTIVE, alarmId)
        putLong(KEY_STARTED, System.currentTimeMillis())
        putInt(KEY_ESCALATION, escalation)
        remove(KEY_SNOOZE_UNTIL)
        remove(KEY_SILENCE_UNTIL).remove(KEY_SILENCE_USED)
    }

    fun clearRinging() = prefs.edit(commit = true) {
        remove(KEY_ACTIVE).remove(KEY_STARTED).remove(KEY_SILENCE_UNTIL).remove(KEY_SILENCE_USED)
    }

    fun startedAt(): Long? = prefs.getLong(KEY_STARTED, 0).takeIf { it > 0 }

    fun escalation(): Int = prefs.getInt(KEY_ESCALATION, 0)

    /** The current wake session (survives dismissal so awake checks can be credited to it). */
    fun sessionId(): Long? = prefs.getLong(KEY_SESSION, NONE).takeIf { it != NONE }

    fun setSessionId(id: Long) = prefs.edit(commit = true) { putLong(KEY_SESSION, id) }

    /**
     * Jack-in quiet window: sound + vibration pause until [until] while the user walks to their
     * tag. Allowed once per ring, so it can't be chained into a silent alarm.
     */
    fun startSilence(until: Long): Boolean {
        if (prefs.getBoolean(KEY_SILENCE_USED, false)) return false
        prefs.edit(commit = true) { putLong(KEY_SILENCE_UNTIL, until).putBoolean(KEY_SILENCE_USED, true) }
        return true
    }

    fun silenceUntil(): Long = prefs.getLong(KEY_SILENCE_UNTIL, 0)

    fun silenceUsed(): Boolean = prefs.getBoolean(KEY_SILENCE_USED, false)

    fun isSilenced(now: Long = System.currentTimeMillis()): Boolean = silenceUntil() > now

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
        const val KEY_ESCALATION = "escalation"
        const val KEY_SESSION = "session_id"
        const val KEY_SILENCE_UNTIL = "silence_until"
        const val KEY_SILENCE_USED = "silence_used"
    }
}
