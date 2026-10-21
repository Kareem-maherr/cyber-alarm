package com.cyberalarm.lock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cyberalarm.MainActivity
import com.cyberalarm.R
import com.cyberalarm.alarm.Notifications
import com.cyberalarm.enforcement.CyberAccessibilityService
import com.cyberalarm.ui.permissions.Permissions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the app lock alive while any lock window is active: shows a countdown notification and,
 * when the accessibility service is off, polls usage stats to catch locked apps (fallback path;
 * launching the block screen from the background additionally needs the overlay permission).
 * Stops itself when the last window expires.
 */
@AndroidEntryPoint
class AppLockService : Service() {

    @Inject lateinit var lockManager: LockManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private lateinit var detector: ForegroundAppDetector

    override fun onCreate() {
        super.onCreate()
        detector = ForegroundAppDetector(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = runCatching {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, Notifications.ID_LOCK, buildNotification(0, 0), type)
        }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { watchLoop() }
        return START_STICKY
    }

    private suspend fun watchLoop() {
        var tick = 0
        var lastBlocked: String? = null
        while (scope.isActive) {
            if (tick % 15 == 0) {
                lockManager.purgeExpired()
                val active = lockManager.activeWindows()
                if (active.isEmpty()) {
                    stopSelf()
                    return
                }
                val remaining = active.maxOf { it.lockedUntil } - System.currentTimeMillis()
                getSystemService(NotificationManager::class.java)
                    .notify(Notifications.ID_LOCK, buildNotification(active.size, remaining))
            }
            // Also catches a pass expiring while the user stays inside the app.
            val fg = when {
                CyberAccessibilityService.isRunning -> CyberAccessibilityService.lastForeground
                Permissions.hasUsageAccess(this) -> detector.currentPackage()
                else -> null
            }
            if (fg != null && fg != packageName && lockManager.isLockedNow(fg)) {
                if (fg != lastBlocked) {
                    runCatching { startActivity(LockActivity.intent(this, fg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                lastBlocked = fg
            } else if (fg != null) {
                lastBlocked = null
            }
            tick++
            delay(1_000)
        }
    }

    private fun buildNotification(apps: Int, remainingMs: Long): Notification {
        val mins = (remainingMs / 60_000).coerceAtLeast(0)
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("STAY_AWAKE PROTOCOL // $apps APPS LOCKED")
            .setContentText("Lock releases in %d:%02d".format(mins / 60, mins % 60))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, AppLockService::class.java))
    }
}
