package com.johnb.frenchspelling

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** Take a photo of a word list, run on-device OCR, review, and add to the list. */
class ScanActivity : AppCompatActivity() {

    private lateinit var store: WordStore
    private lateinit var reviewField: EditText
    private lateinit var progress: ProgressBar
    private lateinit var hintText: TextView
    private var photoUri: Uri? = null

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = photoUri
            if (ok && uri != null) runOcr(uri) else hintText.text = "Photo annulée."
        }

    private val camPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else toast("Accès à la caméra refusé.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        supportActionBar?.title = "Scanner une liste"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        store = WordStore(this)
        reviewField = findViewById(R.id.reviewField)
        progress = findViewById(R.id.progress)
        hintText = findViewById(R.id.hintText)

        findViewById<Button>(R.id.photoBtn).setOnClickListener { onPhoto() }
        findViewById<Button>(R.id.addBtn).setOnClickListener { addWords() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            camPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        photoUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: Exception) {
            toast("Aucune application appareil photo trouvée.")
        }
    }

    private fun runOcr(uri: Uri) {
        progress.visibility = View.VISIBLE
        hintText.text = "Lecture en cours…"
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    progress.visibility = View.GONE
                    val found = extractWords(result.text)
                    if (found.isEmpty()) {
                        hintText.text =
                            "Aucun mot reconnu. Réessaie avec plus de lumière, le texte bien à plat."
                    } else {
                        hintText.text = "Vérifie et corrige, puis « Ajouter à la liste »."
                        val existing = reviewField.text.toString().trim()
                        val merged = (if (existing.isEmpty()) emptyList() else existing.split("\n")) + found
                        reviewField.setText(merged.map { it.trim() }.filter { it.isNotEmpty() }
                            .distinctBy { it.lowercase() }.joinToString("\n"))
                    }
                }
                .addOnFailureListener { e ->
                    progress.visibility = View.GONE
                    hintText.text = "Lecture impossible : ${e.message}"
                }
        } catch (e: Exception) {
            progress.visibility = View.GONE
            hintText.text = "Image illisible : ${e.message}"
        }
    }

    /** Best-effort: one candidate word per line, list markers and stray punctuation removed. */
    private fun extractWords(raw: String): List<String> {
        val out = ArrayList<String>()
        for (lineRaw in raw.split("\n")) {
            var line = lineRaw.trim()
            if (line.isEmpty()) continue
            line = line.replace(Regex("^\\s*(\\d+\\s*[.)\\-–]|[-*•·–])\\s*"), "")
            for (partRaw in line.split(Regex("[,;/|]|\\s{2,}|\\s-\\s"))) {
                val part = partRaw.trim()
                    .trim('.', ',', ';', ':', '"', '\'', '(', ')', '!', '?', '·', '–', '-', '_')
                if (part.length in 1..30 && part.any { it.isLetter() } && part.none { it.isDigit() }) {
                    out.add(part)
                }
            }
        }
        return out.distinctBy { it.lowercase() }
    }

    private fun addWords() {
        val parts = reviewField.text.toString().split("\n")
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            toast("Rien à ajouter.")
            return
        }
        val current = store.words()
        var added = 0
        for (p in parts) {
            if (current.none { it.equals(p, ignoreCase = true) }) {
                current.add(p)
                added++
            }
        }
        store.save(current)
        toast(if (added == 0) "Ces mots sont déjà dans la liste." else "$added mot(s) ajouté(s).")
        finish()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
