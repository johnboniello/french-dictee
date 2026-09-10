package com.johnb.frenchspelling

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Optional cross-device sync, keyed by a "family code" — no accounts, no
 * personal data. Syncs two things under the same code:
 *   /list/<code>   the current word list (+ a "new week" generation marker)
 *   /stats/<code>  the "Mots à revoir" practice record
 * Talks to the Cloudflare Worker in `/worker` (see `worker/README.md`).
 * Runs on a background thread; the result is delivered on the main thread.
 */
object Sync {

    /** No trailing slash. Empty = feature hidden. */
    const val SYNC_BASE_URL = "https://dictee-sync.johnboniello.workers.dev"

    val isConfigured: Boolean get() = SYNC_BASE_URL.isNotBlank()

    sealed class Result {
        data class Ok(val message: String) : Result()
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

    private const val ALNUM = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val secure = SecureRandom()

    /** A readable prefix so a parent recognises "their" code, plus 6 random
     *  chars so it isn't guessable: e.g. "coq-bleu-h7k2m9" (~40 bits). */
    fun newCode(): String {
        val a = ADJ[Random.nextInt(ADJ.size)]
        val n = NOUN[Random.nextInt(NOUN.size)]
        val rand = buildString { repeat(6) { append(ALNUM[secure.nextInt(ALNUM.length)]) } }
        return "$n-$a-$rand"
    }

    fun normalizeCode(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9-]"), "")

    fun isValidCode(code: String): Boolean = Regex("^[a-z0-9-]{8,40}$").matches(code)

    /** Pull + merge + push both list and stats. One background pass, one result. */
    fun syncAll(store: WordStore, code: String, onResult: (Result) -> Unit) {
        Thread {
            val r = try {
                val listMsg = syncList(store, code)
                val statsNote = syncStats(store, code)
                store.markSyncedNow()
                Result.Ok("À jour : $listMsg." + (if (statsNote != null) " $statsNote" else ""))
            } catch (e: Exception) {
                Result.Error(e.message ?: "Échec réseau")
            }
            main.post { onResult(r) }
        }.start()
    }

    // ---- list ----

    private fun syncList(store: WordStore, code: String): String {
        val now = System.currentTimeMillis()
        val local = store.words()
        val localRep = store.wordsReplacedAt()

        val remote = httpGet("$SYNC_BASE_URL/list/$code")
        if (remote == null || !remote.has("words")) {
            httpPut("$SYNC_BASE_URL/list/$code", JSONObject().apply {
                put("words", JSONArray(local))
                put("updatedAt", now)
                put("replacedAt", localRep)
            })
            return "envoyé ${local.size} mot(s)"
        }

        val remoteWords = jsonToList(remote.optJSONArray("words"))
        val remoteRep = remote.optLong("replacedAt", 0L)

        val merged: List<String>
        val replacedAt: Long
        var adopted = false
        when {
            remoteRep > localRep -> {        // other device started a new week
                merged = remoteWords
                replacedAt = remoteRep
                adopted = true
            }
            localRep > remoteRep -> {        // this device started a new week
                merged = local
                replacedAt = localRep
            }
            else -> {                        // same generation -> union
                merged = union(local, remoteWords)
                replacedAt = localRep
            }
        }

        store.saveFromSync(merged, now, replacedAt)
        httpPut("$SYNC_BASE_URL/list/$code", JSONObject().apply {
            put("words", JSONArray(merged))
            put("updatedAt", now)
            put("replacedAt", replacedAt)
        })

        if (adopted) return "nouvelle liste : ${merged.size} mot(s)"
        val received = merged.count { w -> local.none { it.equals(w, ignoreCase = true) } }
        return if (received > 0) "${merged.size} mot(s) (+$received reçu(s))" else "${merged.size} mot(s)"
    }

    // ---- stats ----

    private fun syncStats(store: WordStore, code: String): String? {
        val now = System.currentTimeMillis()
        val local = store.stats()

        val remote = try {
            httpGet("$SYNC_BASE_URL/stats/$code")
        } catch (e: Exception) {
            return "mots à revoir non synchronisés (serveur à mettre à jour)"
        }
        val remoteStats = if (remote != null && remote.has("stats"))
            Stats.fromJson(remote.getJSONObject("stats").toString())
        else
            mutableMapOf()

        val merged = Stats.merge(local, remoteStats)
        store.saveStatsFromSync(merged, now)

        return try {
            httpPut("$SYNC_BASE_URL/stats/$code", JSONObject().apply {
                put("stats", JSONObject(Stats.toJson(merged)))
                put("updatedAt", now)
            })
            null
        } catch (e: Exception) {
            "mots à revoir fusionnés (envoi à réessayer)"
        }
    }

    // ---- helpers ----

    private fun union(primary: List<String>, other: List<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>(primary.size + other.size)
        for (w in primary) if (seen.add(w.lowercase())) out.add(w)
        for (w in other) if (seen.add(w.lowercase())) out.add(w)
        return out
    }

    private fun jsonToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }

    /** Blocking GET. Returns null on 404, throws on other failures. */
    private fun httpGet(url: String): JSONObject? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
        }
        try {
            val status = c.responseCode
            if (status == 404) return null
            if (status !in 200..299) throw RuntimeException("Serveur : $status")
            val body = c.inputStream.bufferedReader().use(BufferedReader::readText)
            return JSONObject(body)
        } finally {
            c.disconnect()
        }
    }

    /** Blocking PUT. Throws on non-2xx. */
    private fun httpPut(url: String, body: JSONObject) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = c.responseCode
            if (status !in 200..299) throw RuntimeException("Serveur : $status")
            c.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            c.disconnect()
        }
    }
}
