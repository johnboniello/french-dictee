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

    /** Local edit: save and stamp "now" so sync knows this device is ahead. */
    fun save(list: List<String>) {
        writeWords(list, System.currentTimeMillis())
    }

    /** Sync adopted a list from the server: save it with the server's timestamp. */
    fun saveFromSync(list: List<String>, updatedAt: Long) {
        writeWords(list, updatedAt)
        prefs.edit().putLong(KEY_SYNCED_AT, System.currentTimeMillis()).apply()
    }

    private fun writeWords(list: List<String>, updatedAt: Long) {
        val arr = JSONArray()
        for (w in list) arr.put(w)
        prefs.edit()
            .putString(KEY_WORDS, arr.toString())
            .putLong(KEY_WORDS_AT, updatedAt)
            .apply()
    }

    /** When the local list was last changed (epoch millis). 0 if never. */
    fun wordsUpdatedAt(): Long = prefs.getLong(KEY_WORDS_AT, 0L)

    /** When this device last completed a sync (epoch millis). 0 if never. */
    fun lastSyncedAt(): Long = prefs.getLong(KEY_SYNCED_AT, 0L)

    fun markSyncedNow() {
        prefs.edit().putLong(KEY_SYNCED_AT, System.currentTimeMillis()).apply()
    }

    fun familyCode(): String = prefs.getString(KEY_CODE, "") ?: ""

    fun setFamilyCode(code: String) {
        prefs.edit().putString(KEY_CODE, code).apply()
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
        private const val KEY_WORDS_AT = "words_updated_at"
        private const val KEY_SYNCED_AT = "last_synced_at"
        private const val KEY_CODE = "family_code"
        private const val KEY_RATE = "rate"
    }
}
