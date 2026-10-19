package com.example.aikeyboard.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AndroidSpeechRecognitionProvider(
    context: Context,
    private val onEvent: (VoiceEvent) -> Unit,
) : SpeechRecognitionProvider {
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onEvent(VoiceEvent.State(VoiceSessionState.LISTENING))
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) onEvent(VoiceEvent.PartialText(text))
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            onEvent(VoiceEvent.Transcript(text))
        }
        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service is unavailable"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                else -> "Speech recognition failed"
            }
            onEvent(VoiceEvent.State(VoiceSessionState.ERROR, message))
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
    private val recognizer: SpeechRecognizer? = if (SpeechRecognizer.isRecognitionAvailable(context)) {
        SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
    } else null

    override fun start(language: SpeechLanguage) {
        val engine = recognizer ?: run {
            onEvent(VoiceEvent.State(VoiceSessionState.ERROR, "Speech recognition is unavailable"))
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { engine.startListening(intent) }
            .onFailure { onEvent(VoiceEvent.State(VoiceSessionState.ERROR, "Microphone permission is required")) }
    }

    override fun pause() = runCatching { recognizer?.stopListening() }
    override fun stop() = runCatching { recognizer?.stopListening() }
    override fun cancel() = runCatching { recognizer?.cancel() }
    override fun close() { runCatching { recognizer?.destroy() } }
}
