package com.cyberalarm.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cyberalarm.R
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.enforcement.AlarmActivity
import com.cyberalarm.enforcement.OverlayManager
import com.cyberalarm.feedback.Haptics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that owns a ringing alarm: sound, vibration, wake lock, the full-screen
 * notification and the escape watchdog. It runs until [AlarmController.dismiss] / snooze clears
 * [RingingStateStore]; any other stop (kill, swipe, crash) arms a guard alarm that brings it back.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var repo: AlarmRepository
    @Inject lateinit var store: RingingStateStore
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var overlay: OverlayManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AlarmPlayer
    private var currentId = NO_ALARM
    private var watchdog: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastLaunch = 0L
    private var vibrateOn = false
    private var quiet = false

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this, scope)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must happen promptly after startForegroundService(), whatever comes next.
        val active = store.activeAlarmId()
        val inForeground = runCatching { startInForeground(buildNotification(active ?: NO_ALARM, "ALARM")) }.isSuccess
        if (!inForeground) {
            // e.g. a sticky restart from the background on Android 12+, where FGS starts are
            // restricted. onDestroy arms the guard alarm, whose fire *is* allowed to start us.
            stopSelf()
            return START_NOT_STICKY
        }
        if (active == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (active != currentId) begin(active) else if (!AlarmActivity.onScreen) launchAlarmUi(force = true)
        // START_STICKY: if the process is killed, the system restarts us with a null intent and
        // we resume from RingingStateStore.
        return START_STICKY
    }

    private fun begin(alarmId: Long) {
        currentId = alarmId
        acquireWakeLock()
        scope.launch {
            val alarm = withContext(Dispatchers.IO) { repo.get(alarmId) } ?: Alarm(id = alarmId, label = "ALARM")
            getSystemService(NotificationManager::class.java)
                .notify(Notifications.ID_RINGING, buildNotification(alarmId, alarm.label))
            // Escalated re-ring (fell asleep after an awake check): no ramp, louder, always vibrating.
            val escalated = store.escalation() > 0
            player.start(alarm.soundUri, rampMillis = if (escalated) 0 else 30_000, streamFloor = if (escalated) 1f else 0.8f)
            vibrateOn = alarm.vibrate || escalated
            if (vibrateOn) Haptics.get(this@AlarmService).startAlarmPattern()
            quiet = false
            applySilence()
        }
        launchAlarmUi(force = true)
        startWatchdog(alarmId)
    }

    /**
     * Escape detection. If the ringing activity isn't visible (home, recents, another app), cover the
     * screen with the overlay shield and relaunch the activity. Background activity starts are allowed
     * because we hold SYSTEM_ALERT_WINDOW; without it only the full-screen notification can help.
     */
    private fun startWatchdog(alarmId: Long) {
        watchdog?.cancel()
        watchdog = scope.launch {
            var tick = 0
            while (isActive) {
                if (store.activeAlarmId() == null) {
                    stopSelf()
                    break
                }
                applySilence()
                if (!AlarmActivity.onScreen) {
                    overlay.showAlarmShield()
                    launchAlarmUi(force = false)
                } else {
                    overlay.hide()
                }
                if (tick % 30 == 0) {
                    scheduler.scheduleGuard(alarmId, System.currentTimeMillis() + GUARD_LEAD_MS)
                    acquireWakeLock()
                }
                tick++
                delay(1_000)
            }
        }
    }

    /** Jack-in quiet window: pause sound + vibration while it lasts, resume when it runs out. */
    private fun applySilence() {
        val silenced = store.isSilenced()
        if (silenced == quiet) return
        quiet = silenced
        val haptics = Haptics.get(this)
        if (silenced) {
            player.pause()
            haptics.stopAlarmPattern()
        } else {
            player.resume()
            if (vibrateOn) haptics.startAlarmPattern()
        }
    }

    /**
     * Relaunching while a previous launch is still in flight (cold start after a process death can
     * take seconds) stacks keyguard-occlusion transitions and can wedge SystemUI on a black screen,
     * so non-forced relaunches wait [RELAUNCH_GAP_MS] since the last one.
     */
    private fun launchAlarmUi(force: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastLaunch < if (force) 1_000 else RELAUNCH_GAP_MS) return
        lastLaunch = now
        runCatching { startActivity(AlarmActivity.intent(this, currentId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun buildNotification(alarmId: Long, label: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, AlarmActivity.intent(this, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("ALARM // $label")
            .setContentText("Solve the challenge to dismiss.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFF0FF00.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
    }

    private fun startInForeground(notification: Notification) {
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else -> 0
        }
        ServiceCompat.startForeground(this, Notifications.ID_RINGING, notification, type)
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CyberAlarm:ringing")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(10 * 60_000L)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        store.activeAlarmId()?.let { scheduler.scheduleGuard(it, System.currentTimeMillis() + 1_000) }
    }

    override fun onDestroy() {
        watchdog?.cancel()
        player.stop()
        Haptics.get(this).stopAlarmPattern()
        overlay.hide()
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        // Stopped without a dismissal (killed by the OS, "stop" from a task manager, ...):
        // the guard alarm restarts ringing in a couple of seconds.
        store.activeAlarmId()?.let { scheduler.scheduleGuard(it, System.currentTimeMillis() + 2_000) }
        super.onDestroy()
    }

    companion object {
        private const val NO_ALARM = -1L
        private const val GUARD_LEAD_MS = 90_000L
        private const val RELAUNCH_GAP_MS = 4_000L

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
