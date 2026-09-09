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

/** Parent screen: enter, paste, and delete the practice words. */
class WordListActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private lateinit var input: EditText
    private lateinit var listContainer: LinearLayout
    private lateinit var countView: TextView
    private val items = mutableListOf<String>()

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
        findViewById<Button>(R.id.addBtn).setOnClickListener { onAdd() }
        findViewById<Button>(R.id.scanBtn).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        setupSync()

        items.clear()
        items.addAll(store.words())
        redraw()
    }

    override fun onResume() {
        super.onResume()
        val latest = store.words()
        if (latest != items) {
            items.clear()
            items.addAll(latest)
            redraw()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onAdd() {
        val parts = input.text.toString()
            .split(Regex("[\\n,;]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            toast("Écris au moins un mot.")
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
        val pad = dp(6)
        items.forEachIndexed { index, word ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, pad, 0, pad)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val label = TextView(this).apply {
                text = word
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val del = Button(this).apply {
                text = "Supprimer"
                isAllCaps = false
                setOnClickListener { removeAt(index) }
            }
            row.addView(label)
            row.addView(del)
            listContainer.addView(row)
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

        Sync.pull(code) { result ->
            when (result) {
                is Sync.Result.Error -> finishSync("Échec : ${result.message}")
                is Sync.Result.Empty -> {
                    val now = System.currentTimeMillis()
                    Sync.push(code, items.toList(), now) { r ->
                        when (r) {
                            is Sync.Result.Ok -> {
                                store.markSyncedNow()
                                finishSync("Envoyé ${items.size} mot(s). Saisis « $code » sur l'autre téléphone.")
                            }
                            is Sync.Result.Error -> finishSync("Échec de l'envoi : ${r.message}")
                            else -> finishSync("Échec de l'envoi.")
                        }
                    }
                }
                is Sync.Result.Ok -> {
                    val remote = result.remote
                    val merged = Sync.merge(items.toList(), remote.words)
                    val received = merged.size - items.size
                    val now = System.currentTimeMillis()
                    items.clear()
                    items.addAll(merged)
                    store.saveFromSync(merged, now)
                    redraw()
                    Sync.push(code, merged, now) { r ->
                        when (r) {
                            is Sync.Result.Ok -> {
                                store.markSyncedNow()
                                finishSync(
                                    if (received > 0) "À jour : ${merged.size} mot(s) (+$received reçu(s))."
                                    else "À jour : ${merged.size} mot(s)."
                                )
                            }
                            is Sync.Result.Error -> finishSync("Fusionné, mais l'envoi a échoué : ${r.message}")
                            else -> finishSync("Fusionné localement.")
                        }
                    }
                }
            }
        }
    }

    private fun finishSync(message: String) {
        syncing = false
        syncBtn.isEnabled = true
        setSyncStatus(message)
    }

    private fun setSyncStatus(message: String) {
        syncStatus.text = message
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
