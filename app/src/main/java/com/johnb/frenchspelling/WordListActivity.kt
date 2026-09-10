package com.johnb.frenchspelling

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Parent screen: enter/replace the weekly words, manage "Mots à revoir", sync. */
class WordListActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private lateinit var input: EditText
    private lateinit var listContainer: LinearLayout
    private lateinit var countView: TextView
    private val items = mutableListOf<String>()

    private lateinit var reviewCard: View
    private lateinit var reviewHeading: TextView
    private lateinit var reviewContainer: LinearLayout

    private lateinit var syncCard: LinearLayout
    private lateinit var codeInput: EditText
    private lateinit var syncBtn: Button
    private lateinit var syncStatus: TextView
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_list)
        supportActionBar?.title = "Gérer les mots"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        store = WordStore(this)
        input = findViewById(R.id.input)
        listContainer = findViewById(R.id.listContainer)
        countView = findViewById(R.id.countView)
        reviewCard = findViewById(R.id.reviewCard)
        reviewHeading = findViewById(R.id.reviewHeading)
        reviewContainer = findViewById(R.id.reviewContainer)

        findViewById<Button>(R.id.addBtn).setOnClickListener { onAdd(replace = false) }
        findViewById<Button>(R.id.replaceBtn).setOnClickListener { onAdd(replace = true) }
        findViewById<Button>(R.id.scanBtn).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<Button>(R.id.reviewCleanBtn).setOnClickListener {
            val n = Stats.clearMastered(store)
            renderReview()
            toast(if (n > 0) "$n mot(s) retiré(s)." else "Rien à retirer.")
        }

        setupSync()

        items.clear()
        items.addAll(store.words())
        redraw()
        renderReview()
    }

    override fun onResume() {
        super.onResume()
        val latest = store.words()
        if (latest != items) {
            items.clear()
            items.addAll(latest)
            redraw()
        }
        renderReview()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onAdd(replace: Boolean) {
        val parts = input.text.toString()
            .split(Regex("[\\n,;]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            toast("Écris au moins un mot.")
            return
        }
        if (replace) {
            AlertDialog.Builder(this)
                .setTitle("Nouvelle semaine")
                .setMessage("Remplacer la liste par ces ${parts.size} mots ?\n\nLes « Mots à revoir » sont gardés.")
                .setPositiveButton("Remplacer") { _, _ ->
                    Stats.prune(store)
                    val uniq = ArrayList<String>()
                    val seen = HashSet<String>()
                    for (p in parts) if (seen.add(p.lowercase())) uniq.add(p)
                    items.clear()
                    items.addAll(uniq)
                    store.replaceWords(uniq)
                    input.setText("")
                    redraw()
                    renderReview()
                    AlertDialog.Builder(this)
                        .setMessage("Nouvelle liste : ${uniq.size} mots.\nMots à revoir : ${Stats.dueCount(store)}.")
                        .setPositiveButton("OK", null)
                        .show()
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }
        var added = 0
        for (p in parts) {
            if (items.none { it.equals(p, ignoreCase = true) }) {
                items.add(p)
                added++
            }
        }
        store.save(items)
        input.setText("")
        redraw()
        toast(if (added == 0) "Ces mots sont déjà dans la liste." else "$added mot(s) ajouté(s).")
    }

    private fun removeAt(index: Int) {
        items.removeAt(index)
        store.save(items)
        redraw()
    }

    private fun redraw() {
        countView.text = when (items.size) {
            0 -> "Aucun mot"
            1 -> "1 mot"
            else -> "${items.size} mots"
        }
        listContainer.removeAllViews()
        items.forEachIndexed { index, word ->
            listContainer.addView(wordRow(word, deletable = true, index = index))
        }
    }

    private fun renderReview() {
        val list = Stats.listForManage(store)
        reviewCard.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        reviewHeading.text = "🔁  Mots à revoir (${list.size})"
        reviewContainer.removeAllViews()
        for (it in list) {
            reviewContainer.addView(reviewRow(it))
        }
    }

    private fun rowFrame(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun wordRow(word: String, deletable: Boolean, index: Int): View {
        val row = rowFrame()
        row.addView(TextView(this).apply {
            text = word
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(pinButton(word))
        if (deletable) {
            row.addView(Button(this).apply {
                text = "Supprimer"
                isAllCaps = false
                setOnClickListener { removeAt(index) }
            })
        }
        return row
    }

    private fun reviewRow(item: Stats.ManageItem): View {
        val row = rowFrame()
        row.addView(TextView(this).apply {
            text = item.text
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = Stats.boxDots(item.box)
            textSize = 13f
            setPadding(dp(6), 0, dp(6), 0)
            setTextColor(0xFF0C447C.toInt())
        })
        row.addView(Button(this).apply {
            text = "✓"
            isAllCaps = false
            minWidth = 0
            setOnClickListener {
                Stats.master(store, item.text)
                renderReview()
            }
        })
        row.addView(pinButton(item.text))
        return row
    }

    private fun pinButton(word: String): Button = Button(this).apply {
        text = "📌"
        isAllCaps = false
        minWidth = 0
        alpha = if (Stats.isPinned(store, word)) 1f else 0.35f
        setOnClickListener {
            Stats.setPinned(store, word, !Stats.isPinned(store, word))
            redraw()
            renderReview()
        }
    }

    // ---- Family-code sync -------------------------------------------------

    private fun setupSync() {
        syncCard = findViewById(R.id.syncCard)
        codeInput = findViewById(R.id.codeInput)
        syncBtn = findViewById(R.id.syncBtn)
        syncStatus = findViewById(R.id.syncStatus)

        if (!Sync.isConfigured) {
            syncCard.visibility = View.GONE
            return
        }
        syncCard.visibility = View.VISIBLE
        codeInput.setText(store.familyCode())

        findViewById<Button>(R.id.genCodeBtn).setOnClickListener {
            val existing = Sync.normalizeCode(codeInput.text.toString())
            if (Sync.isValidCode(existing)) {
                AlertDialog.Builder(this)
                    .setTitle("Remplacer le code ?")
                    .setMessage("Un nouveau code ne verra pas la liste déjà partagée sous « $existing ».")
                    .setPositiveButton("Nouveau code") { _, _ -> codeInput.setText(Sync.newCode()) }
                    .setNegativeButton("Garder", null)
                    .show()
            } else {
                codeInput.setText(Sync.newCode())
            }
        }

        findViewById<Button>(R.id.shareCodeBtn).setOnClickListener {
            val code = Sync.normalizeCode(codeInput.text.toString())
            if (!Sync.isValidCode(code)) {
                toast("Crée d'abord un code.")
                return@setOnClickListener
            }
            store.setFamilyCode(code)
            val msg = "Code pour « Dictée FR » : $code\n\n" +
                "Ouvre l'appli sur l'autre téléphone → Gérer les mots → saisis ce code → Synchroniser."
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, msg)
                    },
                    "Envoyer le code"
                )
            )
        }

        syncBtn.setOnClickListener { doSync() }
    }

    private fun doSync() {
        if (syncing) return
        val code = Sync.normalizeCode(codeInput.text.toString())
        if (!Sync.isValidCode(code)) {
            setSyncStatus("Code invalide : 4 à 40 lettres, chiffres ou tirets.")
            return
        }
        codeInput.setText(code)
        store.setFamilyCode(code)

        syncing = true
        syncBtn.isEnabled = false
        setSyncStatus("Synchronisation…")

        Sync.syncAll(store, code) { result ->
            syncing = false
            syncBtn.isEnabled = true
            when (result) {
                is Sync.Result.Ok -> {
                    items.clear()
                    items.addAll(store.words())
                    redraw()
                    renderReview()
                    setSyncStatus(result.message)
                }
                is Sync.Result.Error -> setSyncStatus("Échec : ${result.message}")
            }
        }
    }

    private fun setSyncStatus(message: String) {
        syncStatus.text = message
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
