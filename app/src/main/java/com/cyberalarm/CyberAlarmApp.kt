package com.cyberalarm

import android.app.Application
import com.cyberalarm.alarm.AlarmScheduler
import com.cyberalarm.alarm.Notifications
import com.cyberalarm.alarm.ScheduleSync
import com.cyberalarm.cred.CrewTheme
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.di.AppScope
import com.cyberalarm.lock.LockManager
import com.cyberalarm.settings.SettingsStore
import com.cyberalarm.ui.theme.Hud
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CyberAlarmApp : Application() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var repo: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var sync: ScheduleSync
    @Inject lateinit var lockManager: LockManager
    @Inject @field:AppScope lateinit var scope: CoroutineScope

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        Hud.theme = CrewTheme.of(settings.current.theme)

        scope.launch(Dispatchers.Main) {
            settings.state.map { it.theme }.distinctUntilChanged().collect { Hud.theme = CrewTheme.of(it) }
        }
        // Holiday mode moves every repeating alarm's next trigger.
        scope.launch {
            settings.state.map { it.holidayUntil }.distinctUntilChanged().drop(1).collect {
                scheduler.rescheduleAll(repo.getAll())
            }
        }
        // Bedtime reminder + widget follow alarms, settings and locks.
        scope.launch {
            combine(repo.observeAll(), settings.state, lockManager.windows) { _, _, _ -> }
                .debounce(500)
                .collect { runCatching { sync.refresh() } }
        }
    }
}
