package com.cyberalarm.feedback

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cyberalarm.cred.Rank
import com.cyberalarm.cred.WakeReport
import com.cyberalarm.lingo.Lingo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spoken fixer lines after a dismissal, via the system text-to-speech engine with a lowered pitch.
 * Media-volume level, not alarm level: it's a reward, not another alarm. The engine is created on
 * first use and shut down after each line so it doesn't sit in memory all day.
 */
@Singleton
class VoiceLines @Inject constructor(@ApplicationContext private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(text: String) {
        val engine = tts
        if (engine != null && ready) {
            say(engine, text)
            return
        }
        pending = text
        if (engine != null) return
        tts = TextToSpeech(context) { status ->
            val t = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                shutdown()
                return@TextToSpeech
            }
            ready = true
            t.language = Locale.US
            t.setPitch(0.72f)
            t.setSpeechRate(0.95f)
            t.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = shutdown()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = shutdown()
            })
            pending?.let { say(t, it) }
            pending = null
        }
    }

    /** Line for a Wake Report: a rank-up beats a streak beats a plain wake. */
    fun lineFor(report: WakeReport): String = when {
        report.rankUp != null -> "Rank up. You're a ${report.rankUp.title.lowercase()} now, choom. Preem."
        report.session.reRings > 0 -> Lingo.pick(Lingo.voiceAfterRelapse)
        report.streak >= 3 -> "${report.streak} clean wakes in a row. Night City's taking notice."
        report.rank == Rank.LEGEND -> "Another day, legend. The city's yours."
        else -> Lingo.pick(Lingo.voiceWake)
    }

    private fun say(engine: TextToSpeech, text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cyber-line")
    }

    private fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
