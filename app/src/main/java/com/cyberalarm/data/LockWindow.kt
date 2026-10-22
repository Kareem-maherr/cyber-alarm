package com.cyberalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app that is locked until [lockedUntil] (wall-clock millis). Solving the block-screen challenge
 * grants a short pass: the app is usable until [passUntil] while the lock window keeps running.
 * Persisted in Room so locks survive process death and reboot.
 */
@Entity(tableName = "lock_windows")
data class LockWindow(
    @PrimaryKey val packageName: String,
    val lockedUntil: Long,
    val passUntil: Long = 0,
    val sourceAlarmId: Long = 0,
    /** When this lock window started; drives the release progress bar. 0 = unknown (pre-v2 rows). */
    val lockedAt: Long = 0,
) {
    fun isLocked(now: Long): Boolean = lockedUntil > now && passUntil <= now
}
