package com.cyberalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyberalarm.data.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives AlarmManager fires (regular, snooze and guard) and hands off to [AlarmController]. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var controller: AlarmController
    @Inject lateinit var store: RingingStateStore

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        val alarm = repo.get(alarmId) ?: return@launch
                        if (!alarm.enabled) return@launch
                        // Arm the next occurrence right away so a crash mid-ring can't lose it.
                        if (alarm.isRepeating) scheduler.schedule(alarm)
                        val ringing = store.activeAlarmId()
                        if (ringing != null && ringing != alarmId) return@launch // one alarm at a time
                        store.resetSnooze()
                        controller.startRinging(alarmId)
                    }
                    ACTION_SNOOZE_FIRE -> controller.startRinging(alarmId)
                    // Dead man's switch: only acts if that alarm is still un-dismissed.
                    ACTION_GUARD -> if (store.activeAlarmId() == alarmId) AlarmService.start(context, alarmId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.cyberalarm.action.FIRE"
        const val ACTION_SNOOZE_FIRE = "com.cyberalarm.action.SNOOZE_FIRE"
        const val ACTION_GUARD = "com.cyberalarm.action.GUARD"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
