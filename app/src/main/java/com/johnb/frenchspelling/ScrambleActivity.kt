package com.johnb.frenchspelling

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.abs

/**
 * "Lettres mélangées" — the word is spoken, its letters appear scrambled as tiles,
 * and the child drags each tile into its slot. Every letter plays its sound as it
 * lands (recording from assets/lettersounds if present, otherwise the voice).
 */
class ScrambleActivity : AppCompatActivity() {

    private class Tile(val view: TextView, val letter: Char) {
        var homeX = 0f
        var homeY = 0f
        var slotIndex = -1
        var locked = false
    }

    private lateinit var store: WordStore
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var words: MutableList<String> = mutableListOf()
    private var order: MutableList<Int> = mutableListOf()
    private var pos = 0
    private var score = 0
    private var aided = 0

    private lateinit var progressView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var board: FrameLayout
    private lateinit var nextBtn: Button

    private var slotChars: List<Char> = emptyList()
    private val slotRects = ArrayList<Rect>()
    private val slotFilledBy = ArrayList<Tile?>()
    private val tiles = ArrayList<Tile>()
    private var tileSize = 0
    private var gap = 0
    private var hintUsedThisWord = false
    private var solvedThisWord = false

    private var dragTile: Tile? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f

    private val green = 0xFF2E7D32.toInt()
    private val red = 0xFFC62828.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scramble)
        supportActionBar?.title = "Lettres mélangées"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        store = WordStore(this)
        progressView = findViewById(R.id.progressView)
        feedbackView = findViewById(R.id.feedbackView)
        board = findViewById(R.id.board)
        nextBtn = findViewById(R.id.nextBtn)
        gap = dp(8)

        findViewById<Button>(R.id.listenBtn).setOnClickListener { speakWord() }
        findViewById<Button>(R.id.hintBtn).setOnClickListener { revealNext() }
        findViewById<Button>(R.id.spellBtn).setOnClickListener {
            if (order.isNotEmpty()) Voice.spellSlowly(tts, ttsReady, currentWord(), store.rate())
        }
        nextBtn.setOnClickListener { nextWord() }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.FRANCE)
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (ttsReady) runOnUiThread { if (order.isNotEmpty() && !solvedThisWord) speakWord() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val latest = store.words()
        if (latest != words || order.isEmpty()) {
            words = latest
            if (words.isEmpty()) {
                board.removeAllViews()
                progressView.text = ""
                feedbackView.text = "Ajoute d'abord des mots (« Gérer les mots »)."
                nextBtn.visibility = View.GONE
                order = mutableListOf()
                return
            }
            order = MutableList(words.size) { it }
            order.shuffle()
            pos = 0
            score = 0
            aided = 0
            board.post { setupRound() }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        LetterAudio.release()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun currentWord(): String = words[order[pos]]

    private fun progressText(): String =
        "Mot ${pos + 1} / ${order.size}     Score : $score" +
            (if (aided > 0) "   ·   avec aide : $aided" else "")

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRound() {
        board.removeAllViews()
        slotRects.clear()
        slotFilledBy.clear()
        tiles.clear()
        feedbackView.text = ""
        nextBtn.visibility = View.GONE
        hintUsedThisWord = false
        solvedThisWord = false

        val target = currentWord()
        slotChars = target.filter { it != ' ' }.toList()
        val n = slotChars.size
        if (n == 0) {
            nextWord()
            return
        }

        val boardW = if (board.width > 0) board.width else resources.displayMetrics.widthPixels
        tileSize = ((boardW - gap * 9) / 8).coerceIn(dp(38), dp(60))

        val slotPos = flow(n, boardW, dp(8))
        for (i in 0 until n) {
            val v = View(this)
            v.setBackgroundResource(R.drawable.slot)
            val lp = FrameLayout.LayoutParams(tileSize, tileSize)
            lp.leftMargin = slotPos[i].first
            lp.topMargin = slotPos[i].second
            board.addView(v, lp)
            slotRects.add(
                Rect(
                    slotPos[i].first,
                    slotPos[i].second,
                    slotPos[i].first + tileSize,
                    slotPos[i].second + tileSize
                )
            )
            slotFilledBy.add(null)
        }

        val slotsBottom = (slotPos.maxOfOrNull { it.second } ?: dp(8)) + tileSize
        val trayTop = slotsBottom + dp(28)

        val letters = slotChars.toMutableList()
        if (n > 1 && letters.toSet().size > 1) {
            var guard = 0
            do {
                letters.shuffle()
                guard++
            } while (letters == slotChars && guard < 20)
        }

        val trayPos = flow(n, boardW, trayTop)
        for (i in 0 until n) {
            val tv = makeTileView(letters[i])
            val lp = FrameLayout.LayoutParams(tileSize, tileSize)
            board.addView(tv, lp)
            val tile = Tile(tv, letters[i])
            tile.homeX = trayPos[i].first.toFloat()
            tile.homeY = trayPos[i].second.toFloat()
            tv.x = tile.homeX
            tv.y = tile.homeY
            tv.tag = tile
            tv.setOnTouchListener(touchListener)
            tiles.add(tile)
        }

        progressView.text = progressText()
        if (ttsReady) speakWord()
    }

    private fun makeTileView(letter: Char): TextView {
        val tv = TextView(this)
        tv.text = letter.toString()
        tv.gravity = Gravity.CENTER
        tv.textSize = 22f
        tv.setTextColor(0xFF1A1A1A.toInt())
        tv.setBackgroundResource(R.drawable.tile)
        tv.elevation = dp(2).toFloat()
        tv.isFocusable = false
        return tv
    }

    private val touchListener = View.OnTouchListener { v, e ->
        val tile = v.tag as? Tile ?: return@OnTouchListener false
        if (tile.locked || solvedThisWord) return@OnTouchListener false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragTile = tile
                downX = e.rawX
                downY = e.rawY
                startX = v.x
                startY = v.y
                v.bringToFront()
                v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(80).start()
                if (tile.slotIndex >= 0) {
                    slotFilledBy[tile.slotIndex] = null
                    tile.slotIndex = -1
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                v.x = startX + (e.rawX - downX)
                v.y = startY + (e.rawY - downY)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                dropTile(tile)
                dragTile = null
                true
            }
            else -> false
        }
    }

    private fun dropTile(tile: Tile) {
        val cx = tile.view.x + tileSize / 2f
        val cy = tile.view.y + tileSize / 2f
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in slotRects.indices) {
            if (slotFilledBy[i] != null) continue
            val r = slotRects[i]
            val d = abs(cx - r.exactCenterX()) + abs(cy - r.exactCenterY())
            if (d < bestD && d < tileSize * 1.4f) {
                bestD = d
                best = i
            }
        }
        if (best >= 0) {
            val r = slotRects[best]
            tile.slotIndex = best
            slotFilledBy[best] = tile
            animateTo(tile.view, r.left.toFloat(), r.top.toFloat())
            LetterAudio.play(this, tts, ttsReady, tile.letter)
            if (slotFilledBy.all { it != null }) board.postDelayed({ checkSolution() }, 240)
        } else {
            animateTo(tile.view, tile.homeX, tile.homeY)
        }
    }

    private fun revealNext() {
        if (order.isEmpty() || solvedThisWord) return
        val k = slotFilledBy.indexOfFirst { it == null }
        if (k < 0) return
        val want = slotChars[k]
        val tile = tiles.firstOrNull {
            !it.locked && sameLetter(it.letter, want) &&
                (it.slotIndex < 0 || !sameLetter(it.letter, slotChars[it.slotIndex]))
        } ?: tiles.firstOrNull { !it.locked && sameLetter(it.letter, want) } ?: return

        if (tile.slotIndex >= 0) {
            slotFilledBy[tile.slotIndex] = null
        }
        tile.slotIndex = k
        slotFilledBy[k] = tile
        tile.locked = true
        tile.view.setBackgroundResource(R.drawable.tile_correct)
        val r = slotRects[k]
        animateTo(tile.view, r.left.toFloat(), r.top.toFloat())
        LetterAudio.play(this, tts, ttsReady, want)
        hintUsedThisWord = true
        if (slotFilledBy.all { it != null }) board.postDelayed({ checkSolution() }, 240)
    }

    private fun checkSolution() {
        if (solvedThisWord) return
        val allRight = slotChars.indices.all { i ->
            val placed = slotFilledBy[i]?.letter
            placed != null && sameLetter(placed, slotChars[i])
        }
        if (allRight) {
            solvedThisWord = true
            for (t in tiles) {
                t.view.setBackgroundResource(R.drawable.tile_correct)
                t.locked = true
            }
            feedbackView.setTextColor(green)
            feedbackView.text = if (hintUsedThisWord) "Bravo ! (avec aide)" else "Bravo ! 🎉"
            if (hintUsedThisWord) aided++ else score++
            progressView.text = progressText()
            if (ttsReady) {
                tts?.setSpeechRate(store.rate())
                tts?.speak(currentWord(), TextToSpeech.QUEUE_FLUSH, null, "w")
            }
            nextBtn.visibility = View.VISIBLE
        } else {
            feedbackView.setTextColor(red)
            feedbackView.text = "Pas tout à fait — les lettres en rouge reviennent."
            for (i in slotChars.indices) {
                val t = slotFilledBy[i] ?: continue
                if (!sameLetter(t.letter, slotChars[i])) {
                    slotFilledBy[i] = null
                    t.slotIndex = -1
                    t.view.setBackgroundResource(R.drawable.tile_wrong)
                    animateTo(t.view, t.homeX, t.homeY)
                    t.view.postDelayed({
                        if (t.slotIndex < 0) t.view.setBackgroundResource(R.drawable.tile)
                    }, 700)
                }
            }
        }
    }

    private fun nextWord() {
        if (pos + 1 >= order.size) {
            AlertDialog.Builder(this)
                .setTitle("Terminé !")
                .setMessage("Sans aide : $score / ${order.size}\nAvec aide : $aided")
                .setPositiveButton("Recommencer") { _, _ ->
                    order.shuffle()
                    pos = 0
                    score = 0
                    aided = 0
                    setupRound()
                }
                .setNegativeButton("Retour", null)
                .setCancelable(false)
                .show()
            return
        }
        pos++
        setupRound()
    }

    private fun speakWord() {
        val t = tts ?: return
        if (!ttsReady || order.isEmpty()) return
        t.setSpeechRate(store.rate())
        t.speak(currentWord(), TextToSpeech.QUEUE_FLUSH, null, "w")
    }

    private fun sameLetter(a: Char, b: Char) = a.lowercaseChar() == b.lowercaseChar()

    private fun animateTo(v: View, x: Float, y: Float) {
        v.animate().x(x).y(y).setDuration(140).start()
    }

    /** Positions `count` square cells of `tileSize`, centred per row, wrapping to fit `boardW`. */
    private fun flow(count: Int, boardW: Int, startY: Int): List<Pair<Int, Int>> {
        val per = maxOf(1, (boardW + gap) / (tileSize + gap))
        val res = ArrayList<Pair<Int, Int>>(count)
        var i = 0
        while (i < count) {
            val row = i / per
            val inRow = minOf(per, count - row * per)
            val rowW = inRow * tileSize + (inRow - 1) * gap
            val x0 = ((boardW - rowW) / 2).coerceAtLeast(0)
            for (c in 0 until inRow) {
                res.add(Pair(x0 + c * (tileSize + gap), startY + row * (tileSize + gap)))
                i++
            }
        }
        return res
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
