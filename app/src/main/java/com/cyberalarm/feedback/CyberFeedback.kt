package com.cyberalarm.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.cyberalarm.R
import com.cyberalarm.settings.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

enum class SoundSlot(val title: String) { KEY("KEY CLICK"), ERROR("WRONG ANSWER"), OK("CORRECT") }

/**
 * Built-in UI sound sets. SYNTH = the procedurally generated originals; GLITCH and TERMINAL use
 * Kenney's "Interface Sounds" pack (CC0, kenney.nl; license in assets/licenses).
 */
enum class SoundPack(val title: String, val key: Int, val error: Int, val ok: Int) {
    SYNTH("SYNTH", R.raw.sfx_key, R.raw.sfx_error, R.raw.sfx_ok),
    GLITCH("GLITCH", R.raw.sfx_glitch_key, R.raw.sfx_glitch_error, R.raw.sfx_glitch_ok),
    TERMINAL("TERMINAL", R.raw.sfx_term_key, R.raw.sfx_term_error, R.raw.sfx_term_ok);

    fun res(slot: SoundSlot) = when (slot) {
        SoundSlot.KEY -> key
        SoundSlot.ERROR -> error
        SoundSlot.OK -> ok
    }

    companion object {
        fun of(name: String) = entries.find { it.name == name } ?: SYNTH
    }
}

/** Which sounds to load: a pack, with optional per-slot files the user picked (copied into app storage). */
data class UiSounds(val pack: SoundPack, val custom: Map<SoundSlot, String>)

/**
 * Sound + haptic feedback for challenge input. SoundPool keeps the short UI blips low-latency.
 * A custom file that fails to load falls back to the pack's sound for that slot.
 */
class CyberFeedback(context: Context, sounds: UiSounds = UiSounds(SoundPack.SYNTH, emptyMap())) {

    private val haptics = Haptics.get(context)
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids: Map<SoundSlot, Int> = SoundSlot.entries.associateWith { slot ->
        sounds.custom[slot]?.takeIf { File(it).isFile }?.let { path -> runCatching { pool.load(path, 1) }.getOrNull() }
            ?.takeIf { it > 0 }
            ?: pool.load(context, sounds.pack.res(slot), 1)
    }

    fun key() {
        play(SoundSlot.KEY, 0.9f, 1)
        haptics.tick()
    }

    fun error() {
        play(SoundSlot.ERROR, 1f, 2)
        haptics.error()
    }

    fun success() {
        play(SoundSlot.OK, 1f, 2)
        haptics.success()
    }

    fun preview(slot: SoundSlot) = when (slot) {
        SoundSlot.KEY -> key()
        SoundSlot.ERROR -> error()
        SoundSlot.OK -> success()
    }

    private fun play(slot: SoundSlot, volume: Float, priority: Int) {
        ids[slot]?.let { pool.play(it, volume, volume, priority, 0, 1f) }
    }

    fun release() = pool.release()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeedbackDeps {
    fun settings(): SettingsStore
}

/** Follows the sound settings: picking another pack or file reloads the pool. */
@Composable
fun rememberCyberFeedback(): CyberFeedback {
    val context = LocalContext.current
    val store = remember { EntryPointAccessors.fromApplication(context.applicationContext, FeedbackDeps::class.java).settings() }
    val settings by store.state.collectAsState()
    val sounds = settings.uiSounds()
    val feedback = remember(sounds) { CyberFeedback(context.applicationContext, sounds) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    return feedback
}
