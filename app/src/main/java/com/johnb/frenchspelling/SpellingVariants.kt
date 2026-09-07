package com.johnb.frenchspelling

import kotlin.math.abs

/**
 * Generates plausible wrong spellings of a French word, for the multiple-choice
 * game. Uses the mistakes children actually make: accent swaps, doubled/undoubled
 * consonants, confusable endings, and adjacent-letter transpositions.
 */
object SpellingVariants {

    fun distractors(word: String, count: Int = 3): List<String> {
        val w = word.trim()
        if (w.length < 2) return fallback(w, count)

        val lower = w.lowercase()
        val out = LinkedHashSet<String>()

        val accentAlts = mapOf(
            'é' to "eè", 'è' to "eé", 'ê' to "eé", 'ë' to "e", 'e' to "éè",
            'à' to "a", 'â' to "a", 'a' to "à",
            'ç' to "c", 'c' to "ç",
            'ù' to "u", 'û' to "u", 'ü' to "u",
            'î' to "i", 'ï' to "i",
            'ô' to "o"
        )
        for ((i, ch) in w.withIndex()) {
            val alts = accentAlts[ch.lowercaseChar()] ?: continue
            for (r in alts) out.add(w.substring(0, i) + matchCase(ch, r) + w.substring(i + 1))
        }

        val doublable = "lmnprtsfcdgz"
        for (i in w.indices) {
            val c = w[i].lowercaseChar()
            if (c in doublable) out.add(w.substring(0, i + 1) + w[i] + w.substring(i + 1))
            if (i + 1 < w.length && c == w[i + 1].lowercaseChar() && c in doublable) {
                out.add(w.substring(0, i) + w.substring(i + 1))
            }
        }

        val endings = listOf(
            "er" to listOf("é", "ez", "ai"),
            "é" to listOf("er", "ée", "ai"),
            "ée" to listOf("é", "er"),
            "ez" to listOf("er", "é"),
            "eau" to listOf("au", "o"),
            "au" to listOf("eau", "o"),
            "tion" to listOf("sion", "cion"),
            "sion" to listOf("tion"),
            "ent" to listOf("ant", "ents"),
            "ai" to listOf("é", "è", "ei"),
            "ph" to listOf("f"),
            "s" to listOf("", "x"),
            "x" to listOf("s")
        )
        for ((suf, alts) in endings) {
            if (suf.isNotEmpty() && lower.endsWith(suf)) {
                for (a in alts) out.add(w.substring(0, w.length - suf.length) + a)
            }
        }

        for (i in 0 until w.length - 1) {
            if (w[i].lowercaseChar() != w[i + 1].lowercaseChar() && w[i] != ' ' && w[i + 1] != ' ') {
                val a = w.toCharArray()
                val t = a[i]; a[i] = a[i + 1]; a[i + 1] = t
                out.add(String(a))
            }
        }

        if (w.length > 3 && w.last().lowercaseChar() in "estx") out.add(w.dropLast(1))

        val vowelAlts = mapOf('a' to "e", 'e' to "a", 'i' to "y", 'o' to "au", 'u' to "ou", 'y' to "i")
        for ((i, ch) in w.withIndex()) {
            val alt = vowelAlts[ch.lowercaseChar()] ?: continue
            out.add(w.substring(0, i) + (if (ch.isUpperCase()) alt.replaceFirstChar { it.uppercase() } else alt) + w.substring(i + 1))
        }

        val cleaned = out
            .filter { it.isNotBlank() && !it.equals(w, ignoreCase = true) && it.length >= 2 && abs(it.length - w.length) <= 2 }
            .distinct()
            .toMutableList()
        cleaned.shuffle()

        val result = cleaned.take(count).toMutableList()
        var i = 0
        while (result.size < count && i < w.length - 1) {
            val a = w.toCharArray()
            val j = i + 1
            val t = a[i]; a[i] = a[j]; a[j] = t
            val cand = String(a)
            if (cand != w && cand !in result) result.add(cand)
            i++
        }
        while (result.size < count) result.add(w + "s".repeat(result.size + 1))
        return result.take(count)
    }

    private fun fallback(w: String, count: Int): List<String> =
        (1..count).map { w + "x".repeat(it) }

    private fun matchCase(orig: Char, repl: Char): Char =
        if (orig.isUpperCase()) repl.uppercaseChar() else repl
}
