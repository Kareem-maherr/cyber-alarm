package com.cyberalarm.ui.ringing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberalarm.alarm.AlarmController
import com.cyberalarm.alarm.AlarmReceiver
import com.cyberalarm.alarm.RingingStateStore
import com.cyberalarm.challenge.ChallengeSpec
import com.cyberalarm.challenge.ChallengeType
import com.cyberalarm.cred.WakeReport
import com.cyberalarm.data.Alarm
import com.cyberalarm.data.AlarmRepository
import com.cyberalarm.feedback.VoiceLines
import com.cyberalarm.settings.SettingsStore
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
    private val settings: SettingsStore,
    private val voice: VoiceLines,
) : ViewModel() {

    // Activity-scoped VMs receive the launching intent's extras in SavedStateHandle.
    private val alarmId: Long = savedState.get<Long>(AlarmReceiver.EXTRA_ALARM_ID) ?: store.activeAlarmId() ?: -1L

    private val _alarm = MutableStateFlow<Alarm?>(null)
    val alarm: StateFlow<Alarm?> = _alarm.asStateFlow()

    /** The chain actually demanded: the alarm's, made harder on an escalated re-ring. */
    private val _challenges = MutableStateFlow<List<ChallengeSpec>>(emptyList())
    val challenges: StateFlow<List<ChallengeSpec>> = _challenges.asStateFlow()

    val escalation: Int = controller.escalation()

    private val _snoozesLeft = MutableStateFlow(0)
    val snoozesLeft: StateFlow<Int> = _snoozesLeft.asStateFlow()

    private val _report = MutableStateFlow<WakeReport?>(null)
    val report: StateFlow<WakeReport?> = _report.asStateFlow()

    private var mistakes = 0
    private var busy = false

    init {
        viewModelScope.launch {
            // If the alarm was deleted mid-ring, still demand the default challenge.
            val a = repo.get(alarmId) ?: Alarm(id = alarmId, label = "ALARM", snoozeEnabled = false)
            _challenges.value = ChallengeSpec.escalate(a.challenges, escalation)
            _alarm.value = a
            _snoozesLeft.value = controller.snoozesLeft(a)
        }
    }

    fun onMistake() {
        mistakes++
    }

    /** Dismisses, then shows the Wake Report (the screen closes from there). */
    fun dismiss() {
        val a = _alarm.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val usedJackIn = _challenges.value.any { it.type == ChallengeType.JACK_IN }
            val report = controller.dismiss(a, mistakes, usedJackIn)
            _report.value = report
            if (settings.current.voiceLines) voice.speak(voice.lineFor(report))
        }
    }

    fun snooze(onDone: () -> Unit) {
        val a = _alarm.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            if (controller.snooze(a)) onDone() else busy = false
        }
    }
}
