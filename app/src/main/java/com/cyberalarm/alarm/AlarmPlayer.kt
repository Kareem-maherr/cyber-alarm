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

    fun start(soundKey: String, rampMillis: Long = 30_000) {
        stop()
        boostAlarmStream()
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
    private fun boostAlarmStream() {
        val am = context.getSystemService(AudioManager::class.java)
        runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val floor = (max * 0.8f).toInt()
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
