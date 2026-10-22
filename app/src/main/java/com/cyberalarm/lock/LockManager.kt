package com.cyberalarm.lock

import android.content.Context
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.challenge.Difficulty
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.LockDao
import com.cyberalarm.data.LockWindow
import com.cyberalarm.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Post-alarm app lock ("stay awake" mode).
 *
 * Policy: after an alarm is dismissed, each of its selected apps is locked for `alarm.lockMinutes`.
 * Opening a locked app shows the block screen. Solving the block-screen challenge proves you're
 * awake and grants a [PASS_MINUTES] pass for *that app only*; the lock window keeps running and
 * the app locks again when the pass expires.
 */
@Singleton
class LockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LockDao,
    @AppScope scope: CoroutineScope,
) {
    /** In-memory mirror of the table, so the accessibility service can check synchronously. */
    val windows: StateFlow<Map<String, LockWindow>> = dao.observeAll()
        .map { list -> list.associateBy { it.packageName } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun isLockedNow(pkg: String, now: Long = System.currentTimeMillis()): Boolean =
        windows.value[pkg]?.isLocked(now) == true

    suspend fun startLocks(alarm: Alarm) {
        if (alarm.lockMinutes <= 0) return
        lockUntil(alarm.lockedPackages, System.currentTimeMillis() + alarm.lockMinutes * 60_000L, alarm.id)
    }

    /** Night lock: [packages] stay locked from bedtime until the alarm rings at [until]. */
    suspend fun lockUntil(packages: List<String>, until: Long, sourceAlarmId: Long) {
        if (packages.isEmpty()) return
        val now = System.currentTimeMillis()
        packages.forEach { pkg ->
            val existing = dao.get(pkg)?.takeIf { it.lockedUntil > now }
            dao.upsert(
                LockWindow(
                    packageName = pkg,
                    lockedUntil = maxOf(existing?.lockedUntil ?: 0, until),
                    passUntil = 0,
                    sourceAlarmId = sourceAlarmId,
                    lockedAt = existing?.lockedAt?.takeIf { it > 0 } ?: now,
                ),
            )
        }
        runCatching { AppLockService.start(context) }
    }

    suspend fun grantPass(pkg: String) {
        val window = dao.get(pkg) ?: return
        dao.upsert(window.copy(passUntil = System.currentTimeMillis() + PASS_MINUTES * 60_000L))
    }

    suspend fun purgeExpired() = dao.deleteExpired(System.currentTimeMillis())

    suspend fun activeWindows(): List<LockWindow> {
        val now = System.currentTimeMillis()
        return dao.getAll().filter { it.lockedUntil > now }
    }

    suspend fun hasActiveLocks(): Boolean = activeWindows().isNotEmpty()

    companion object {
        const val PASS_MINUTES = 10
        val UNLOCK_CHALLENGE = listOf(ChallengeSpec(ChallengeType.MATH, Difficulty.MEDIUM, 3))
    }
}
