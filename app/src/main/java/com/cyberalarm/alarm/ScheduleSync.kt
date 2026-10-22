package com.cyberalarm.alarm

import android.content.Context
import com.cyberalarm.cred.CrewTheme
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.lock.LockManager
import com.cyberalarm.settings.SettingsStore
import com.cyberalarm.widget.NextAlarmWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything derived from "which alarm rings next": the bedtime reminder and the widget.
 * Called when alarms, settings or locks change, and after each ring.
 */
@Singleton
class ScheduleSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: AlarmRepository,
    private val bedtime: BedtimeScheduler,
    private val settings: SettingsStore,
    private val lockManager: LockManager,
) {
    suspend fun refresh() {
        val alarms = repo.getAll()
        bedtime.refresh(alarms)
        val s = settings.current
        val lockUntil = lockManager.activeWindows().maxOfOrNull { it.lockedUntil } ?: 0L
        runCatching { NextAlarmWidget.render(context, nextUpcoming(alarms, s), lockUntil, CrewTheme.of(s.theme)) }
    }
}
