package com.jxino.aladinaccessiblebookapp.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechRecognizerManager(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (SpeechRecognitionFailure) -> Unit,
    private val onReady: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isReady = false
    private var lastPartialResult: String = ""
    private val readyTimeoutRunnable: Runnable = Runnable {
        if (isListening && !isReady) {
            isListening = false
            recognizer.cancel()
            onError(
                SpeechRecognitionFailure(
                    code = -2,
                    userMessage = "Android 음성 인식 서비스가 응답하지 않습니다. Google 앱의 음성 인식 설정을 확인하거나 기기를 다시 시작한 뒤 시도해 주세요.",
                ),
            )
        }
    }

    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isReady = true
                mainHandler.removeCallbacks(readyTimeoutRunnable)
                onReady()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                lastPartialResult = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                isListening = false
                isReady = false
                mainHandler.removeCallbacks(readyTimeoutRunnable)
                val partial = lastPartialResult.trim()
                lastPartialResult = ""
                if (error == SpeechRecognizer.ERROR_NO_MATCH && partial.isNotBlank()) {
                    onResult(partial)
                } else {
                    onError(speechRecognitionFailureFor(error))
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                isReady = false
                mainHandler.removeCallbacks(readyTimeoutRunnable)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val text = matches.firstOrNull().orEmpty().ifBlank { lastPartialResult }
                lastPartialResult = ""
                if (text.isBlank()) {
                    onError(speechRecognitionFailureFor(SpeechRecognizer.ERROR_NO_MATCH))
                } else {
                    onResult(text)
                }
            }
        })
    }

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.KOREAN.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError(
                SpeechRecognitionFailure(
                    code = -1,
                    userMessage = "이 기기에서 사용할 수 있는 Android 음성 인식 서비스가 없습니다. Google 앱 또는 Google 음성 인식 서비스를 설치하거나 실제 Android 기기에서 다시 시도해 주세요.",
                ),
            )
            return
        }
        if (isListening) {
            recognizer.cancel()
        }
        isListening = true
        isReady = false
        lastPartialResult = ""
        recognizer.startListening(intent)
        mainHandler.postDelayed(readyTimeoutRunnable, 5_000L)
    }

    fun stopListening() {
        if (isListening) {
            mainHandler.removeCallbacks(readyTimeoutRunnable)
            recognizer.stopListening()
        }
    }

    fun destroy() {
        isListening = false
        isReady = false
        lastPartialResult = ""
        mainHandler.removeCallbacks(readyTimeoutRunnable)
        recognizer.destroy()
    }
}
