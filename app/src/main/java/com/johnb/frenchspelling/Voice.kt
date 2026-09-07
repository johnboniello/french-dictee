package com.johnb.frenchspelling

import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

/** Shared text-to-speech helpers used by more than one screen. */
object Voice {

    private val RATE_LABELS = arrayOf("Très lente", "Lente", "Normale", "Assez rapide", "Rapide")
    private val RATE_VALUES = floatArrayOf(0.5f, 0.7f, 0.9f, 1.1f, 1.3f)

    fun rateDialog(activity: AppCompatActivity, store: WordStore, tts: TextToSpeech?) {
        val current = store.rate()
        var sel = RATE_VALUES.indexOfFirst { abs(it - current) < 0.01f }
        if (sel < 0) sel = 2
        AlertDialog.Builder(activity)
            .setTitle("Vitesse de la voix")
            .setSingleChoiceItems(RATE_LABELS, sel) { d, which ->
                store.setRate(RATE_VALUES[which])
                tts?.setSpeechRate(RATE_VALUES[which])
                tts?.speak("Voici la vitesse de la voix", TextToSpeech.QUEUE_FLUSH, null, "demo")
                d.dismiss()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    /** Says the whole word, then spells it out letter by letter with pauses. */
    fun spellSlowly(tts: TextToSpeech?, ready: Boolean, word: String, rate: Float) {
        val t = tts ?: return
        if (!ready || word.isBlank()) return
        t.setSpeechRate((rate - 0.2f).coerceAtLeast(0.4f))
        t.speak(word, TextToSpeech.QUEUE_FLUSH, null, "w")
        t.playSilentUtterance(400, TextToSpeech.QUEUE_ADD, "gap")
        for ((i, c) in word.withIndex()) {
            val name = when (c) {
                ' ' -> "espace"
                '-' -> "trait d'union"
                '\'' -> "apostrophe"
                else -> c.toString()
            }
            t.speak(name, TextToSpeech.QUEUE_ADD, null, "l$i")
            t.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "g$i")
        }
        t.setSpeechRate(rate)
    }
}
