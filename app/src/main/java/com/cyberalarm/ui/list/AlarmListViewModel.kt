package com.cyberalarm.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberalarm.alarm.AlarmScheduler
import com.cyberalarm.alarm.NextTrigger
import com.cyberalarm.cred.Rank
import com.cyberalarm.cred.WakeTracker
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.settings.AppSettings
import com.cyberalarm.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repo: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val settingsStore: SettingsStore,
    tracker: WakeTracker,
) : ViewModel() {

    /** null until the first DB emission, so the UI can tell "loading" from "empty". */
    val alarms: StateFlow<List<Alarm>?> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings> = settingsStore.state
    val eddies: StateFlow<Int> = tracker.total
    val rank: StateFlow<Rank> = tracker.rank

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(enabled = enabled)
            repo.save(updated)
            if (enabled) scheduler.schedule(updated) else scheduler.cancel(alarm.id)
        }
    }

    /** "Skip next": skip exactly the upcoming occurrence of a repeating alarm (tap again to undo). */
    fun toggleSkip(alarm: Alarm) {
        viewModelScope.launch {
            val holiday = settingsStore.current.holidayUntil
            val now = ZonedDateTime.now()
            val skipAt = if (NextTrigger.isSkipping(alarm, now, holiday)) 0
            else NextTrigger.nextUnskipped(alarm, now, holiday).toInstant().toEpochMilli()
            val updated = alarm.copy(skipAt = skipAt)
            repo.save(updated)
            scheduler.schedule(updated)
        }
    }

    fun endHoliday() = settingsStore.update { it.copy(holidayUntil = 0) }
}
