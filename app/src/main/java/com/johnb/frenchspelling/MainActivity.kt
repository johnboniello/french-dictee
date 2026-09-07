package com.johnb.frenchspelling

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private var words: MutableList<String> = mutableListOf()
    private var order: MutableList<Int> = mutableListOf()
    private var pos = 0
    private var score = 0
    private var aidedCount = 0
    private val guess = StringBuilder()
    private var scoredThisWord = false
    private var aidedThisWord = false
    private var revealCount = 0

    private lateinit var emptyView: TextView
    private lateinit var emptyAddBtn: Button
    private lateinit var practiceBox: View
    private lateinit var progressView: TextView
    private lateinit var listenBtn: Button
    private lateinit var repeatBtn: Button
    private lateinit var hintBtn: Button
    private lateinit var spellBtn: Button
    private lateinit var hintView: TextView
    private lateinit var guessView: TextView
    private lateinit var letterGrid: GridLayout
    private lateinit var backspaceBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var micBtn: Button
    private lateinit var checkBtn: Button
    private lateinit var heardView: TextView
    private lateinit var resultBox: View
    private lateinit var targetView: TextView
    private lateinit var yourView: TextView
    private lateinit var summaryView: TextView
    private lateinit var hearAnswerBtn: Button
    private lateinit var retryBtn: Button
    private lateinit var nextBtn: Button

    private val green = 0xFF2E7D32.toInt()
    private val red = 0xFFC62828.toInt()
    private val grey = 0xFF9E9E9E.toInt()

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else toast("Micro refusé : utilise le clavier de lettres.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = "Dictée sur papier"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        store = WordStore(this)

        emptyView = findViewById(R.id.emptyView)
        emptyAddBtn = findViewById(R.id.emptyAddBtn)
        practiceBox = findViewById(R.id.practiceBox)
        progressView = findViewById(R.id.progressView)
        listenBtn = findViewById(R.id.listenBtn)
        repeatBtn = findViewById(R.id.repeatBtn)
        hintBtn = findViewById(R.id.hintBtn)
        spellBtn = findViewById(R.id.spellBtn)
        hintView = findViewById(R.id.hintView)
        guessView = findViewById(R.id.guessView)
        letterGrid = findViewById(R.id.letterGrid)
        backspaceBtn = findViewById(R.id.backspaceBtn)
        clearBtn = findViewById(R.id.clearBtn)
        micBtn = findViewById(R.id.micBtn)
        checkBtn = findViewById(R.id.checkBtn)
        heardView = findViewById(R.id.heardView)
        resultBox = findViewById(R.id.resultBox)
        targetView = findViewById(R.id.targetView)
        yourView = findViewById(R.id.yourView)
        summaryView = findViewById(R.id.summaryView)
        hearAnswerBtn = findViewById(R.id.hearAnswerBtn)
        retryBtn = findViewById(R.id.retryBtn)
        nextBtn = findViewById(R.id.nextBtn)

        buildLetterKeys()

        emptyAddBtn.setOnClickListener { openWordList() }
        listenBtn.setOnClickListener { speakWord() }
        repeatBtn.setOnClickListener { speakWord() }
        backspaceBtn.setOnClickListener {
            if (guess.isNotEmpty()) {
                guess.deleteCharAt(guess.length - 1)
                refreshGuess()
            }
        }
        clearBtn.setOnClickListener {
            guess.clear()
            heardView.visibility = View.GONE
            refreshGuess()
        }
        micBtn.setOnClickListener { onMicTapped() }
        checkBtn.setOnClickListener { onCheck() }
        retryBtn.setOnClickListener {
            guess.clear()
            heardView.visibility = View.GONE
            resultBox.visibility = View.GONE
            scoredThisWord = false
            refreshGuess()
        }
        nextBtn.setOnClickListener { nextWord() }
        hearAnswerBtn.setOnClickListener { speakSpelledOut() }
        hintBtn.setOnClickListener { revealHint() }
        spellBtn.setOnClickListener {
            if (words.isNotEmpty()) Voice.spellSlowly(tts, ttsReady, currentWord(), store.rate())
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.FRANCE)
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) runOnUiThread {
                    toast("Voix française absente. Installe-la dans Paramètres > Système > Langues > Synthèse vocale.")
                }
            } else {
                runOnUiThread { toast("Synthèse vocale indisponible sur cet appareil.") }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val latest = store.words()
        if (latest != words || order.isEmpty()) {
            words = latest
            initRound()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        recognizer?.destroy()
        LetterAudio.release()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_words -> { openWordList(); true }
        R.id.action_rate -> { Voice.rateDialog(this, store, tts); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openWordList() {
        startActivity(Intent(this, WordListActivity::class.java))
    }

    private fun initRound() {
        guess.clear()
        heardView.visibility = View.GONE
        resultBox.visibility = View.GONE
        scoredThisWord = false
        aidedThisWord = false
        revealCount = 0
        pos = 0
        score = 0
        aidedCount = 0

        if (words.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyAddBtn.visibility = View.VISIBLE
            practiceBox.visibility = View.GONE
            return
        }
        emptyView.visibility = View.GONE
        emptyAddBtn.visibility = View.GONE
        practiceBox.visibility = View.VISIBLE

        order = MutableList(words.size) { it }
        order.shuffle()
        render()
    }

    private fun currentWord(): String = words[order[pos]]

    private fun render() {
        revealCount = 0
        aidedThisWord = false
        progressView.text = progressText()
        refreshGuess()
        updateHintView()
        resultBox.visibility = View.GONE
    }

    private fun progressText(): String =
        "Mot ${pos + 1} / ${order.size}     Score : $score" +
            (if (aidedCount > 0) "   ·   avec aide : $aidedCount" else "")

    private fun refreshGuess() {
        guessView.text = if (guess.isEmpty()) "— — —" else spaced(guess.toString())
    }

    private fun spaced(s: String): String = s.toCharArray().joinToString("  ")

    private fun revealHint() {
        if (words.isEmpty()) return
        val w = currentWord()
        if (revealCount < w.length) revealCount++
        aidedThisWord = true
        updateHintView()
    }

    private fun updateHintView() {
        if (revealCount <= 0 || words.isEmpty()) {
            hintView.visibility = View.GONE
            return
        }
        val w = currentWord()
        val shown = buildString {
            for ((i, c) in w.withIndex()) {
                append(if (i < revealCount) c else '_')
                append(' ')
            }
        }
        hintView.text = "Indice : ${shown.trimEnd()}"
        hintView.visibility = View.VISIBLE
    }

    private fun buildLetterKeys() {
        val keys = ('a'..'z').map { it.toString() } +
            listOf("é", "è", "ê", "ë", "à", "â", "î", "ï", "ô", "û", "ù", "ü", "ç", "œ", "'", "-", " ")
        val cols = 7
        letterGrid.columnCount = cols
        val m = dp(3)
        for (k in keys) {
            val isSpace = k == " "
            val b = Button(this)
            b.text = if (isSpace) "espace" else k
            b.isAllCaps = false
            b.textSize = if (isSpace) 13f else 18f
            b.setPadding(0, dp(6), 0, dp(6))
            b.minWidth = 0
            b.minimumWidth = 0
            val span = if (isSpace) 2 else 1
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, span, span.toFloat())
            lp.setMargins(m, m, m, m)
            b.layoutParams = lp
            b.setOnClickListener {
                guess.append(k)
                heardView.visibility = View.GONE
                refreshGuess()
                LetterAudio.play(this, tts, ttsReady, k[0])
            }
            letterGrid.addView(b)
        }
    }

    private fun speakWord() {
        val t = tts
        if (t == null || !ttsReady) {
            toast("La voix n'est pas encore prête.")
            return
        }
        t.setSpeechRate(store.rate())
        t.speak(currentWord(), TextToSpeech.QUEUE_FLUSH, null, "word")
    }

    private fun speakSpelledOut() {
        val t = tts ?: return
        if (!ttsReady) return
        t.setSpeechRate(store.rate())
        t.speak(currentWord(), TextToSpeech.QUEUE_FLUSH, null, "word")
        val letters = currentWord().toCharArray().joinToString(", ")
        t.speak(letters, TextToSpeech.QUEUE_ADD, null, "spell")
    }

    private fun onMicTapped() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Reconnaissance vocale indisponible sur cet appareil.")
            return
        }
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { micBtn.text = "🎤  Parle…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { micBtn.text = "🎤  …" }
            override fun onError(error: Int) {
                micBtn.text = "🎤  Dicter les lettres"
                toast(speechError(error))
            }
            override fun onResults(results: Bundle?) {
                micBtn.text = "🎤  Dicter les lettres"
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (list.isNullOrEmpty()) {
                    toast("Je n'ai pas entendu. Réessaie.")
                    return
                }
                val hyp = list[0]
                val parsed = LetterParser.parse(hyp)
                heardView.text = "J'ai entendu : « $hyp »   →   $parsed"
                heardView.visibility = View.VISIBLE
                guess.clear()
                guess.append(parsed)
                refreshGuess()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        micBtn.text = "🎤  …"
        sr.startListening(intent)
    }

    private fun onCheck() {
        if (guess.isEmpty()) {
            toast("Écris d'abord ton orthographe.")
            return
        }
        val res = SpellingChecker.check(currentWord(), guess.toString())
        targetView.text = renderRow(res, targetRow = true)
        yourView.text = renderRow(res, targetRow = false)
        if (res.correct) {
            if (!scoredThisWord) {
                if (aidedThisWord) aidedCount++ else score++
                scoredThisWord = true
            }
            summaryView.text =
                if (aidedThisWord) "Bravo ! 🎉  (avec aide)" else "Bravo ! 🎉  Orthographe parfaite."
            progressView.text = progressText()
        } else {
            summaryView.text =
                "${res.correctCount} / ${res.total} lettres bien placées. Corrige les lettres en rouge, puis réessaie."
        }
        resultBox.visibility = View.VISIBLE
    }

    private fun nextWord() {
        if (pos + 1 >= order.size) {
            showEndDialog()
            return
        }
        pos++
        guess.clear()
        heardView.visibility = View.GONE
        scoredThisWord = false
        render()
    }

    private fun showEndDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dictée terminée !")
            .setMessage("Sans aide : $score / ${order.size}\nAvec aide : $aidedCount")
            .setPositiveButton("Recommencer") { _, _ -> initRound() }
            .setNegativeButton("Fermer", null)
            .setCancelable(false)
            .show()
    }

    private fun renderRow(res: SpellingChecker.Result, targetRow: Boolean): CharSequence {
        val sb = SpannableStringBuilder()
        for (op in res.ops) {
            val ch: String
            val color: Int
            var strike = false
            var underline = false
            when (op.kind) {
                SpellingChecker.Kind.MATCH -> {
                    ch = (if (targetRow) op.target else op.guess).toString()
                    color = green
                }
                SpellingChecker.Kind.SUB -> {
                    ch = (if (targetRow) op.target else op.guess).toString()
                    color = red
                }
                SpellingChecker.Kind.MISSING -> {
                    if (targetRow) {
                        ch = op.target.toString(); color = red; underline = true
                    } else {
                        ch = "_"; color = grey
                    }
                }
                SpellingChecker.Kind.EXTRA -> {
                    if (targetRow) {
                        ch = "·"; color = grey
                    } else {
                        ch = op.guess.toString(); color = red; strike = true
                    }
                }
            }
            val start = sb.length
            sb.append(ch)
            sb.append("  ")
            sb.setSpan(ForegroundColorSpan(color), start, start + ch.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (strike) sb.setSpan(StrikethroughSpan(), start, start + ch.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (underline) sb.setSpan(UnderlineSpan(), start, start + ch.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun speechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Problème audio."
        SpeechRecognizer.ERROR_CLIENT -> "Erreur interne de reconnaissance."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission micro manquante."
        SpeechRecognizer.ERROR_NETWORK -> "Pas de réseau pour la reconnaissance vocale."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Réseau trop lent."
        SpeechRecognizer.ERROR_NO_MATCH -> "Je n'ai pas compris. Réessaie, lettre par lettre."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconnaissance occupée, réessaie."
        SpeechRecognizer.ERROR_SERVER -> "Erreur du serveur de reconnaissance."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Je n'ai rien entendu."
        else -> "Erreur de reconnaissance ($code)."
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
