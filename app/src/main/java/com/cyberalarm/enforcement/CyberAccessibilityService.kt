package com.cyberalarm.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.lock.LockActivity
import com.cyberalarm.lock.LockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Watches which window comes to the front (package + class name only; never window content).
 *
 * While an alarm rings:
 *  - power menu (GlobalActions dialog) appears -> press Back to close it, re-show the alarm.
 *    Best effort: the dialog class differs by OEM and the hardware long-press-to-power-off
 *    (or force-reboot key combos) can't be intercepted at all.
 *  - any other app/launcher comes forward -> re-show the alarm.
 * Otherwise:
 *  - a locked app comes forward -> show the block screen ([LockActivity]).
 */
@AndroidEntryPoint
class CyberAccessibilityService : AccessibilityService() {

    @Inject lateinit var store: RingingStateStore
    @Inject lateinit var lockManager: LockManager

    private var lastRelaunch = 0L
    private var lastBlock = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()

        if (pkg != SYSTEM_UI && !isKeyboard(pkg)) lastForeground = pkg

        val ringingId = store.activeAlarmId()
        if (ringingId != null) {
            handleRinging(ringingId, pkg, cls)
            return
        }
        if (pkg == packageName) return
        if (lockManager.isLockedNow(pkg)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBlock < 800) return
            lastBlock = now
            startActivity(LockActivity.intent(this, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun handleRinging(alarmId: Long, pkg: String, cls: String) {
        if (isPowerMenu(pkg, cls)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            relaunchAlarm(alarmId)
            return
        }
        if (pkg == packageName || pkg == SYSTEM_UI || isKeyboard(pkg)) return
        relaunchAlarm(alarmId)
    }

    private fun isPowerMenu(pkg: String, cls: String): Boolean =
        (pkg == SYSTEM_UI || pkg == "android" || pkg.contains("globalactions")) &&
            POWER_MENU_HINTS.any { cls.contains(it, ignoreCase = true) }

    private fun isKeyboard(pkg: String): Boolean =
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == pkg }

    private fun relaunchAlarm(alarmId: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRelaunch < 500) return
        lastRelaunch = now
        startActivity(AlarmActivity.intent(this, alarmId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Package of the last app window that came to the front; read by AppLockService. */
        @Volatile
        var lastForeground: String? = null
            private set

        private const val SYSTEM_UI = "com.android.systemui"
        private val POWER_MENU_HINTS = listOf("GlobalActions", "PowerMenu", "ShutdownDialog", "PowerOptions")
    }
}
