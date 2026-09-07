package com.johnb.frenchspelling

/**
 * Turns a speech-recognition hypothesis into a candidate spelling.
 *
 * A child dictating a word aloud may:
 *  - name each letter ("bé", "o", "enne", "ji"...) -> we map the names to letters;
 *  - describe an accent ("e accent aigu" -> "é");
 *  - or just say the whole word, and the recognizer returns it as one token
 *    ("bonjour") -> we keep the token's letters as-is.
 *
 * This is best-effort. The on-screen letter keyboard is the reliable path;
 * whatever this produces can always be corrected there before checking.
 */
object LetterParser {

    private val NAMES: Map<String, String> = buildMap {
        // Plain letters. Only spellings that are unambiguous enough to trust.
        put("a", "a")
        put("bé", "b")
        put("cé", "c")
        put("dé", "d")
        put("e", "e")
        put("effe", "f"); put("èfe", "f"); put("ef", "f")
        put("gé", "g")
        put("ache", "h"); put("hache", "h")
        put("i", "i")
        put("ji", "j")
        put("ka", "k"); put("k", "k")
        put("elle", "l"); put("èl", "l")
        put("emme", "m"); put("èm", "m")
        put("enne", "n"); put("èn", "n")
        put("o", "o")
        put("pé", "p")
        put("ku", "q"); put("qu", "q")
        put("erre", "r"); put("ère", "r")
        put("esse", "s"); put("ès", "s")
        put("té", "t")
        put("u", "u")
        put("vé", "v")
        put("double vé", "w"); put("doublevé", "w"); put("double v", "w"); put("w", "w")
        put("iks", "x"); put("ixe", "x"); put("ix", "x")
        put("i grec", "y"); put("igrec", "y"); put("y grec", "y")
        put("zède", "z"); put("zed", "z")

        // Accented and special characters, usually spoken as a description.
        put("é", "é"); put("e accent aigu", "é"); put("accent aigu", "é")
        put("è", "è"); put("e accent grave", "è")
        put("ê", "ê"); put("e accent circonflexe", "ê"); put("e circonflexe", "ê")
        put("ë", "ë"); put("e tréma", "ë")
        put("à", "à"); put("a accent grave", "à")
        put("â", "â"); put("a accent circonflexe", "â"); put("a circonflexe", "â")
        put("î", "î"); put("i accent circonflexe", "î"); put("i circonflexe", "î")
        put("ï", "ï"); put("i tréma", "ï")
        put("ô", "ô"); put("o accent circonflexe", "ô"); put("o circonflexe", "ô")
        put("û", "û"); put("u accent circonflexe", "û")
        put("ù", "ù"); put("u accent grave", "ù")
        put("ü", "ü"); put("u tréma", "ü")
        put("ç", "ç"); put("c cédille", "ç"); put("cédille", "ç"); put("sé cédille", "ç")
        put("œ", "œ"); put("o e collés", "œ"); put("e dans l'o", "œ")
        put("trait d'union", "-"); put("trait d union", "-"); put("tiret", "-")
        put("apostrophe", "'")
        put("espace", " ")
    }

    private const val MAX_SPAN = 4

    fun parse(hypothesis: String): String {
        var s = hypothesis.lowercase()
        s = s.replace('’', '\'')                       // curly apostrophe -> straight
        s = s.replace(Regex("[.,;:!?\"()\\[\\]]"), " ")
        s = s.replace("-", " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return ""

        val tokens = s.split(" ")
        val out = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            var matched = false
            var span = minOf(MAX_SPAN, tokens.size - i)
            while (span >= 1) {
                val phrase = tokens.subList(i, i + span).joinToString(" ")
                val letter = NAMES[phrase]
                if (letter != null) {
                    out.append(letter)
                    i += span
                    matched = true
                    break
                }
                span--
            }
            if (!matched) {
                // Unknown token: a single stray character, or a whole word the
                // recognizer returned instead of letters. Keep its characters.
                out.append(tokens[i])
                i++
            }
        }
        return out.toString()
    }
}
