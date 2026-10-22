package com.cyberalarm.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cyberalarm.MainActivity
import com.cyberalarm.R
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.lock.LockManager
import com.cyberalarm.settings.AppSettings
import com.cyberalarm.settings.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** The next alarm that will actually ring (skip / holiday aware), or null. */
data class UpcomingAlarm(val alarm: Alarm, val at: ZonedDateTime)

fun nextUpcoming(alarms: List<Alarm>, settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()): UpcomingAlarm? =
    alarms.filter { it.enabled }
        .map { UpcomingAlarm(it, NextTrigger.compute(it, now, settings.holidayUntil)) }
        .minByOrNull { it.at }

/**
 * Bedtime reminder: fires [AppSettings.sleepMinutes] before the next alarm. With night lock on it
 * also locks that alarm's apps until it rings, so doom-scrolling in bed gets the block screen.
 * Recomputed whenever alarms or settings change (see CyberAlarmApp).
 */
@Singleton
class BedtimeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun refresh(alarms: List<Alarm>) {
        val s = settings.current
        val pi = pending()
        val next = nextUpcoming(alarms, s)
        if (!s.bedtimeEnabled || next == null) {
            alarmManager.cancel(pi)
            return
        }
        val at = next.at.toInstant().toEpochMilli() - s.sleepMinutes * 60_000L
        if (at <= System.currentTimeMillis()) {
            alarmManager.cancel(pi) // already past bedtime for that alarm
            return
        }
        val exact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, BedtimeReceiver::class.java).setAction(BedtimeReceiver.ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

@AndroidEntryPoint
class BedtimeReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: AlarmRepository
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var lockManager: LockManager
    @Inject lateinit var bedtime: BedtimeScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = settings.current
                val alarms = repo.getAll()
                val next = nextUpcoming(alarms, s) ?: return@launch
                val at = next.at.toInstant().toEpochMilli()
                val locked = s.nightLock && next.alarm.lockedPackages.isNotEmpty()
                if (locked) lockManager.lockUntil(next.alarm.lockedPackages, at, next.alarm.id)
                notify(context, at, s.sleepMinutes, locked, next.alarm.lockedPackages.size)
                // The next bedtime is computed once that alarm has rung; the app-level sync does it.
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, alarmAt: Long, sleepMinutes: Int, locked: Boolean, apps: Int) {
        val time = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(alarmAt).atZone(ZoneId.systemDefault()))
        val pi = PendingIntent.getActivity(context, 4, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val hours = sleepMinutes / 60
        val mins = sleepMinutes % 60
        val goal = if (mins == 0) "${hours}H" else "${hours}H${mins}M"
        val text = buildString {
            append("Alarm at $time. Sleep now and you still get your $goal, choom.")
            if (locked) append(" Night lock on: $apps apps blocked till then.")
        }
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_BEDTIME)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("$goal TO WAKE // DELTA TO BED, CHOOM")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFF00E5FF.toInt())
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_BEDTIME, n) }
    }

    companion object {
        const val ACTION = "com.cyberalarm.action.BEDTIME"
    }
}
