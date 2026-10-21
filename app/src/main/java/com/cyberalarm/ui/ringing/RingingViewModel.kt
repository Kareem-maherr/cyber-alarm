package com.cyberalarm.ui.ringing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberalarm.alarm.AlarmController
import com.cyberalarm.alarm.AlarmReceiver
import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RingingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    store: RingingStateStore,
    private val repo: AlarmRepository,
    private val controller: AlarmController,
) : ViewModel() {

    // Activity-scoped VMs receive the launching intent's extras in SavedStateHandle.
    private val alarmId: Long = savedState.get<Long>(AlarmReceiver.EXTRA_ALARM_ID) ?: store.activeAlarmId() ?: -1L

    private val _alarm = MutableStateFlow<Alarm?>(null)
    val alarm: StateFlow<Alarm?> = _alarm.asStateFlow()

    private val _snoozesLeft = MutableStateFlow(0)
    val snoozesLeft: StateFlow<Int> = _snoozesLeft.asStateFlow()

    private var busy = false

    init {
        viewModelScope.launch {
            // If the alarm was deleted mid-ring, still demand the default challenge.
            val a = repo.get(alarmId) ?: Alarm(id = alarmId, label = "ALARM", snoozeEnabled = false)
            _alarm.value = a
            _snoozesLeft.value = controller.snoozesLeft(a)
        }
    }

    fun dismiss(onDone: () -> Unit) {
        val a = _alarm.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            controller.dismiss(a)
            onDone()
        }
    }

    fun snooze(onDone: () -> Unit) {
        val a = _alarm.value ?: return
        if (busy) return
        if (controller.snooze(a)) {
            busy = true
            onDone()
        }
    }
}
