package com.johnb.frenchspelling

import android.content.Context
import org.json.JSONArray

/** Persists the practice word list and voice settings in SharedPreferences. */
class WordStore(context: Context) {

    private val prefs = context.getSharedPreferences("french_spelling", Context.MODE_PRIVATE)

    fun words(): MutableList<String> {
        val raw = prefs.getString(KEY_WORDS, null) ?: return defaultWords()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            defaultWords()
        }
    }

    fun save(list: List<String>) {
        val arr = JSONArray()
        for (w in list) arr.put(w)
        prefs.edit().putString(KEY_WORDS, arr.toString()).apply()
    }

    fun rate(): Float = prefs.getFloat(KEY_RATE, 0.9f)

    fun setRate(r: Float) {
        prefs.edit().putFloat(KEY_RATE, r).apply()
    }

    /** Sample words shown on first launch, before the parent enters their own. */
    private fun defaultWords(): MutableList<String> = mutableListOf(
        "bonjour", "école", "maison", "chat", "fenêtre", "jeudi", "orange", "cahier"
    )

    companion object {
        private const val KEY_WORDS = "words"
        private const val KEY_RATE = "rate"
    }
}
