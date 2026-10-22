package com.cyberalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One wake-up: from the first ring of an alarm occurrence through its snoozes, dismissal and the
 * awake checks after it. Re-rings caused by a failed awake check stay in the same session.
 */
@Entity(tableName = "wake_sessions")
data class WakeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val label: String,
    val firedAt: Long,
    /** First dismissal; 0 while still ringing. */
    val dismissedAt: Long = 0,
    val mistakes: Int = 0,
    val snoozes: Int = 0,
    /** Failed awake checks (each one re-fired the alarm). */
    val reRings: Int = 0,
    val checksPassed: Int = 0,
    val maxEscalation: Int = 0,
    val usedJackIn: Boolean = false,
    /** Net eddies this session earned (sum of its cred entries). */
    val eddies: Int = 0,
) {
    val dismissed: Boolean get() = dismissedAt > 0

    /** No snoozes and no failed awake checks. */
    val clean: Boolean get() = dismissed && snoozes == 0 && reRings == 0

    val secondsToWake: Long get() = if (dismissed) (dismissedAt - firedAt) / 1000 else 0
}

/** Street Cred ledger. The eddie balance is the sum of [amount]. */
@Entity(tableName = "cred_log")
data class CredEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val amount: Int,
    val reason: String,
    val sessionId: Long = 0,
)
