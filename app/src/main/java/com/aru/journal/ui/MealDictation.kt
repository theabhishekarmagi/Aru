package com.aru.journal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Platform boundary lets UI tests exercise transcripts without pretending to test microphone accuracy. */
interface MealDictation {
    fun start(onText: (String) -> Unit, onListening: (Boolean) -> Unit, onError: (String) -> Unit)
    fun stop()
    fun close()
}

class AndroidMealDictation(private val context: Context) : MealDictation {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var closed = false
    override fun start(onText: (String) -> Unit, onListening: (Boolean) -> Unit, onError: (String) -> Unit) {
        main.post {
            if (closed) return@post
            val current = ++generation
            recognizer?.destroy()
            recognizer = null
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Dictation is unavailable on this device. You can still type your meal.")
                onListening(false)
                return@post
            }
            try {
                val speech = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = speech
                speech.setRecognitionListener(object : RecognitionListener {
                    private fun active() = !closed && generation == current
                    private fun words(bundle: Bundle?) {
                        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (active() && text.isNotBlank()) onText(text)
                    }
                    override fun onReadyForSpeech(params: Bundle?) { if (active()) onListening(true) }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit // Keep controls busy until the final result arrives.
                    override fun onError(error: Int) {
                        if (active()) {
                            onListening(false)
                            onError(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "Allow microphone access to dictate a meal." else "Couldn’t hear a meal. Please try again or type it below.")
                        }
                    }
                    override fun onResults(results: Bundle?) { words(results); if (active()) onListening(false) }
                    override fun onPartialResults(partialResults: Bundle?) = words(partialResults)
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                onListening(true)
                speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                })
            } catch (_: Exception) {
                onListening(false)
                onError("Dictation couldn’t start. Please try again or type your meal.")
            }
        }
    }
    override fun stop() { main.post { generation++; recognizer?.cancel() } }
    override fun close() { main.post { closed = true; generation++; recognizer?.destroy(); recognizer = null } }
}
