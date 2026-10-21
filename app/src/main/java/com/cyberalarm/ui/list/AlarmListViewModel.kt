package com.cyberalarm.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberalarm.alarm.AlarmScheduler
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repo: AlarmRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    /** null until the first DB emission, so the UI can tell "loading" from "empty". */
    val alarms: StateFlow<List<Alarm>?> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(enabled = enabled)
            repo.save(updated)
            if (enabled) scheduler.schedule(updated) else scheduler.cancel(alarm.id)
        }
    }
}
