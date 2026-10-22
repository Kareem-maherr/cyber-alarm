package com.cyberalarm.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.cyberalarm.R
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.cred.WakeTracker
import com.cyberalarm.di.AppScope
import com.cyberalarm.enforcement.AwakeCheckActivity
import com.cyberalarm.wear.WearBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Are you still awake?" follow-ups after a dismissal.
 *
 * Dismiss -> check #1 at +5 min -> check #2 at +10 min. Each check opens [AwakeCheckActivity] with a
 * short challenge and a [WINDOW_MS] deadline (an exact alarm of its own, so it works even if the
 * screen never turned on or our process died). Missing the deadline re-fires the alarm, whose next
 * dismissal starts the cycle again.
 *
 * State lives in SharedPreferences (commit) so a reboot can't skip a pending check.
 */
@Singleton
class AwakeCheckManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: AlarmScheduler,
    private val ringing: RingingStateStore,
    private val tracker: WakeTracker,
    private val wear: WearBridge,
    @AppScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("awake_check", Context.MODE_PRIVATE)

    data class State(
        val alarmId: Long,
        /** 1 or 2: which check is next / running. */
        val stage: Int,
        val dismissedAt: Long,
        /** When the next check opens; 0 while one is running. */
        val nextCheckAt: Long,
        /** Deadline of the running check; 0 if none is running. */
        val deadline: Long,
    ) {
        val running: Boolean get() = deadline > 0
    }

    fun state(): State? {
        val id = prefs.getLong(KEY_ALARM, NONE).takeIf { it != NONE } ?: return null
        return State(
            id,
            prefs.getInt(KEY_STAGE, 1),
            prefs.getLong(KEY_DISMISSED, 0),
            prefs.getLong(KEY_NEXT, 0),
            prefs.getLong(KEY_DEADLINE, 0),
        )
    }

    /** Called on every real dismissal of an alarm that has awake checks on. */
    fun begin(alarmId: Long, now: Long = System.currentTimeMillis()) {
        state()?.let { cancelAlarms(it.alarmId) }
        dismissNotice()
        save(State(alarmId, stage = 1, dismissedAt = now, nextCheckAt = now + offsetFor(1), deadline = 0))
        scheduler.scheduleAwakeCheck(alarmId, now + offsetFor(1))
    }

    /** The check time arrived: open the prompt and arm its deadline. */
    fun open(alarmId: Long) {
        val s = state()?.takeIf { it.alarmId == alarmId } ?: return
        if (ringing.isRinging()) {
            // Busy solving another alarm right now: that counts as awake.
            passed(alarmId)
            return
        }
        val deadline = System.currentTimeMillis() + WINDOW_MS
        save(s.copy(nextCheckAt = 0, deadline = deadline))
        scheduler.scheduleAwakeDeadline(alarmId, deadline)
        showPrompt(alarmId)
        wear.awakeCheck(s.stage, deadline)
    }

    fun passed(alarmId: Long) {
        val s = state()?.takeIf { it.alarmId == alarmId } ?: return
        scheduler.cancelAwakeDeadline(alarmId)
        dismissNotice()
        wear.awakeDone()
        scope.launch { tracker.onCheckPassed() }
        if (s.stage >= STAGES) {
            clear()
            return
        }
        val next = maxOf(s.dismissedAt + offsetFor(s.stage + 1), System.currentTimeMillis() + 60_000)
        save(s.copy(stage = s.stage + 1, nextCheckAt = next, deadline = 0))
        scheduler.scheduleAwakeCheck(alarmId, next)
    }

    /**
     * Deadline fired. Returns true (once) if the check was still unanswered, i.e. the caller should
     * re-fire the alarm. Both the deadline alarm and the on-screen timer call this, hence the lock.
     */
    @Synchronized
    fun expire(alarmId: Long): Boolean {
        val s = state()?.takeIf { it.alarmId == alarmId && it.running } ?: return false
        if (System.currentTimeMillis() < s.deadline - 1_000) return false
        clear()
        dismissNotice()
        return true
    }

    /** Re-arm after a reboot. Returns the alarm to re-fire if a running check's deadline passed. */
    fun restoreAfterBoot(now: Long = System.currentTimeMillis()): Long? {
        val s = state() ?: return null
        return when {
            s.running && s.deadline <= now -> {
                clear()
                s.alarmId
            }
            s.running -> {
                scheduler.scheduleAwakeDeadline(s.alarmId, s.deadline)
                showPrompt(s.alarmId)
                null
            }
            else -> {
                scheduler.scheduleAwakeCheck(s.alarmId, maxOf(s.nextCheckAt, now + 5_000))
                null
            }
        }
    }

    private fun showPrompt(alarmId: Long) {
        val pi = PendingIntent.getActivity(
            context, 3, AwakeCheckActivity.intent(context, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("STILL WITH US, CHOOM?")
            .setContentText("Awake check. Solve it in ${WINDOW_MS / 1000}s or the alarm comes back.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF00E5FF.toInt())
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setTimeoutAfter(WINDOW_MS + 5_000)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_AWAKE, n) }
        // The full-screen intent only takes over a locked/off screen; with the overlay grant we may
        // also start the activity directly over whatever is in front.
        if (Settings.canDrawOverlays(context)) {
            runCatching { context.startActivity(AwakeCheckActivity.intent(context, alarmId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun dismissNotice() =
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_AWAKE)

    private fun cancelAlarms(alarmId: Long) {
        scheduler.cancelAwakeCheck(alarmId)
        scheduler.cancelAwakeDeadline(alarmId)
    }

    private fun save(s: State) = prefs.edit(commit = true) {
        putLong(KEY_ALARM, s.alarmId)
        putInt(KEY_STAGE, s.stage)
        putLong(KEY_DISMISSED, s.dismissedAt)
        putLong(KEY_NEXT, s.nextCheckAt)
        putLong(KEY_DEADLINE, s.deadline)
    }

    private fun clear() = prefs.edit(commit = true) { clear() }

    companion object {
        const val STAGES = 2
        const val WINDOW_MS = 90_000L
        val CHALLENGE = listOf(ChallengeSpec(ChallengeType.MATH, Difficulty.EASY, 2))

        /** Check n opens 5n minutes after the dismissal. */
        fun offsetFor(stage: Int): Long = stage * 5 * 60_000L

        private const val NONE = -1L
        private const val KEY_ALARM = "alarm_id"
        private const val KEY_STAGE = "stage"
        private const val KEY_DISMISSED = "dismissed_at"
        private const val KEY_NEXT = "next_check_at"
        private const val KEY_DEADLINE = "deadline"
    }
}
