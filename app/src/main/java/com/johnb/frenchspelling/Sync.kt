package com.johnb.frenchspelling

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Optional cross-device sync for the word list, keyed by a "family code" — no
 * accounts, no personal data. Talks to the Cloudflare Worker in `/worker`
 * (see `worker/README.md`). Everything runs on a background thread; results are
 * delivered on the main thread.
 *
 * To enable: deploy the worker and paste its URL into [SYNC_BASE_URL] below.
 */
object Sync {

    /** e.g. "https://dictee-sync.johnb.workers.dev" — no trailing slash. Empty = feature hidden. */
    const val SYNC_BASE_URL = ""

    val isConfigured: Boolean get() = SYNC_BASE_URL.isNotBlank()

    data class Remote(val words: List<String>, val updatedAt: Long)

    sealed class Result {
        /** Remote had no list yet (nothing synced under this code before). */
        data class Empty(val nothing: Boolean = true) : Result()
        data class Ok(val remote: Remote) : Result()
        data class Error(val message: String) : Result()
    }

    private val main = Handler(Looper.getMainLooper())

    private val ADJ = listOf(
        "bleu", "rouge", "vert", "jaune", "rose", "gris", "brun", "noir",
        "petit", "grand", "joli", "sage", "vif", "doux", "fier", "calme"
    )
    private val NOUN = listOf(
        "coq", "chat", "chien", "lion", "ours", "loup", "cerf", "pie",
        "pomme", "poire", "prune", "fleur", "arbre", "livre", "craie", "stylo"
    )

    /** A readable, hard-to-guess code like "coq-bleu-8241". */
    fun newCode(): String {
        val a = ADJ[Random.nextInt(ADJ.size)]
        val n = NOUN[Random.nextInt(NOUN.size)]
        val num = Random.nextInt(1000, 10000)
        return "$n-$a-$num"
    }

    fun normalizeCode(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9-]"), "")

    fun isValidCode(code: String): Boolean = Regex("^[a-z0-9-]{4,40}$").matches(code)

    fun pull(code: String, onResult: (Result) -> Unit) {
        Thread { deliver(onResult, doPull(code)) }.start()
    }

    private fun doPull(code: String): Result {
        val url = "$SYNC_BASE_URL/list/$code"
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
            }
            val status = c.responseCode
            when {
                status == 404 -> Result.Empty()
                status !in 200..299 -> Result.Error("Serveur : $status")
                else -> {
                    val body = c.inputStream.bufferedReader().use(BufferedReader::readText)
                    c.disconnect()
                    val obj = JSONObject(body)
                    if (!obj.has("words")) Result.Empty() else Result.Ok(parse(obj))
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Échec réseau")
        }
    }

    fun push(code: String, words: List<String>, updatedAt: Long, onResult: (Result) -> Unit) {
        Thread {
            val url = "$SYNC_BASE_URL/list/$code"
            try {
                val payload = JSONObject().apply {
                    put("words", JSONArray(words))
                    put("updatedAt", updatedAt)
                }.toString()
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 12000
                    readTimeout = 12000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                c.outputStream.use { it.write(payload.toByteArray()) }
                val status = c.responseCode
                if (status !in 200..299) return@Thread deliver(onResult, Result.Error("Serveur : $status"))
                val body = c.inputStream.bufferedReader().use(BufferedReader::readText)
                c.disconnect()
                deliver(onResult, Result.Ok(parse(JSONObject(body))))
            } catch (e: Exception) {
                deliver(onResult, Result.Error(e.message ?: "Échec réseau"))
            }
        }.start()
    }

    private fun parse(obj: JSONObject): Remote {
        val arr = obj.optJSONArray("words") ?: JSONArray()
        val words = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val w = arr.optString(i).trim()
            if (w.isNotEmpty()) words.add(w)
        }
        return Remote(words, obj.optLong("updatedAt", 0L))
    }

    private fun deliver(cb: (Result) -> Unit, r: Result) {
        main.post { cb(r) }
    }

    /** Union merge, case-insensitive, keeping [primary]'s order then new items from [other]. */
    fun merge(primary: List<String>, other: List<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>(primary.size + other.size)
        for (w in primary) if (seen.add(w.lowercase())) out.add(w)
        for (w in other) if (seen.add(w.lowercase())) out.add(w)
        return out
    }
}
