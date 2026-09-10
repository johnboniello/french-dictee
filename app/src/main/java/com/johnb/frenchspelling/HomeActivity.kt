package com.johnb.frenchspelling

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Landing screen: pick a game or manage the word list. */
class HomeActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private lateinit var countView: TextView
    private lateinit var reviewBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        supportActionBar?.title = "Dictée FR"

        store = WordStore(this)
        countView = findViewById(R.id.countView)
        reviewBtn = findViewById(R.id.reviewBtn)

        findViewById<Button>(R.id.scrambleBtn).setOnClickListener { open(ScrambleActivity::class.java) }
        findViewById<Button>(R.id.choiceBtn).setOnClickListener { open(ChoiceActivity::class.java) }
        findViewById<Button>(R.id.dictationBtn).setOnClickListener { open(MainActivity::class.java) }
        findViewById<Button>(R.id.wordsBtn).setOnClickListener { open(WordListActivity::class.java) }
        findViewById<Button>(R.id.scanBtn).setOnClickListener { open(ScanActivity::class.java) }
        reviewBtn.setOnClickListener { chooseReviewGame() }
    }

    override fun onResume() {
        super.onResume()
        val n = store.words().size
        countView.text = when (n) {
            0 -> "Aucun mot dans la liste"
            1 -> "1 mot dans la liste"
            else -> "$n mots dans la liste"
        }
        val due = Stats.dueCount(store)
        reviewBtn.visibility = if (due == 0) android.view.View.GONE else android.view.View.VISIBLE
        reviewBtn.text = "🔁  Mots à revoir ($due)"
    }

    private fun chooseReviewGame() {
        val labels = arrayOf("🔤  Lettres mélangées", "🎯  Le bon mot", "✏️  Écris le mot")
        val classes = arrayOf(
            ScrambleActivity::class.java, ChoiceActivity::class.java, MainActivity::class.java
        )
        AlertDialog.Builder(this)
            .setTitle("Réviser — comment ?")
            .setItems(labels) { _, which ->
                startActivity(Intent(this, classes[which]).putExtra(EXTRA_REVIEW, true))
            }
            .show()
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    companion object {
        const val EXTRA_REVIEW = "review_mode"
    }
}
