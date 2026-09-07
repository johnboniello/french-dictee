package com.johnb.frenchspelling

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech

/**
 * Plays the sound of a single letter as it is entered.
 *
 * If a recording exists at `assets/lettersounds/<key>.mp3` it is played (this is
 * how you get real phonics — drop clips into that folder). Otherwise the French
 * text-to-speech voice says the letter's name.
 *
 * Asset key mapping (so accented letters get valid filenames):
 *   a..z      -> a.mp3 .. z.mp3
 *   é è ê ë   -> e_aigu / e_grave / e_circ / e_trema .mp3
 *   à â       -> a_grave / a_circ .mp3
 *   î ï       -> i_circ / i_trema .mp3
 *   ô         -> o_circ.mp3
 *   û ù ü     -> u_circ / u_grave / u_trema .mp3
 *   ç         -> c_cedille.mp3
 *   œ         -> oe.mp3
 *   -  '  ' ' -> trait / apostrophe / espace .mp3
 */
object LetterAudio {

    private var player: MediaPlayer? = null

    fun keyFor(ch: Char): String = when (ch.lowercaseChar()) {
        'é' -> "e_aigu"; 'è' -> "e_grave"; 'ê' -> "e_circ"; 'ë' -> "e_trema"
        'à' -> "a_grave"; 'â' -> "a_circ"
        'î' -> "i_circ"; 'ï' -> "i_trema"
        'ô' -> "o_circ"
        'û' -> "u_circ"; 'ù' -> "u_grave"; 'ü' -> "u_trema"
        'ç' -> "c_cedille"; 'œ' -> "oe"
        '-' -> "trait"; '\'' -> "apostrophe"; ' ' -> "espace"
        else -> ch.lowercaseChar().toString()
    }

    private fun nameFor(ch: Char): String = when (ch) {
        ' ' -> "espace"
        '-' -> "trait d'union"
        '\'' -> "apostrophe"
        else -> ch.toString()
    }

    fun hasClip(context: Context, ch: Char): Boolean = try {
        context.assets.open("lettersounds/${keyFor(ch)}.mp3").close(); true
    } catch (e: Exception) {
        false
    }

    fun play(context: Context, tts: TextToSpeech?, ttsReady: Boolean, ch: Char) {
        if (hasClip(context, ch)) {
            try {
                player?.release()
                player = null
                val afd = context.assets.openFd("lettersounds/${keyFor(ch)}.mp3")
                val mp = MediaPlayer()
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mp.setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                }
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                // fall through to the voice
            }
        }
        if (tts != null && ttsReady) {
            tts.speak(nameFor(ch), TextToSpeech.QUEUE_FLUSH, null, "letter")
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
