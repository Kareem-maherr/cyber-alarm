package com.cyberalarm.feedback

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Single owner of the vibrator for the whole process.
 *
 * Why: while the alarm's repeating vibration runs, Android gives it priority and silently drops
 * short touch haptics from the same app. So every short pulse goes through here: it pauses the
 * alarm pattern, plays the pulse, then resumes the alarm pattern.
 */
class Haptics private constructor(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var alarmPatternOn = false
    private val resumeAlarm = Runnable { if (alarmPatternOn) vibrate(ALARM_PATTERN, alarm = true) }

    fun startAlarmPattern() {
        alarmPatternOn = true
        handler.removeCallbacks(resumeAlarm)
        vibrate(ALARM_PATTERN, alarm = true)
    }

    fun stopAlarmPattern() {
        alarmPatternOn = false
        handler.removeCallbacks(resumeAlarm)
        vibrator.cancel()
    }

    /** Crisp tick for a key press. */
    fun tick() = pulse(
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        else VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE),
        durationMs = 40,
    )

    /** Three hard buzzes: wrong answer. */
    fun error() = pulse(VibrationEffect.createWaveform(longArrayOf(0, 90, 60, 90, 60, 220), -1), durationMs = 520)

    /** Two light taps: correct answer. */
    fun success() = pulse(VibrationEffect.createWaveform(longArrayOf(0, 35, 45, 35), -1), durationMs = 115)

    /** Long double buzz with alarm usage (not muted by touch-feedback settings): awake-check nudge. */
    fun nudge() {
        if (!vibrator.hasVibrator() || alarmPatternOn) return
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1), alarm = true)
    }

    private fun pulse(effect: VibrationEffect, durationMs: Long) {
        if (!vibrator.hasVibrator()) return
        handler.removeCallbacks(resumeAlarm)
        // A repeating vibration makes the OS drop new requests ("ignored_for_ongoing"), even
        // alarm-usage ones, so the alarm pattern has to be cancelled explicitly first.
        if (alarmPatternOn) vibrator.cancel()
        vibrate(effect, alarm = alarmPatternOn)
        if (alarmPatternOn) handler.postDelayed(resumeAlarm, durationMs + 60)
    }

    private fun vibrate(effect: VibrationEffect, alarm: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                val usage = if (alarm) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_TOUCH
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(usage))
            } else {
                val usage = if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(usage).build())
            }
        }
    }

    companion object {
        private val ALARM_PATTERN = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)

        @Volatile
        private var instance: Haptics? = null

        fun get(context: Context): Haptics =
            instance ?: synchronized(this) { instance ?: Haptics(context.applicationContext).also { instance = it } }
    }
}
