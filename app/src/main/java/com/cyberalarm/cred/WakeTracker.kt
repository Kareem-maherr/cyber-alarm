package com.cyberalarm.cred

import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.CredDao
import com.cyberalarm.data.CredEntry
import com.cyberalarm.data.WakeDao
import com.cyberalarm.data.WakeSession
import com.cyberalarm.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** What the post-dismissal Wake Report shows. */
data class WakeReport(
    val session: WakeSession,
    val lines: List<Pair<Int, String>>,
    val earned: Int,
    val total: Int,
    val streak: Int,
    val rank: Rank,
    /** Set when this wake pushed the balance into a new rank. */
    val rankUp: Rank?,
)

/** Records wake sessions and keeps the Street Cred ledger. */
@Singleton
class WakeTracker @Inject constructor(
    private val wakeDao: WakeDao,
    private val credDao: CredDao,
    private val ringing: RingingStateStore,
    @AppScope scope: CoroutineScope,
) {
    val total: StateFlow<Int> = credDao.observeTotal().stateIn(scope, SharingStarted.Eagerly, 0)
    val rank: StateFlow<Rank> = total.map { Rank.of(it) }.stateIn(scope, SharingStarted.Eagerly, Rank.STREET_KID)
    val sessions: Flow<List<WakeSession>> = wakeDao.observeRecent()
    val claimedGigs: Flow<Set<String>> = credDao.observeGigs().map { it.toSet() }

    /** A fresh occurrence started ringing. */
    suspend fun startSession(alarm: Alarm) {
        val id = wakeDao.insert(WakeSession(alarmId = alarm.id, label = alarm.label, firedAt = System.currentTimeMillis()))
        ringing.setSessionId(id)
    }

    suspend fun onSnooze() = mutate { it.copy(snoozes = it.snoozes + 1) }

    /** An awake check was missed and the alarm re-fired at [level]. */
    suspend fun onReRing(level: Int) {
        val s = mutate { it.copy(reRings = it.reRings + 1, maxEscalation = maxOf(it.maxEscalation, level)) } ?: return
        award(Scoring.CHECK_FAILED, "AWAKE CHECK FAILED", s.id)
    }

    suspend fun onCheckPassed() {
        val s = mutate { it.copy(checksPassed = it.checksPassed + 1) } ?: return
        award(Scoring.CHECK_PASSED, "AWAKE CHECK", s.id)
    }

    suspend fun onDismiss(alarm: Alarm, mistakes: Int, usedJackIn: Boolean): WakeReport {
        val now = System.currentTimeMillis()
        val before = credDao.total()
        var session = ringing.sessionId()?.let { wakeDao.get(it) }
            ?: WakeSession(alarmId = alarm.id, label = alarm.label, firedAt = ringing.startedAt() ?: now)
                .let { it.copy(id = wakeDao.insert(it)) }
        session = session.copy(
            dismissedAt = session.dismissedAt.takeIf { it > 0 } ?: now,
            mistakes = session.mistakes + mistakes,
            usedJackIn = session.usedJackIn || usedJackIn,
        )
        wakeDao.update(session)
        val history = wakeDao.getAll().map { if (it.id == session.id) session else it }
        val streak = if (session.clean) Scoring.streak(history) else 0
        val lines = Scoring.dismissal(session, streak)
        lines.forEach { (amount, reason) -> credDao.insert(CredEntry(at = now, amount = amount, reason = reason, sessionId = session.id)) }
        val earned = lines.sumOf { it.first }
        session = session.copy(eddies = credDao.sessionTotal(session.id))
        wakeDao.update(session)
        val after = before + earned
        val rankBefore = Rank.of(before)
        val rankAfter = Rank.of(after)
        return WakeReport(session, lines, earned, after, streak, rankAfter, rankAfter.takeIf { it.ordinal > rankBefore.ordinal })
    }

    /** Pays out today's gig once. Returns false if it was already claimed. */
    suspend fun claimGig(gig: Gig): Boolean {
        if (credDao.countReason(gig.id) > 0) return false
        credDao.insert(CredEntry(at = System.currentTimeMillis(), amount = gig.reward, reason = gig.id))
        return true
    }

    private suspend fun award(amount: Int, reason: String, sessionId: Long) {
        credDao.insert(CredEntry(at = System.currentTimeMillis(), amount = amount, reason = reason, sessionId = sessionId))
        wakeDao.get(sessionId)?.let { wakeDao.update(it.copy(eddies = credDao.sessionTotal(sessionId))) }
    }

    private suspend fun mutate(transform: (WakeSession) -> WakeSession): WakeSession? {
        val s = ringing.sessionId()?.let { wakeDao.get(it) } ?: return null
        return transform(s).also { wakeDao.update(it) }
    }
}
