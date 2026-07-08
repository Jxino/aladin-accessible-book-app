package com.jxino.aladinaccessiblebookapp.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechRecognizerManager(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onError: () -> Unit,
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
        setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                onError()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val text = matches.firstOrNull().orEmpty()
                if (text.isBlank()) onError() else onResult(text)
            }
        })
    }

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    fun startListening() {
        recognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer.stopListening()
    }

    fun destroy() {
        recognizer.destroy()
    }
}
