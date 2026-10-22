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
import com.cyberalarm.data.LockWindow
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
            ServiceCompat.startForeground(this, Notifications.ID_LOCK, buildNotification(emptyList()), type)
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
                getSystemService(NotificationManager::class.java)
                    .notify(Notifications.ID_LOCK, buildNotification(active))
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

    /**
     * Countdown to the last lock releasing, as an Android 16 "Live Update" (promoted ongoing
     * notification): a chronometer counting down plus a progress bar. Promoted notifications get the
     * status-bar chip and, on Samsung One UI 8, a card in the Now Bar. Older Android versions just
     * show a normal ongoing notification with the same content.
     */
    private fun buildNotification(active: List<LockWindow>): Notification {
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_LOCK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(0xFFF0FF00.toInt())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setRequestPromotedOngoing(true)
        if (active.isEmpty()) return builder.setContentTitle("STAY_AWAKE PROTOCOL").build()

        val now = System.currentTimeMillis()
        val until = active.maxOf { it.lockedUntil }
        val from = active.mapNotNull { w -> w.lockedAt.takeIf { it > 0 } }.minOrNull() ?: now
        val total = (until - from).coerceAtLeast(1)
        val progress = (((now - from).toFloat() / total) * PROGRESS_MAX).toInt().coerceIn(0, PROGRESS_MAX)
        val time = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()))
        val apps = if (active.size == 1) "1 APP" else "${active.size} APPS"
        return builder
            .setContentTitle("$apps LOCKED // STAY FROSTY, CHOOM")
            .setContentText("Lock releases at $time")
            .setWhen(until)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setStyle(
                NotificationCompat.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX).setColor(0xFFF0FF00.toInt())))
                    .setProgress(progress),
            )
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PROGRESS_MAX = 1_000

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, AppLockService::class.java))
    }
}
