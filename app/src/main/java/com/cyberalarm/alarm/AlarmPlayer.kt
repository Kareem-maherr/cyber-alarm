package com.cyberalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.cyberalarm.data.BuiltinTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Loops the alarm sound on the ALARM stream with a volume ramp. Falls back from the user's track
 * to the default built-in tone, then to the system alarm sound, so something always plays.
 */
class AlarmPlayer(private val context: Context, private val scope: CoroutineScope) {

    private var player: MediaPlayer? = null
    private var rampJob: Job? = null

    /** [rampMillis] 0 = full volume immediately; [streamFloor] = minimum alarm-stream volume (0..1). */
    fun start(soundKey: String, rampMillis: Long = 30_000, streamFloor: Float = 0.8f) {
        stop()
        boostAlarmStream(streamFloor)
        val candidates = listOfNotNull(
            resolve(context, soundKey),
            builtinUri(context, BuiltinTone.NEON_PULSE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )
        for (uri in candidates) {
            val mp = runCatching { create(uri) }
                .onFailure { Log.w(TAG, "Cannot play $uri, falling back", it) }
                .getOrNull()
            if (mp != null) {
                player = mp
                break
            }
        }
        val mp = player ?: return
        if (rampMillis <= 0) {
            mp.setVolume(1f, 1f)
            mp.start()
            return
        }
        mp.setVolume(MIN_VOLUME, MIN_VOLUME)
        mp.start()
        rampJob = scope.launch {
            val steps = 30
            repeat(steps) { i ->
                delay(rampMillis / steps)
                val v = MIN_VOLUME + (1f - MIN_VOLUME) * (i + 1) / steps
                runCatching { mp.setVolume(v, v) }
            }
        }
    }

    /** Jack-in quiet window. The ramp keeps its place and continues on [resume]. */
    fun pause() {
        player?.let { runCatching { if (it.isPlaying) it.pause() } }
    }

    fun resume() {
        player?.let { runCatching { if (!it.isPlaying) it.start() } }
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun create(uri: Uri): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        try {
            setDataSource(context, uri)
            isLooping = true
            prepare()
        } catch (t: Throwable) {
            release()
            throw t
        }
    }

    /** Makes sure the alarm stream isn't muted/near-silent. May be refused under DND policies. */
    private fun boostAlarmStream(fraction: Float) {
        val am = context.getSystemService(AudioManager::class.java)
        runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val floor = (max * fraction).toInt()
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < floor) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
            }
        }
    }

    companion object {
        private const val TAG = "AlarmPlayer"
        private const val MIN_VOLUME = 0.12f

        fun builtinUri(context: Context, tone: BuiltinTone): Uri =
            Uri.parse("android.resource://${context.packageName}/${tone.resId}")

        fun resolve(context: Context, soundKey: String): Uri? =
            BuiltinTone.fromKey(soundKey)?.let { builtinUri(context, it) }
                ?: runCatching { Uri.parse(soundKey) }.getOrNull()
    }
}
