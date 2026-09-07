package com.johnb.frenchspelling

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Parent screen: enter, paste, and delete the practice words. */
class WordListActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private lateinit var input: EditText
    private lateinit var listContainer: LinearLayout
    private lateinit var countView: TextView
    private val items = mutableListOf<String>()

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

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
