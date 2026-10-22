package com.cyberalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.lock.AppLockService
import com.cyberalarm.lock.LockManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AlarmManager forgets everything on reboot, so re-arm from Room. Also the "escape via power-off"
 * mitigation: if an alarm was still ringing when the phone went down, it fires again right away.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var store: RingingStateStore
    @Inject lateinit var controller: AlarmController
    @Inject lateinit var lockManager: LockManager
    @Inject lateinit var awake: AwakeCheckManager
    @Inject lateinit var sync: ScheduleSync

    override fun onReceive(context: Context, intent: Intent) {
        val isBoot = intent.action in BOOT_ACTIONS
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.rescheduleAll(repo.getAll())
                runCatching { sync.refresh() }

                store.pendingSnooze()?.let { (id, until) ->
                    if (until > System.currentTimeMillis()) scheduler.scheduleSnooze(id, until)
                    else if (!store.isRinging() && isBoot) controller.startRinging(id)
                }

                // Foreground-service starts are only allowed from the boot/package-replaced
                // broadcasts, not from TIME_SET & co., hence the guard + runCatching.
                if (isBoot) {
                    // Rebooting during an awake check doesn't dodge it: a missed deadline rings.
                    awake.restoreAfterBoot()?.let { id -> if (!store.isRinging()) controller.reRing(id) }
                    store.activeAlarmId()?.let { id ->
                        runCatching { AlarmService.start(context, id) }
                            .onFailure { Log.w(TAG, "Could not resume ringing alarm", it) }
                    }
                    if (lockManager.hasActiveLocks()) {
                        runCatching { AppLockService.start(context) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
