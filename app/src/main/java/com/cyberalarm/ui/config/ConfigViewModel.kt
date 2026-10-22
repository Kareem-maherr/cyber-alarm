package com.cyberalarm.ui.config

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberalarm.cred.Rank
import com.cyberalarm.cred.WakeTracker
import com.cyberalarm.feedback.SoundPack
import com.cyberalarm.feedback.SoundSlot
import com.cyberalarm.feedback.VoiceLines
import com.cyberalarm.lingo.Lingo
import com.cyberalarm.settings.AppSettings
import com.cyberalarm.settings.BackupManager
import com.cyberalarm.settings.JackInStore
import com.cyberalarm.settings.JackInTag
import com.cyberalarm.settings.SettingsStore
import com.cyberalarm.settings.TagKind
import com.cyberalarm.wear.WearBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val jackIn: JackInStore,
    private val backup: BackupManager,
    private val voice: VoiceLines,
    private val wear: WearBridge,
    @ApplicationContext private val context: Context,
    tracker: WakeTracker,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsStore.state
    val tags: StateFlow<List<JackInTag>> = jackIn.tags
    val rank: StateFlow<Rank> = tracker.rank

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _watches = MutableStateFlow(0)
    val watches: StateFlow<Int> = _watches.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    /** Holiday runs until local midnight [days] days from today. */
    fun setHoliday(days: Int?) = settingsStore.update {
        it.copy(holidayUntil = days?.let { d -> holidayEnd(d) } ?: 0)
    }

    fun holidayDays(s: AppSettings): Int {
        if (!s.holidayActive()) return 7
        val end = java.time.Instant.ofEpochMilli(s.holidayUntil).atZone(ZoneId.systemDefault()).toLocalDate()
        return (end.toEpochDay() - LocalDate.now().toEpochDay()).toInt().coerceAtLeast(1)
    }

    private fun holidayEnd(days: Int): Long =
        LocalDate.now().plusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun register(kind: TagKind, raw: String): Boolean {
        val added = jackIn.add(kind, raw)
        _message.value = if (added) "NODE REGISTERED // ${jackIn.tags.value.size} TOTAL" else "ALREADY REGISTERED, CHOOM"
        return added
    }

    fun remove(tag: JackInTag) = jackIn.remove(tag)

    fun setSoundPack(pack: SoundPack) = settingsStore.update { it.copy(soundPack = pack.name) }

    /**
     * Copies the picked audio file into app storage (so it keeps working even if the original is
     * moved or the read grant expires) and uses it for [slot]. Max 1 MB: these are short blips.
     */
    fun setCustomSound(slot: SoundSlot, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val dir = File(context.filesDir, "ui_sounds").apply { mkdirs() }
                // New name each time so the sound pool reloads instead of reusing a cached path.
                val out = File(dir, "${slot.name}_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                if (out.length() !in 1..MAX_SOUND_BYTES) {
                    out.delete()
                    error("FILE TOO BIG // KEEP IT UNDER 1 MB")
                }
                dir.listFiles { f -> f.name.startsWith(slot.name) && f != out }?.forEach { it.delete() }
                settingsStore.update { it.withCustomSound(slot, out.absolutePath) }
            }
            _message.value = result.fold({ "${slot.title} SOUND LOADED" }, { it.message ?: "COULDN'T READ THAT FILE" })
        }
    }

    fun clearCustomSound(slot: SoundSlot) {
        settingsStore.current.customSound(slot).takeIf { it.isNotEmpty() }?.let { File(it).delete() }
        settingsStore.update { it.withCustomSound(slot, "") }
    }

    fun testVoice() = voice.speak(Lingo.pick(Lingo.voiceWake))

    fun refreshWatches() {
        viewModelScope.launch { _watches.value = wear.connectedWatches() }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching { "BACKUP SAVED // ${backup.export(uri)} ALARMS" }
                .getOrElse { "EXPORT FAILED // ${it.message}" }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching { "RESTORED // ${backup.import(uri)} ALARMS RE-ARMED" }
                .getOrElse { "IMPORT FAILED // ${it.message}" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val MAX_SOUND_BYTES = 1_048_576L
    }
}
