package com.example.voicetotext

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val languages = listOf(
        "O'zbekcha" to "uz-UZ",
        "Русский" to "ru-RU",
        "English" to "en-US"
    )

    private lateinit var spinner: Spinner
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView
    private lateinit var micButton: Button
    private lateinit var copyButton: Button
    private lateinit var clearButton: Button

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var committedText = ""

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.languageSpinner)
        resultText = findViewById(R.id.resultText)
        statusText = findViewById(R.id.statusText)
        micButton = findViewById(R.id.micButton)
        copyButton = findViewById(R.id.copyButton)
        clearButton = findViewById(R.id.clearButton)

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages.map { it.first }
        )

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = getString(R.string.not_available)
            micButton.isEnabled = false
        }

        micButton.setOnClickListener {
            if (listening) {
                recognizer?.stopListening()
            } else {
                ensurePermissionAndListen()
            }
        }

        copyButton.setOnClickListener {
            val text = resultText.text.toString()
            if (text.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("voice_text", text))
                Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }
        }

        clearButton.setOnClickListener {
            committedText = ""
            resultText.text = ""
        }
    }

    private fun ensurePermissionAndListen() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
        }

        val langTag = languages[spinner.selectedItemPosition].second
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        listening = true
        micButton.text = getString(R.string.stop)
        statusText.text = getString(R.string.preparing)
        recognizer?.startListening(intent)
    }

    private fun finishListening() {
        listening = false
        micButton.text = getString(R.string.speak)
    }

    private fun showText(partial: String) {
        resultText.text = listOf(committedText, partial)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            statusText.text = getString(R.string.speak_now)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            statusText.text = getString(R.string.processing)
        }

        override fun onError(error: Int) {
            finishListening()
            statusText.text = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.err_no_speech)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> getString(R.string.err_network)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.permission_denied)
                else -> getString(R.string.err_generic, error)
            }
        }

        override fun onResults(results: Bundle?) {
            finishListening()
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                committedText = listOf(committedText, text)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
            showText("")
            statusText.text = getString(R.string.done)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            showText(partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }
}
