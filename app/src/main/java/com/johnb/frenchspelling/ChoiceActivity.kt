package com.johnb.frenchspelling

import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/** "Le bon mot" — hear the word, pick the correct spelling out of four. */
class ChoiceActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var words: MutableList<String> = mutableListOf()
    private var order: MutableList<Int> = mutableListOf()
    private var pos = 0
    private var score = 0
    private var aided = 0

    private lateinit var progressView: TextView
    private lateinit var instructionView: TextView
    private lateinit var emptyView: TextView
    private lateinit var listenBtn: Button
    private lateinit var feedbackView: TextView
    private lateinit var nextBtn: Button
    private val options = ArrayList<Button>(4)

    private var correctIndex = -1
    private var solved = false
    private var wrongThisWord = false

    private val optIdle = 0xFFB5D4F4.toInt()
    private val optIdleText = 0xFF042C53.toInt()
    private val green = 0xFF2E7D32.toInt()
    private val red = 0xFFC62828.toInt()
    private val onStrong = 0xFFFFFFFF.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choice)
        supportActionBar?.title = "Le bon mot"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        store = WordStore(this)
        progressView = findViewById(R.id.progressView)
        instructionView = findViewById(R.id.instructionView)
        emptyView = findViewById(R.id.emptyView)
        listenBtn = findViewById(R.id.listenBtn)
        feedbackView = findViewById(R.id.feedbackView)
        nextBtn = findViewById(R.id.nextBtn)
        options.add(findViewById(R.id.opt0))
        options.add(findViewById(R.id.opt1))
        options.add(findViewById(R.id.opt2))
        options.add(findViewById(R.id.opt3))

        listenBtn.setOnClickListener { speakWord() }
        nextBtn.setOnClickListener { nextWord() }
        options.forEachIndexed { i, b -> b.setOnClickListener { onPick(i) } }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.FRANCE)
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (ttsReady) runOnUiThread { if (order.isNotEmpty() && !solved) speakWord() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val latest = store.words()
        if (latest != words || order.isEmpty()) {
            words = latest
            if (words.isEmpty()) {
                showEmpty(true)
                order = mutableListOf()
                return
            }
            showEmpty(false)
            order = MutableList(words.size) { it }
            order.shuffle()
            pos = 0
            score = 0
            aided = 0
            setupRound()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        Feedback.release()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showEmpty(empty: Boolean) {
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        instructionView.visibility = if (empty) View.GONE else View.VISIBLE
        for (b in options) b.visibility = if (empty) View.GONE else View.VISIBLE
        listenBtn.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            progressView.text = ""
            feedbackView.text = ""
            nextBtn.visibility = View.GONE
        }
    }

    private fun currentWord(): String = words[order[pos]]

    private fun setupRound() {
        solved = false
        wrongThisWord = false
        feedbackView.text = ""
        nextBtn.visibility = View.GONE

        val target = currentWord()
        val choices = (SpellingVariants.distractors(target, 3) + target).distinct().toMutableList()
        while (choices.size < 4) choices.add(target + "s".repeat(choices.size))
        choices.shuffle()
        correctIndex = choices.indexOf(target)
        if (correctIndex < 0) {
            choices[0] = target
            correctIndex = 0
        }

        for (i in options.indices) {
            options[i].text = choices[i]
            options[i].isEnabled = true
            options[i].backgroundTintList = ColorStateList.valueOf(optIdle)
            options[i].setTextColor(optIdleText)
        }
        progressView.text = "Mot ${pos + 1} / ${order.size}     Score : $score" +
            (if (aided > 0) "   ·   avec aide : $aided" else "")
        if (ttsReady) speakWord()
    }

    private fun onPick(i: Int) {
        if (solved || !options[i].isEnabled) return
        if (i == correctIndex) {
            solved = true
            options[i].backgroundTintList = ColorStateList.valueOf(green)
            options[i].setTextColor(onStrong)
            for (b in options) b.isEnabled = false
            feedbackView.setTextColor(green)
            feedbackView.text = if (wrongThisWord) "Bravo ! (avec aide)" else "Bravo ! 🎉"
            if (wrongThisWord) aided++ else score++
            progressView.text = "Mot ${pos + 1} / ${order.size}     Score : $score" +
                (if (aided > 0) "   ·   avec aide : $aided" else "")
            Feedback.correct(this, tts, ttsReady)
            Celebrate.correct(this)
            nextBtn.visibility = View.VISIBLE
        } else {
            wrongThisWord = true
            options[i].isEnabled = false
            options[i].backgroundTintList = ColorStateList.valueOf(red)
            options[i].setTextColor(onStrong)
            feedbackView.setTextColor(red)
            feedbackView.text = "Essaie encore."
            Feedback.wrong(this, tts, ttsReady)
            Celebrate.reset()
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
}
