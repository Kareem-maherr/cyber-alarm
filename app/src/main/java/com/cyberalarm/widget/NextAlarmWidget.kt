package com.cyberalarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.cyberalarm.MainActivity
import com.cyberalarm.R
import com.cyberalarm.alarm.ScheduleSync
import com.cyberalarm.alarm.UpcomingAlarm
import com.cyberalarm.cred.CrewTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget: next alarm time with a live T- countdown, plus the app-lock timer while one
 * runs. Both countdowns are Chronometers, so the launcher ticks them without us waking up.
 */
class NextAlarmWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun sync(): ScheduleSync
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).sync().refresh()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun render(context: Context, next: UpcomingAlarm?, lockUntil: Long, theme: CrewTheme) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextAlarmWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
            val now = System.currentTimeMillis()
            val elapsedNow = SystemClock.elapsedRealtime()
            views.setTextColor(R.id.widget_time, theme.primary.toInt())
            views.setTextColor(R.id.widget_caption, theme.secondary.toInt())
            if (next != null) {
                val at = next.at.toInstant().toEpochMilli()
                views.setTextViewText(R.id.widget_caption, "NEXT // ${next.alarm.label.uppercase()} · ${next.at.dayOfWeek.name.take(3)}")
                views.setTextViewText(R.id.widget_time, "%02d:%02d".format(next.at.hour, next.at.minute))
                views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                views.setViewVisibility(R.id.widget_idle, View.GONE)
                views.setChronometer(R.id.widget_countdown, elapsedNow + (at - now), "T- %s", true)
                views.setChronometerCountDown(R.id.widget_countdown, true)
            } else {
                views.setTextViewText(R.id.widget_caption, "NEXT ALARM")
                views.setTextViewText(R.id.widget_time, "--:--")
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_idle, View.VISIBLE)
            }
            if (lockUntil > now) {
                views.setViewVisibility(R.id.widget_lock, View.VISIBLE)
                views.setChronometer(R.id.widget_lock, elapsedNow + (lockUntil - now), "APPS LOCKED // %s", true)
                views.setChronometerCountDown(R.id.widget_lock, true)
            } else {
                views.setViewVisibility(R.id.widget_lock, View.GONE)
            }
            val open = PendingIntent.getActivity(
                context, 5, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
