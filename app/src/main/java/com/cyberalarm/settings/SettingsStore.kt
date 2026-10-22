package com.cyberalarm.settings

import android.content.Context
import androidx.core.content.edit
import com.cyberalarm.feedback.SoundPack
import com.cyberalarm.feedback.SoundSlot
import com.cyberalarm.feedback.UiSounds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide preferences (not per alarm). */
data class AppSettings(
    val bedtimeEnabled: Boolean = false,
    /** Sleep goal; bedtime = next alarm minus this. */
    val sleepMinutes: Int = 8 * 60,
    /** At bedtime, also lock the next alarm's locked apps until it rings. */
    val nightLock: Boolean = false,
    val voiceLines: Boolean = true,
    /** Repeating alarms stay silent until this epoch millis; 0 = off. */
    val holidayUntil: Long = 0,
    val theme: String = "SAMURAI",
    val watchLink: Boolean = true,
    /** UI sound pack ([SoundPack] name) and per-slot custom files (absolute paths, "" = pack sound). */
    val soundPack: String = "SYNTH",
    val soundKey: String = "",
    val soundError: String = "",
    val soundOk: String = "",
) {
    fun uiSounds() = UiSounds(
        SoundPack.of(soundPack),
        buildMap {
            if (soundKey.isNotEmpty()) put(SoundSlot.KEY, soundKey)
            if (soundError.isNotEmpty()) put(SoundSlot.ERROR, soundError)
            if (soundOk.isNotEmpty()) put(SoundSlot.OK, soundOk)
        },
    )

    fun customSound(slot: SoundSlot) = when (slot) {
        SoundSlot.KEY -> soundKey
        SoundSlot.ERROR -> soundError
        SoundSlot.OK -> soundOk
    }

    fun withCustomSound(slot: SoundSlot, path: String) = when (slot) {
        SoundSlot.KEY -> copy(soundKey = path)
        SoundSlot.ERROR -> copy(soundError = path)
        SoundSlot.OK -> copy(soundOk = path)
    }

    fun holidayActive(now: Long = System.currentTimeMillis()) = holidayUntil > now
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        prefs.edit(commit = true) {
            putBoolean("bedtime", next.bedtimeEnabled)
            putInt("sleep_minutes", next.sleepMinutes)
            putBoolean("night_lock", next.nightLock)
            putBoolean("voice", next.voiceLines)
            putLong("holiday_until", next.holidayUntil)
            putString("theme", next.theme)
            putBoolean("watch", next.watchLink)
            putString("sound_pack", next.soundPack)
            putString("sound_key", next.soundKey)
            putString("sound_error", next.soundError)
            putString("sound_ok", next.soundOk)
        }
        _state.value = next
    }

    private fun read() = AppSettings(
        bedtimeEnabled = prefs.getBoolean("bedtime", false),
        sleepMinutes = prefs.getInt("sleep_minutes", 8 * 60),
        nightLock = prefs.getBoolean("night_lock", false),
        voiceLines = prefs.getBoolean("voice", true),
        holidayUntil = prefs.getLong("holiday_until", 0),
        theme = prefs.getString("theme", "SAMURAI") ?: "SAMURAI",
        watchLink = prefs.getBoolean("watch", true),
        soundPack = prefs.getString("sound_pack", "SYNTH") ?: "SYNTH",
        soundKey = prefs.getString("sound_key", "") ?: "",
        soundError = prefs.getString("sound_error", "") ?: "",
        soundOk = prefs.getString("sound_ok", "") ?: "",
    )
}
