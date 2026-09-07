package com.johnb.frenchspelling

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Landing screen: pick a game or manage the word list. */
class HomeActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private lateinit var countView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        supportActionBar?.title = "Dictée FR"

        store = WordStore(this)
        countView = findViewById(R.id.countView)

        findViewById<Button>(R.id.scrambleBtn).setOnClickListener { open(ScrambleActivity::class.java) }
        findViewById<Button>(R.id.dictationBtn).setOnClickListener { open(MainActivity::class.java) }
        findViewById<Button>(R.id.wordsBtn).setOnClickListener { open(WordListActivity::class.java) }
        findViewById<Button>(R.id.scanBtn).setOnClickListener { open(ScanActivity::class.java) }
    }

    override fun onResume() {
        super.onResume()
        val n = store.words().size
        countView.text = when (n) {
            0 -> "Aucun mot dans la liste"
            1 -> "1 mot dans la liste"
            else -> "$n mots dans la liste"
        }
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))
}
